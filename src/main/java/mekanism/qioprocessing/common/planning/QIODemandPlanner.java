package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import mekanism.qioprocessing.common.planning.QIOPlanningResult.Status;

import java.math.BigInteger;
import java.util.*;

/**
 * Demand-driven inventory simulation. Recipe trials share a reversible ledger; a failed trial
 * cannot publish inventory, requirements or execution steps. Explicit frames survive Forge
 * validation yields. Recursion is a material boundary, never a coefficient-growth solver.
 */
final class QIODemandPlanner {
    static final QIODemandPlanner INSTANCE = new QIODemandPlanner();

    // Finding the first complete plan retains the snapshot's full search budget. Improving
    // an already valid plan must not spend another 65k nodes or hundreds of server handoffs.
    static final int MAX_OPTIMIZATION_NODES = 2_048;
    static final int MAX_OPTIMIZATION_ATTEMPTS = 1_024;
    static final int MAX_OPTIMIZATION_YIELDS = 4;
    static final long MAX_OPTIMIZATION_NANOS = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(250);

    QIOPlanningResult plan(QIOPlanningRequest request, QIOPlanningExecutor.CancellationToken cancelled) {
        return plan(request, cancelled, null);
    }

    QIOPlanningResult plan(QIOPlanningRequest request, QIOPlanningExecutor.CancellationToken cancelled,
                           QIOPlanningResult incumbent) {
        Session session = QIOPlanningExecutor.taskLocal(QIODemandPlanner.class, Session::new);
        if (session.state == null || !session.state.matches(request)) session.state = new State(request, cancelled);
        State state = session.state;
        try {
            state.seed(incumbent);
            return state.run();
        } catch (QIOPlanningContinuation pending) {
            if (state.best != null) {
                if (state.optimizationStopped() || state.optimizationYields >= MAX_OPTIMIZATION_YIELDS) {
                    return state.searchStopped("complete-plan refinement validation budget reached");
                }
                state.optimizationYields++;
                QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.OPTIMIZATION_VALIDATION_YIELDS, 1);
            }
            throw pending;
        } catch (QIOPlanningVersionException changed) {
            return state.failure(Status.VERSION_CHANGED, changed.getMessage(), state.currentResource());
        } catch (Failure failure) {
            return state.failure(failure.status, failure.getMessage(), failure.resource);
        } catch (QIOPlanningLimitException limit) {
            if (limit.isSearchLimit()) return state.searchStopped(limit.getMessage());
            return state.failure(Status.TOO_COMPLEX, limit.getMessage(), state.currentResource());
        } catch (ArithmeticException overflow) {
            return state.failure(Status.AMOUNT_OVERFLOW, "QIO material quantity overflow", state.currentResource());
        } catch (RuntimeException failure) {
            return state.failure(Status.INTERNAL_ERROR, failure.toString(), state.currentResource());
        }
    }

    private static final class Session {
        State state;
    }

    private static final class Lot {
        final long source, amount;
        final Lot next;

        Lot(long source, long amount, Lot next) {
            this.source = source;
            this.amount = amount;
            this.next = next;
        }
    }

    /**
     * Immutable two-stack FIFO. Checkpoints share old tails; appending never copies accumulated output.
     */
    private static final class Lots {
        final Lot front, back;

        Lots(Lot front, Lot back) {
            this.front = front;
            this.back = back;
        }
    }

    private static final class Frame {
        final PortableResourceDescriptor resource;
        final long required;
        final int depth;
        final boolean root;
        final Set<String> excluded = new LinkedHashSet<>();
        final Set<Long> dependencies = new LinkedHashSet<>();
        boolean entered, cycleBoundary;
        int cursor;
        int failedCycleAncestor = -1;
        long node, operations, before, assumedBefore;
        long batchLimit = Long.MAX_VALUE;
        QIOPlanningRoute route;
        Map<PortableResourceDescriptor, Long> beforeInputs;
        List<Map.Entry<PortableResourceDescriptor, Long>> inputs;

        Frame(PortableResourceDescriptor resource, long required, int depth, boolean root) {
            this.resource = resource;
            this.required = required;
            this.depth = depth;
            this.root = root;
        }

        Frame copy() {
            Frame f = new Frame(resource, required, depth, root);
            f.excluded.addAll(excluded);
            f.dependencies.addAll(dependencies);
            f.entered = entered;
            f.cycleBoundary = cycleBoundary;
            f.cursor = cursor;
            f.failedCycleAncestor = failedCycleAncestor;
            f.node = node;
            f.operations = operations;
            f.before = before;
            f.assumedBefore = assumedBefore;
            f.batchLimit = batchLimit;
            f.route = route;
            f.inputs = inputs;
            f.beforeInputs = beforeInputs;
            return f;
        }
    }

    private static final class State {
        final QIOPlanningRequest request;
        final QIOPlanningSnapshot snapshot;
        final QIOPlanningExecutor.CancellationToken cancelled;
        final QIOPlanningJournal journal = new QIOPlanningJournal();
        final Map<PortableResourceDescriptor, Lots> inventory = journal.map();
        final Map<PortableResourceDescriptor, Long> balances = journal.map();
        final Map<PortableResourceDescriptor, Long> external = journal.map();
        final Map<PortableResourceDescriptor, Long> assumed = journal.map();
        final List<Frame> frames = new ArrayList<>();
        final List<QIOPlanStep> steps = new ArrayList<>();
        final Deque<Decision> decisions = new ArrayDeque<>();
        long nextNode, operations;
        int visited, attempts, retained;
        int optimizationVisited, optimizationAttempts;
        int optimizationYields;
        final long startedNanos = System.nanoTime();
        long optimizationStartedNanos;
        QIOCraftPlan best;
        BigInteger bestMissing;
        BigInteger missingTotal = BigInteger.ZERO;
        long bestOperations;
        boolean initialized;
        QIOPlanningTrace failedTrace;
        QIOPlanningResult completed;

        State(QIOPlanningRequest request, QIOPlanningExecutor.CancellationToken cancelled) {
            this.request = request;
            this.snapshot = request.getSnapshot();
            this.cancelled = cancelled;
            snapshot.getAvailableResources().forEach((r, n) -> {
                if (n > 0) {
                    inventory.put(r, new Lots(new Lot(-1, n, null), null));
                    balances.put(r, n);
                }
            });
        }

        boolean matches(QIOPlanningRequest other) {
            return request.getPlanId().equals(other.getPlanId()) && request.getPlanRevision() == other.getPlanRevision()
                    && request.getRootAmount() == other.getRootAmount() && request.getRootResource().equals(other.getRootResource())
                    && snapshot.getSourceRevisions() == other.getSnapshot().getSourceRevisions();
        }

        void seed(QIOPlanningResult incumbent) {
            if (initialized || incumbent == null || incumbent.getStatus() != Status.SUCCESS) return;
            QIOCraftPlan plan = incumbent.getPlan();
            if (!request.getPlanId().equals(plan.getPlanId()) || request.getPlanRevision() != plan.getRevision() ||
                    plan.getSourceRevisions() != snapshot.getSourceRevisions() ||
                    !request.getRootResource().equals(plan.getRootResource()) || request.getRootAmount() != plan.getRootAmount())
                return;
            BigInteger deficit = BigInteger.ZERO;
            for (Map.Entry<PortableResourceDescriptor, Long> input : plan.getExternalRequirements().entrySet()) {
                deficit = deficit.add(BigInteger.valueOf(Math.max(0,
                        input.getValue() - snapshot.getAvailableResources().getOrDefault(input.getKey(), 0L))));
            }
            best = plan;
            bestMissing = deficit;
            bestOperations = incumbent.getPlannedOperations();
            beginOptimization();
            QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.TOPOLOGY_INCUMBENTS, 1);
        }

        void beginOptimization() {
            optimizationVisited = visited;
            optimizationAttempts = attempts;
            optimizationStartedNanos = System.nanoTime();
            QIOPlanningMetrics metrics = QIOPlanningMetrics.current();
            metrics.add(QIOPlanningMetrics.Metric.FIRST_COMPLETE_PLANS, 1);
            metrics.maximum(QIOPlanningMetrics.Metric.FIRST_COMPLETE_NODES, visited);
            metrics.add(QIOPlanningMetrics.Metric.FIRST_COMPLETE_NS, Math.max(0, optimizationStartedNanos - startedNanos));
        }

        boolean optimizationStopped() {
            if (best == null) return false;
            return visited - optimizationVisited >= Math.min(MAX_OPTIMIZATION_NODES, snapshot.getMaximumNodes()) ||
                    attempts - optimizationAttempts >= Math.min(MAX_OPTIMIZATION_ATTEMPTS, snapshot.getMaximumNodes()) ||
                    System.nanoTime() - optimizationStartedNanos >= MAX_OPTIMIZATION_NANOS;
        }

        boolean selectionCancelled() {
            if (cancelled.isCancelled() || Thread.currentThread().isInterrupted()) return true;
            // Candidate domains can spend most of a solve inside one advance(). Use their
            // existing checkpoints too, and keep budget exhaustion distinct from cancellation.
            if (optimizationStopped())
                throw QIOPlanningLimitException.search("complete-plan candidate refinement budget reached");
            return false;
        }

        void recordOptimization() {
            QIOPlanningMetrics metrics = QIOPlanningMetrics.current();
            metrics.maximum(QIOPlanningMetrics.Metric.OPTIMIZATION_NODES, visited - optimizationVisited);
            metrics.maximum(QIOPlanningMetrics.Metric.OPTIMIZATION_BRANCHES, attempts - optimizationAttempts);
            metrics.maximum(QIOPlanningMetrics.Metric.OPTIMIZATION_ELAPSED_NS,
                    Math.max(0, System.nanoTime() - optimizationStartedNanos));
        }

        PortableResourceDescriptor currentResource() {
            return frames.isEmpty() ? request.getRootResource() : frames.get(frames.size() - 1).resource;
        }

        void check() {
            if (cancelled.isCancelled() || Thread.currentThread().isInterrupted())
                throw new Failure(Status.CANCELLED, "QIO planning cancelled", currentResource());
        }

        QIOPlanningResult searchStopped(String message) {
            if (cancelled.isCancelled() || Thread.currentThread().isInterrupted())
                return failure(Status.CANCELLED, "QIO planning cancelled", currentResource());
            for (QIOPlanningRoute route : snapshot.getRoutes()) {
                if (route.verification() != null && !route.verification().currentEpoch())
                    return failure(Status.VERSION_CHANGED, "Workbench recipes changed while planning", currentResource());
            }
            if (best == null) return failure(Status.TOO_COMPLEX,
                    "QIO search incomplete: " + message, currentResource());
            release();
            QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.OPTIMIZATION_BUDGET_RETURNS, 1);
            return completed = QIOPlanningResult.optimizedUntilBudget(best, visited, bestOperations);
        }

        long available(PortableResourceDescriptor resource) {
            return balances.getOrDefault(resource, 0L);
        }

        long required(Frame frame) {
            // Borrowed target items are seeds, not newly produced items for this order.
            return frame.root ? Math.addExact(frame.required, assumed.getOrDefault(frame.resource, 0L)) : frame.required;
        }

        QIOPlanningResult run() {
            if (completed != null) return completed;
            if (!initialized) {
                frames.add(new Frame(request.getRootResource(), Math.addExact(available(request.getRootResource()),
                        request.getRootAmount()), 0, true));
                initialized = true;
            }
            while (true) {
                check();
                // Apply latency limits only to unfinished refinement. A completed better
                // ledger is evaluated below before deciding whether more work is useful.
                if (!frames.isEmpty() && optimizationStopped())
                    return searchStopped("complete-plan refinement budget reached");
                if (best != null && missingTotal.compareTo(bestMissing) >= 0) {
                    // Consumed external material is irreversible within this trial. Its
                    // deficit is a lower bound; this branch cannot improve the saved plan.
                    QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.DEMAND_BOUND_PRUNES, 1);
                    if (backtrack(false)) continue;
                    release();
                    return completed = QIOPlanningResult.success(best, visited, bestOperations);
                }
                if (frames.isEmpty()) {
                    BigInteger missing = missing();
                    if (best == null || missing.compareTo(bestMissing) < 0) {
                        if (best == null) beginOptimization();
                        best = new QIOCraftPlan(request.getPlanId(), request.getPlanRevision(), snapshot.getSourceRevisions(),
                                request.getRootResource(), request.getRootAmount(), external, steps);
                        bestMissing = missing;
                        bestOperations = operations;
                    }
                    if (missing.signum() == 0 || !backtrack(false)) {
                        release();
                        return completed = QIOPlanningResult.success(best, visited, bestOperations);
                    }
                    continue;
                }
                Frame frame = frames.get(frames.size() - 1);
                try {
                    advance(frame);
                } catch (RetryPrefix retry) {
                    // The enclosing trial was restored with a smaller, already funded batch.
                    continue;
                } catch (BranchFailure failure) {
                    QIOPlanningTrace current = trace(failure.resource);
                    if (!current.getSteps().isEmpty()) failedTrace = current;
                    if (failure.boundary >= 0 && retryBoundary(failure.boundary)) continue;
                    if (backtrack(false)) continue;
                    if (best != null) {
                        release();
                        return completed = QIOPlanningResult.success(best, visited, bestOperations);
                    }
                    throw new Failure(failure.status, failure.getMessage(), failure.resource);
                }
            }
        }

        void advance(Frame frame) {
            if (!frame.entered) {
                if (++visited - (best == null ? 0 : optimizationVisited) > snapshot.getMaximumNodes())
                    throw QIOPlanningLimitException.search("dependency exploration budget exceeded");
                if (frame.depth > snapshot.getMaximumDepth())
                    throw new BranchFailure(Status.TOO_COMPLEX, "QIO dependency depth exceeded", frame.resource, -1);
                frame.entered = true;
            }
            if (frame.route == null) {
                if (available(frame.resource) >= required(frame)) {
                    finish(frame);
                    return;
                }
                List<QIOPlanningRoute> candidates = snapshot.getRoutesProducing(frame.resource);
                if (candidates.isEmpty()) {
                    if (frame.root)
                        throw new BranchFailure(Status.NO_ROUTE, "No enabled recipe produces " + frame.resource, frame.resource, -1);
                    supplyMissing(frame);
                    finish(frame);
                    return;
                }
                int ancestor = ancestor(frame.resource);
                if (ancestor >= 0) {
                    Frame entry = frames.get(ancestor);
                    if (entry.operations > 1) {
                        // Do not reserve the startup inventory of the entire requested batch.
                        // Retry a prefix funded by existing seed, or one round when seed is missing.
                        long prefix = BigInteger.valueOf(entry.operations).multiply(BigInteger.valueOf(available(frame.resource)))
                                .divide(BigInteger.valueOf(frame.required)).max(BigInteger.ONE).longValueExact();
                        if (prefix < entry.operations && retryPrefix(ancestor, prefix)) {
                            QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.RECURSIVE_BOOTSTRAP_RETRIES, 1);
                            throw new RetryPrefix();
                        }
                    }
                    // The requested resource may be conserved for one round while another
                    // internal resource grows (A -> 2 B -> A). Count that real intermediate
                    // growth, but do not mistake arbitrary external byproducts for a seed proof.
                    if (productivePath(ancestor)) {
                        supplyMissing(frame);
                        finish(frame);
                        return;
                    }
                    // Try a different producer at the original request, before falling back to
                    // an external material requirement. No incomplete recipe operations survive.
                    throw new BranchFailure(Status.UNRESOLVABLE_CYCLE,
                            "Recursive recipes have no net output for " + frame.resource, frame.resource, ancestor);
                }
                Map<PortableResourceDescriptor, Long> stock = selectionStock();
                QIOPlanningRoute selected = null, template = null;
                for (QIOPlanningRoute candidate : candidates) {
                    long count = ceil(required(frame) - available(frame.resource), candidate.getGuaranteedOutputAmount(frame.resource));
                    selected = candidate.selectWorkbench(stock, count, this::selectionCancelled, frame.excluded, frame.resource);
                    if (selected != null) {
                        template = candidate;
                        break;
                    }
                }
                if (selected == null) {
                    if (frame.failedCycleAncestor >= 0)
                        throw new BranchFailure(Status.UNRESOLVABLE_CYCLE, "All local cyclic candidates exhausted for " +
                                frame.resource, frame.resource, frame.failedCycleAncestor);
                    if (!frame.root && frame.cycleBoundary) {
                        supplyMissing(frame);
                        finish(frame);
                        return;
                    }
                    throw new BranchFailure(frame.cycleBoundary ? Status.UNRESOLVABLE_CYCLE : Status.NO_ROUTE,
                            "No valid non-recursive recipe remains for " + frame.resource, frame.resource, -1);
                }
                if (++attempts - (best == null ? 0 : optimizationAttempts) > snapshot.getMaximumNodes())
                    throw QIOPlanningLimitException.search("recipe candidate budget exceeded");
                QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.BRANCH_ATTEMPTS, 1);
                Decision decision = new Decision(selected.getStableId(), template.hasAlternatives() || candidates.size() > 1);
                decisions.addLast(decision);
                frame.route = selected;
                frame.before = available(frame.resource);
                frame.assumedBefore = assumed.getOrDefault(frame.resource, 0L);
                frame.beforeInputs = new LinkedHashMap<>();
                for (PortableResourceDescriptor inputResource : selected.getExactInputs().keySet())
                    frame.beforeInputs.put(inputResource, available(inputResource) - assumed.getOrDefault(inputResource, 0L));
                long output = selected.getGuaranteedOutputAmount(frame.resource);
                long input = selected.getExactInputs().getOrDefault(frame.resource, 0L);
                long divisor = output > input ? output - input : output;
                long count = ceil(required(frame) - frame.before, divisor);
                count = Math.min(count, frame.batchLimit);
                if (selected.requiresSequentialWorkbench()) count = 1;
                // Shared input/output resources (catalysts as well as the demand itself) must
                // reuse the actual returned stock. Do not charge one seed per operation.
                for (Map.Entry<PortableResourceDescriptor, Long> entry : selected.getExactInputs().entrySet()) {
                    if (selected.getGuaranteedOutputs().containsKey(entry.getKey()))
                        count = Math.min(count, Math.max(1, available(entry.getKey()) / entry.getValue()));
                }
                {
                    long stocked = count;
                    for (Map.Entry<PortableResourceDescriptor, Long> entry : selected.getExactInputs().entrySet())
                        stocked = Math.min(stocked, stock.getOrDefault(entry.getKey(), 0L) / entry.getValue());
                    if (stocked > 0) count = stocked;
                }
                frame.operations = count;
                frame.node = nextNode++;
                frame.inputs = new ArrayList<>(selected.getExactInputs().entrySet());
                frame.cursor = 0;
                frame.dependencies.clear();
                if (nextNode > snapshot.getMaximumNodes())
                    throw new Failure(Status.TOO_COMPLEX, "QIO execution step budget exceeded", frame.resource);
                if (count > snapshot.getMaximumOperations() - operations)
                    throw new BranchFailure(Status.TOO_COMPLEX, "QIO actual recipe operation budget exceeded", frame.resource, -1);
                operations += count;
            }
            if (frame.cursor < frame.inputs.size()) {
                Map.Entry<PortableResourceDescriptor, Long> input = frame.inputs.get(frame.cursor);
                long amount = Math.multiplyExact(input.getValue(), frame.operations);
                frames.add(new Frame(input.getKey(), amount, frame.depth + 1, false));
                return;
            }
            QIOPlanningRoute route = frame.route;
            for (Map.Entry<PortableResourceDescriptor, Long> output : route.getGuaranteedOutputs().entrySet())
                insert(output.getKey(), Math.multiplyExact(output.getValue(), frame.operations), frame.node);
            steps.add(step(frame));
            long gained = available(frame.resource) - frame.before;
            long borrowed = assumed.getOrDefault(frame.resource, 0L) - frame.assumedBefore;
            boolean intermediateProgress = false;
            for (Map.Entry<PortableResourceDescriptor, Long> input : frame.beforeInputs.entrySet()) {
                if (available(input.getKey()) - assumed.getOrDefault(input.getKey(), 0L) > input.getValue()) {
                    intermediateProgress = true;
                    break;
                }
            }
            if (gained <= borrowed && !intermediateProgress) {
                throw new BranchFailure(Status.UNRESOLVABLE_CYCLE,
                        "Recipe branch does not produce net demand for " + frame.resource, frame.resource, frames.size() - 1);
            }
            // No later backtrack can choose a different variant at these completed frames.
            // Keep checkpoints for active ancestors and all genuine alternative decisions.
            while (!decisions.isEmpty() && !decisions.peekLast().alternatives &&
                    decisions.peekLast().saved.size() >= frames.size()) decisions.removeLast().release();
            frame.route = null;
            frame.excluded.clear();
            frame.cycleBoundary = false;
            frame.failedCycleAncestor = -1;
            frame.batchLimit = Long.MAX_VALUE;
        }

        int ancestor(PortableResourceDescriptor resource) {
            for (int i = frames.size() - 2; i >= 0; i--) if (frames.get(i).resource.equals(resource)) return i;
            return -1;
        }

        void finish(Frame frame) {
            frames.remove(frames.size() - 1);
            if (!frames.isEmpty()) {
                Frame parent = frames.get(frames.size() - 1);
                long count = Math.multiplyExact(parent.inputs.get(parent.cursor).getValue(), parent.operations);
                consume(frame.resource, count, parent.dependencies);
                parent.cursor++;
            }
        }

        void supplyMissing(Frame frame) {
            long missing = required(frame) - available(frame.resource);
            if (missing <= 0) return;
            // Like AE2S's maximum-craftable trial, keep a funded prefix before trying a
            // different producer for the remainder. This also works when the useful stock
            // was manufactured by a deeper dependency, not present in the original snapshot.
            for (int i = frames.size() - 2; i >= 0; i--) {
                Frame parent = frames.get(i);
                if (parent.route == null || parent.operations <= 1 || parent.cursor >= parent.inputs.size()) continue;
                List<QIOPlanningRoute> producers = snapshot.getRoutesProducing(parent.resource);
                boolean alternative = producers.size() > 1;
                for (QIOPlanningRoute producer : producers) alternative |= producer.hasAlternatives();
                if (!alternative) continue;
                Map.Entry<PortableResourceDescriptor, Long> input = parent.inputs.get(parent.cursor);
                long funded = available(input.getKey()) / input.getValue();
                if (funded > 0 && funded < parent.operations && retryPrefix(i, funded)) throw new RetryPrefix();
            }
            assumed.merge(frame.resource, missing, Math::addExact);
            insert(frame.resource, missing, -1);
        }

        void insert(PortableResourceDescriptor resource, long amount, long source) {
            balances.put(resource, Math.addExact(available(resource), amount));
            Lots lots = inventory.get(resource);
            inventory.put(resource, new Lots(lots == null ? null : lots.front,
                    new Lot(source, amount, lots == null ? null : lots.back)));
        }

        void consume(PortableResourceDescriptor resource, long amount, Set<Long> dependencies) {
            long balance = available(resource);
            if (balance < amount) throw new IllegalStateException("Unfunded recipe input " + resource);
            balances.put(resource, balance - amount);
            Lots lots = inventory.get(resource);
            Lot front = lots == null ? null : lots.front, back = lots == null ? null : lots.back;
            while (amount > 0) {
                if (front == null) {
                    for (Lot old = back; old != null; old = old.next) {
                        check();
                        front = new Lot(old.source, old.amount, front);
                    }
                    back = null;
                }
                if (front == null) throw new IllegalStateException("Unfunded recipe input " + resource);
                Lot lot = front;
                long used = Math.min(amount, lot.amount);
                if (used > 0) {
                    if (lot.source < 0) {
                        long previous = external.getOrDefault(resource, 0L);
                        long next = Math.addExact(previous, used);
                        long stock = snapshot.getAvailableResources().getOrDefault(resource, 0L);
                        long delta = Math.max(0, next - stock) - Math.max(0, previous - stock);
                        missingTotal = missingTotal.add(BigInteger.valueOf(delta));
                        external.put(resource, next);
                    } else dependencies.add(lot.source);
                    amount -= used;
                }
                front = used < lot.amount ? new Lot(lot.source, lot.amount - used, lot.next) : lot.next;
            }
            if (amount != 0) throw new IllegalStateException("Unfunded recipe input " + resource);
            if (front == null && back == null) inventory.remove(resource);
            else inventory.put(resource, new Lots(front, back));
        }

        Map<PortableResourceDescriptor, Long> selectionStock() {
            Map<PortableResourceDescriptor, Long> stock = new TreeMap<>(balances);
            for (Frame parent : frames)
                if (parent.route != null) {
                    for (int i = parent.cursor + 1; i < parent.inputs.size(); i++) {
                        Map.Entry<PortableResourceDescriptor, Long> input = parent.inputs.get(i);
                        long held = Math.multiplyExact(input.getValue(), parent.operations);
                        stock.put(input.getKey(), Math.max(0, stock.getOrDefault(input.getKey(), 0L) - held));
                    }
                }
            return stock;
        }

        BigInteger missing() {
            return missingTotal;
        }

        final class Decision {
            final int mark, stepCount, weight;
            final long savedOperations, savedNode;
            final BigInteger savedMissing;
            final List<Frame> saved = new ArrayList<>();
            final String selected;
            final boolean alternatives;

            Decision(String selected, boolean alternatives) {
                this.selected = selected;
                this.alternatives = alternatives;
                long cells = 0;
                for (Frame frame : frames) cells += 1L + frame.excluded.size() + frame.dependencies.size();
                if (cells + retained > 262_144)
                    throw new QIOPlanningLimitException("QIO simulation branch memory budget exceeded");
                weight = (int) cells;
                retained += weight;
                mark = journal.retain();
                stepCount = steps.size();
                savedOperations = operations;
                savedNode = nextNode;
                savedMissing = missingTotal;
                for (Frame frame : frames) saved.add(frame.copy());
            }

            void restore(boolean boundary) {
                journal.rollback(mark);
                steps.subList(stepCount, steps.size()).clear();
                operations = savedOperations;
                nextNode = savedNode;
                missingTotal = savedMissing;
                frames.clear();
                for (Frame frame : saved) frames.add(frame.copy());
                Frame frame = frames.get(frames.size() - 1);
                frame.excluded.add(selected);
                frame.cycleBoundary |= boundary;
            }

            void release() {
                retained -= weight;
                journal.release();
            }
        }

        boolean retryBoundary(int index) {
            while (!decisions.isEmpty()) {
                Decision decision = decisions.removeLast();
                boolean target = decision.saved.size() == index + 1;
                // A child recipe or one concrete ingredient combination may be the only
                // cyclic choice. Exhaust that local choice before discarding its ancestor.
                if (!target && decision.saved.size() > index + 1 && decision.alternatives) {
                    decision.restore(false);
                    frames.get(frames.size() - 1).failedCycleAncestor = index;
                    decision.release();
                    QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.CYCLE_LOCAL_RETRIES, 1);
                    return true;
                }
                if (target) decision.restore(true);
                decision.release();
                if (target) {
                    QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.BACKTRACKS, 1);
                    return true;
                }
            }
            return false;
        }

        boolean backtrack(boolean boundary) {
            while (!decisions.isEmpty()) {
                Decision decision = decisions.removeLast();
                if (decision.alternatives) {
                    decision.restore(boundary);
                    decision.release();
                    QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.BACKTRACKS, 1);
                    return true;
                }
                decision.release();
            }
            return false;
        }

        void release() {
            if (best != null) recordOptimization();
            while (!decisions.isEmpty()) decisions.removeLast().release();
        }

        QIOPlanStep step(Frame frame) {
            return step(frame, frame.dependencies);
        }

        boolean productivePath(int ancestor) {
            Set<PortableResourceDescriptor> internal = new LinkedHashSet<>();
            for (int i = ancestor; i < frames.size() - 1; i++) internal.add(frames.get(i).resource);
            boolean grows = false;
            for (PortableResourceDescriptor resource : internal) {
                BigInteger net = BigInteger.ZERO;
                for (int i = ancestor; i < frames.size() - 1; i++) {
                    Frame member = frames.get(i);
                    if (member.route != null) net = net.add(BigInteger.valueOf(member.operations).multiply(
                            BigInteger.valueOf(member.route.getGuaranteedOutputAmount(resource)).subtract(
                                    BigInteger.valueOf(member.route.getExactInputs().getOrDefault(resource, 0L)))));
                }
                if (net.signum() < 0) return false;
                grows |= net.signum() > 0;
            }
            return grows;
        }

        boolean retryPrefix(int index, long count) {
            while (!decisions.isEmpty()) {
                Decision decision = decisions.removeLast();
                boolean target = decision.saved.size() == index + 1;
                if (target) {
                    decision.restore(false);
                    Frame frame = frames.get(frames.size() - 1);
                    frame.excluded.remove(decision.selected);
                    frame.batchLimit = count;
                }
                decision.release();
                if (target) {
                    QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.DEMAND_PREFIX_RETRIES, 1);
                    return true;
                }
            }
            return false;
        }

        QIOPlanStep step(Frame frame, Collection<Long> dependencies) {
            QIOPlanningRoute r = frame.route;
            return new QIOPlanStep(frame.node, r.getProviderKind(), r.getProviderId(), r.getRouteId(), r.getRecipeKey(),
                    r.getVariantId(), r.getSignature(), frame.operations, r.getExactInputs(), r.getGuaranteedOutputs(),
                    r.getOptionalOutputs(), new ArrayList<>(dependencies), r.getCandidateInputs(), r.getConfigurationInputs());
        }

        QIOPlanningTrace trace(PortableResourceDescriptor error) {
            Map<Long, QIOPlanStep> visible = new LinkedHashMap<>();
            for (QIOPlanStep step : steps) visible.put(step.getNodeId(), step);
            Set<Long> errors = new LinkedHashSet<>();
            for (int i = 0; i < frames.size(); i++) {
                Frame frame = frames.get(i);
                if (frame.route == null || frame.operations <= 0) continue;
                Set<Long> dependencies = new LinkedHashSet<>(frame.dependencies);
                // Diagnostic edges include the in-progress child; execution edges are only
                // committed when that child has actually funded its parent's inputs.
                if (i + 1 < frames.size()) {
                    Frame child = frames.get(i + 1);
                    if (child.route != null && child.operations > 0) dependencies.add(child.node);
                }
                visible.put(frame.node, step(frame, dependencies));
                errors.add(frame.node);
            }
            return new QIOPlanningTrace(new ArrayList<>(visible.values()), Collections.emptyList(), errors,
                    Collections.singleton(error), true);
        }

        QIOPlanningResult failure(Status status, String message, PortableResourceDescriptor error) {
            QIOPlanningTrace trace = trace(error);
            if (trace.getSteps().isEmpty() && failedTrace != null) trace = failedTrace;
            release();
            return completed = QIOPlanningResult.failure(status, message, visited, operations, trace);
        }
    }

    private static long ceil(long amount, long output) {
        if (output <= 0) throw new IllegalArgumentException("Requested route has no output");
        return 1 + (amount - 1) / output;
    }

    private static class Failure extends RuntimeException {
        final Status status;
        final PortableResourceDescriptor resource;

        Failure(Status status, String message, PortableResourceDescriptor resource) {
            super(message, null, false, false);
            this.status = status;
            this.resource = resource;
        }
    }

    private static final class BranchFailure extends Failure {
        final int boundary;

        BranchFailure(Status status, String message, PortableResourceDescriptor resource, int boundary) {
            super(status, message, resource);
            this.boundary = boundary;
        }
    }

    private static final class RetryPrefix extends RuntimeException {
        RetryPrefix() {
            super(null, null, false, false);
        }
    }
}
