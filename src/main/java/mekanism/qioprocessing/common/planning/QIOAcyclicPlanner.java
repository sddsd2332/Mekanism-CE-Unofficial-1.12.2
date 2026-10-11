package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import mekanism.qioprocessing.common.planning.QIOPlanningExecutor.CancellationToken;
import mekanism.qioprocessing.common.planning.QIOPlanningResult.Status;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Deterministic first-stage planner for exact acyclic routes.
 *
 * <p>Cycles are deliberately surfaced as {@link Status#CYCLE_REQUIRES_SCC}; a lower-priority
 * route is never selected merely because the SCC solver has not handled the preferred route yet.</p>
 */

/**
 * QIO 处理模块中的 QIOAcyclicPlanner 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOAcyclicPlanner implements
        QIOPlanningExecutor.PlanningTask<QIOPlanningRequest, QIOPlanningResult> {

    public static final QIOAcyclicPlanner INSTANCE = new QIOAcyclicPlanner();

    private QIOAcyclicPlanner() {
    }

    @Nonnull
    @Override
    /** 对无环依赖图执行确定性规划。 */
    public QIOPlanningResult plan(@Nonnull QIOPlanningRequest request,
                                  @Nonnull CancellationToken cancellationToken) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        QIOPlanningResult topological = QIOTopologicalPlanner.INSTANCE.tryPlan(request,
                cancellationToken);
        if (topological != null) return topological;
        Session session = QIOPlanningExecutor.taskLocal(QIOAcyclicPlanner.class, Session::new);
        if (session.state == null || !session.state.request.getPlanId().equals(request.getPlanId()) ||
                session.state.request.getPlanRevision() != request.getPlanRevision() ||
                session.state.request.getRootAmount() != request.getRootAmount() ||
                !session.state.request.getRootResource().equals(request.getRootResource()) ||
                session.state.request.getSnapshot().getSourceRevisions() != request.getSnapshot().getSourceRevisions()) {
            session.state = new PlannerState(request, cancellationToken);
        }
        PlannerState state = session.state;
        try {
            if (request.getSnapshot().getRoutesProducing(request.getRootResource()).isEmpty()) {
                return QIOPlanningResult.failure(Status.NO_ROUTE,
                        "No enabled route produces " + request.getRootResource(), 0, 0,
                        QIOPlanningTrace.rootFailure(request.getRootResource()));
            }
            state.run();
            return QIOPlanningResult.success(state.selectedPlan, state.exploredNodes, state.selectedOperations);
        } catch (PlanningFailure failure) {
            return QIOPlanningResult.failure(failure.status, failure.getMessage(),
                    state.exploredNodes, state.plannedOperations,
                    state.failureTrace(failure.resource));
        } catch (ArithmeticException failure) {
            return QIOPlanningResult.failure(Status.AMOUNT_OVERFLOW,
                    failure.getMessage() == null ? "QIO planning amount overflow" : failure.getMessage(),
                    state.exploredNodes, state.plannedOperations,
                    state.failureTrace(request.getRootResource()));
        } catch (QIOPlanningContinuation pending) {
            throw pending;
        } catch (QIOPlanningLimitException limit) {
            return QIOPlanningResult.failure(Status.TOO_COMPLEX, limit.getMessage(), state.exploredNodes, state.plannedOperations);
        } catch (RuntimeException failure) {
            return QIOPlanningResult.failure(Status.INTERNAL_ERROR,
                    failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(),
                    state.exploredNodes, state.plannedOperations,
                    state.failureTrace(request.getRootResource()));
        }
    }

    private static final class Session {
        private PlannerState state;
    }

    private static final class PlannerState {

        private final QIOPlanningRequest request;
        private final QIOPlanningSnapshot snapshot;
        private final CancellationToken cancellationToken;
        private final QIOPlanningJournal journal = new QIOPlanningJournal();
        private final Map<PortableResourceDescriptor, Long> stock = journal.map();
        private final Map<PortableResourceDescriptor, Deque<SurplusLot>> surplus = journal.map();
        private final Map<PortableResourceDescriptor, Long> externalRequirements = journal.map();
        private final List<QIOPlanStep> steps = new ArrayList<>();
        private final Deque<PortableResourceDescriptor> activeResources = new ArrayDeque<>();
        private final Set<Long> errorNodeIds = new LinkedHashSet<>();
        private long nextNodeId;
        private long plannedOperations;
        private int exploredNodes;
        private final Deque<Frame> frames = new ArrayDeque<>();
        private boolean initialized;
        private int retainedDecisionWeight;
        private final Deque<Decision> decisions = new ArrayDeque<>();
        private QIOCraftPlan selectedPlan;
        private long selectedOperations;
        private java.math.BigInteger selectedMissing;
        private int branchAttempts;

        private PlannerState(QIOPlanningRequest request, CancellationToken cancellationToken) {
            this.request = request;
            snapshot = request.getSnapshot();
            this.cancellationToken = cancellationToken;
            stock.putAll(snapshot.getAvailableResources());
        }

        /**
         * Explicit frames keep branch and material ledgers intact while a selected grid is validated.
         */
        private void run() {
            if (!initialized) {
                frames.addLast(new Frame(request.getRootResource(), request.getRootAmount(), 0, true));
                initialized = true;
            }
            while (true) {
                checkCancelled();
                if (frames.isEmpty()) {
                    java.math.BigInteger missing = missingAmount();
                    if (selectedPlan == null || missing.compareTo(selectedMissing) < 0) {
                        selectedPlan = new QIOCraftPlan(request.getPlanId(), request.getPlanRevision(), snapshot.getSourceRevisions(),
                                request.getRootResource(), request.getRootAmount(), externalRequirements, steps);
                        selectedOperations = plannedOperations;
                        selectedMissing = missing;
                    }
                    if (missing.signum() == 0 || !backtrack()) {
                        releaseDecisions();
                        return;
                    }
                    continue;
                }
                Frame frame = frames.peekLast();
                if (!frame.entered) {
                    if (frame.depth > snapshot.getMaximumDepth() || ++exploredNodes > snapshot.getMaximumNodes()) {
                        throw new PlanningFailure(Status.TOO_COMPLEX, "QIO dependency frame budget exceeded", frame.resource);
                    }
                    if (!frame.root && activeResources.contains(frame.resource)) {
                        throw new PlanningFailure(Status.CYCLE_REQUIRES_SCC, "QIO preferred route requires cyclic planning: " + frame.resource, frame.resource);
                    }
                    if (!frame.root) {
                        Consumption generated = consumeSurplus(frame.resource, frame.remaining);
                        frame.remaining = generated.remaining;
                        frame.dependencies.addAll(generated.sources);
                        long present = stock.getOrDefault(frame.resource, 0L);
                        long consumed = Math.min(present, frame.remaining);
                        if (consumed > 0) {
                            stock.put(frame.resource, present - consumed);
                            addExternalRequirement(frame.resource, consumed);
                            frame.remaining -= consumed;
                        }
                    }
                    frame.entered = true;
                    if (frame.remaining == 0) {
                        finishFrame(frame);
                        continue;
                    }
                    if (snapshot.getRoutesProducing(frame.resource).isEmpty()) {
                        if (frame.root)
                            throw new PlanningFailure(Status.NO_ROUTE, "No route produces " + frame.resource, frame.resource);
                        addExternalRequirement(frame.resource, frame.remaining);
                        finishFrame(frame);
                        continue;
                    }
                    activeResources.addLast(frame.resource);
                    frame.active = true;
                }
                if (frame.route == null) {
                    if (frame.routes == null) {
                        frame.routes = new ArrayList<>(snapshot.getRoutesProducing(frame.resource));
                        QIOPlanningRoute first = preferredVariant(frame.routes);
                        frame.routes.remove(first);
                        frame.routes.add(0, first);
                    }
                    Map<PortableResourceDescriptor, Long> available = availableForSelection();
                    QIOPlanningRoute preferred = null;
                    QIOPlanningRoute selected = null;
                    while (frame.recipeCursor < frame.routes.size()) {
                        preferred = frame.routes.get(frame.recipeCursor);
                        long requested = ceilDivide(frame.remaining, preferred.getGuaranteedOutputAmount(frame.resource));
                        selected = preferred.selectWorkbench(available, requested, cancellationToken::isCancelled, frame.excluded, frame.resource);
                        if (selected != null) break;
                        frame.recipeCursor++;
                    }
                    if (selected == null) {
                        if (backtrack()) continue;
                        if (selectedPlan != null) return;
                        throw new PlanningFailure(Status.NO_ROUTE, "No valid candidate for " + frame.resource, frame.resource);
                    }
                    QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.BRANCH_ATTEMPTS, 1);
                    if (++branchAttempts > snapshot.getMaximumNodes()) throw new PlanningFailure(Status.TOO_COMPLEX,
                            "QIO branch attempt budget exceeded", frame.resource);
                    long output = selected.getGuaranteedOutputAmount(frame.resource);
                    if (output <= 0)
                        throw new PlanningFailure(Status.NO_ROUTE, "Verified route no longer produces requested resource", frame.resource);
                    long count = ceilDivide(frame.remaining, output);
                    if (selected.requiresSequentialWorkbench()) count = 1;
                    if (selected.verification() != null) {
                        long stocked = count;
                        for (Map.Entry<PortableResourceDescriptor, Long> input : selected.getExactInputs().entrySet()) {
                            stocked = Math.min(stocked, available.getOrDefault(input.getKey(), 0L) / input.getValue());
                        }
                        if (stocked > 0) count = stocked;
                    }
                    // Everything before this point can yield without committing this batch.
                    if (nextNodeId >= snapshot.getMaximumNodes()) throw new PlanningFailure(Status.TOO_COMPLEX,
                            "QIO batch node budget exceeded", frame.resource);
                    if (preferred.hasAlternatives() || frame.routes.size() > 1)
                        decisions.addLast(new Decision(selected.getStableId()));
                    frame.route = selected;
                    frame.operations = count;
                    frame.nodeId = nextNodeId++;
                    frame.inputs = new ArrayList<>(selected.getExactInputs().entrySet());
                    frame.nextInput = 0;
                    frame.batchDependencies.clear();
                    addOperations(count, frame.resource);
                }
                if (frame.nextInput < frame.inputs.size()) {
                    Map.Entry<PortableResourceDescriptor, Long> input = frame.inputs.get(frame.nextInput++);
                    frames.addLast(new Frame(input.getKey(), Math.multiplyExact(input.getValue(), frame.operations), frame.depth + 1, false));
                    continue;
                }
                QIOPlanningRoute route = frame.route;
                steps.add(new QIOPlanStep(frame.nodeId, route.getProviderKind(), route.getProviderId(), route.getRouteId(),
                        route.getRecipeKey(), route.getVariantId(), route.getSignature(), frame.operations, route.getExactInputs(),
                        route.getGuaranteedOutputs(), route.getOptionalOutputs(), new ArrayList<>(frame.batchDependencies),
                        route.getCandidateInputs(), route.getConfigurationInputs()));
                for (Map.Entry<PortableResourceDescriptor, Long> output : route.getGuaranteedOutputs().entrySet()) {
                    addSurplus(output.getKey(), Math.multiplyExact(output.getValue(), frame.operations), frame.nodeId, output.getKey().equals(frame.resource));
                }
                long delivered = Math.min(frame.remaining, Math.multiplyExact(route.getGuaranteedOutputAmount(frame.resource), frame.operations));
                Consumption produced = consumeSurplus(frame.resource, delivered);
                if (produced.remaining != 0)
                    throw new IllegalStateException("Workbench frame lost its credited output");
                frame.dependencies.addAll(produced.sources);
                frame.remaining -= delivered;
                frame.excluded.clear();
                frame.routes = null;
                frame.recipeCursor = 0;
                frame.route = null;
                if (frame.remaining == 0) finishFrame(frame);
            }
        }

        private java.math.BigInteger missingAmount() {
            java.math.BigInteger missing = java.math.BigInteger.ZERO;
            for (Map.Entry<PortableResourceDescriptor, Long> entry : externalRequirements.entrySet()) {
                missing = missing.add(java.math.BigInteger.valueOf(Math.max(0, entry.getValue() - snapshot.getAvailableResources().getOrDefault(entry.getKey(), 0L))));
            }
            return missing;
        }

        /**
         * Reopen the most recent choice, including choices in already completed sibling frames.
         */
        private boolean backtrack() {
            Decision decision = decisions.pollLast();
            if (decision == null) return false;
            QIOPlanningMetrics.current().add(QIOPlanningMetrics.Metric.BACKTRACKS, 1);
            decision.restore();
            decision.release();
            return true;
        }

        private void releaseDecisions() {
            while (!decisions.isEmpty()) decisions.removeLast().release();
        }

        private final class Decision {
            final int mark, stepCount, weight;
            final long nextNode, operations;
            final List<Frame> suspended;
            final String selected;

            Decision(String selected) {
                long cells = 0;
                for (Frame frame : frames)
                    cells += 1L + frame.excluded.size() + frame.dependencies.size() + frame.batchDependencies.size();
                if (cells + retainedDecisionWeight > 262_144) throw new PlanningFailure(Status.TOO_COMPLEX,
                        "QIO decision stack budget exceeded", frames.peekLast().resource);
                weight = (int) cells;
                retainedDecisionWeight += weight;
                mark = journal.retain();
                stepCount = steps.size();
                nextNode = nextNodeId;
                operations = plannedOperations;
                suspended = new ArrayList<>(frames.size());
                for (Frame frame : frames) suspended.add(copyFrame(frame));
                this.selected = selected;
            }

            void restore() {
                journal.rollback(mark);
                steps.subList(stepCount, steps.size()).clear();
                nextNodeId = nextNode;
                plannedOperations = operations;
                frames.clear();
                activeResources.clear();
                for (Frame saved : suspended) {
                    Frame frame = copyFrame(saved);
                    frames.addLast(frame);
                    if (frame.active) activeResources.addLast(frame.resource);
                }
                Frame current = frames.peekLast();
                current.excluded.add(selected);
                current.route = null;
            }

            void release() {
                retainedDecisionWeight -= weight;
                journal.release();
            }
        }

        private Frame copyFrame(Frame source) {
            Frame copy = new Frame(source.resource, source.remaining, source.depth, source.root);
            copy.routes = source.routes;
            copy.recipeCursor = source.recipeCursor;
            copy.excluded.addAll(source.excluded);
            copy.operations = source.operations;
            copy.nodeId = source.nodeId;
            copy.entered = source.entered;
            copy.active = source.active;
            copy.route = source.route;
            copy.inputs = source.inputs;
            copy.nextInput = source.nextInput;
            copy.dependencies.addAll(source.dependencies);
            copy.batchDependencies.addAll(source.batchDependencies);
            return copy;
        }

        private static Deque<SurplusLot> copyLots(Deque<SurplusLot> source) {
            Deque<SurplusLot> copy = new ArrayDeque<>();
            if (source != null)
                for (SurplusLot value : source) copy.addLast(new SurplusLot(value.sourceNodeId, value.amount));
            return copy;
        }

        private void finishFrame(Frame frame) {
            if (frames.removeLast() != frame) throw new IllegalStateException("QIO frame order changed");
            if (frame.active && !activeResources.removeLast().equals(frame.resource))
                throw new IllegalStateException("QIO active resource order changed");
            Frame parent = frames.peekLast();
            if (parent != null) parent.batchDependencies.addAll(frame.dependencies);
        }

        private Map<PortableResourceDescriptor, Long> availableForSelection() {
            Map<PortableResourceDescriptor, Long> available = new TreeMap<>(stock);
            surplus.forEach((resource, lots) -> {
                for (SurplusLot lot : lots) available.merge(resource, lot.amount, PlannerState::saturatedAdd);
            });
            // Ancestor batches have committed to inputs that have not been visited yet.
            for (Frame frame : frames)
                if (frame.route != null) {
                    for (int index = frame.nextInput; index < frame.inputs.size(); index++) {
                        Map.Entry<PortableResourceDescriptor, Long> input = frame.inputs.get(index);
                        long reserved = Math.multiplyExact(input.getValue(), frame.operations);
                        available.put(input.getKey(), Math.max(0, available.getOrDefault(input.getKey(), 0L) - reserved));
                    }
                }
            return available;
        }

        private final class Frame {
            List<QIOPlanningRoute> routes;
            int recipeCursor;
            final Set<String> excluded = new LinkedHashSet<>();
            final PortableResourceDescriptor resource;
            final int depth;
            final boolean root;
            long remaining, operations, nodeId;
            boolean entered, active;
            QIOPlanningRoute route;
            List<Map.Entry<PortableResourceDescriptor, Long>> inputs;
            int nextInput;
            final Set<Long> dependencies = new LinkedHashSet<>();
            final Set<Long> batchDependencies = new LinkedHashSet<>();

            Frame(PortableResourceDescriptor resource, long amount, int depth, boolean root) {
                this.resource = resource;
                this.remaining = amount;
                this.depth = depth;
                this.root = root;
            }
        }

        private Consumption consumeSurplus(PortableResourceDescriptor resource, long amount) {
            long remaining = amount;
            Set<Long> sources = new LinkedHashSet<>();
            Deque<SurplusLot> lots = copyLots(surplus.get(resource));
            while (remaining > 0 && lots != null && !lots.isEmpty()) {
                SurplusLot lot = lots.peekFirst();
                long consumed = Math.min(remaining, lot.amount);
                remaining -= consumed;
                lot.amount -= consumed;
                sources.add(lot.sourceNodeId);
                if (lot.amount == 0) {
                    lots.removeFirst();
                }
            }
            if (lots.isEmpty()) surplus.remove(resource);
            else surplus.put(resource, lots);
            return new Consumption(remaining, sources);
        }

        private void addSurplus(PortableResourceDescriptor resource, long amount, long sourceNodeId,
                                boolean preferForCurrentDemand) {
            if (amount <= 0) {
                throw new IllegalArgumentException("QIO surplus amount must be positive");
            }
            Deque<SurplusLot> lots = copyLots(surplus.get(resource));
            SurplusLot lot = new SurplusLot(sourceNodeId, amount);
            if (preferForCurrentDemand) {
                lots.addFirst(lot);
            } else {
                lots.addLast(lot);
            }
            surplus.put(resource, lots);
        }

        private void addExternalRequirement(PortableResourceDescriptor resource, long amount) {
            externalRequirements.merge(resource, amount, Math::addExact);
        }

        private QIOPlanningRoute preferredVariant(List<QIOPlanningRoute> candidates) {
            QIOPlanningRoute first = candidates.get(0);
            QIOPlanningRoute selected = first;
            long selectedDeficit = routeDeficit(first);
            for (int index = 1; index < candidates.size(); index++) {
                QIOPlanningRoute candidate = candidates.get(index);
                if (candidate.getRoutePriority() != first.getRoutePriority() ||
                        !candidate.getLogicalId().equals(first.getLogicalId())) {
                    break;
                }
                long deficit = routeDeficit(candidate);
                if (deficit < selectedDeficit || deficit == selectedDeficit &&
                        (candidate.getVariantPriority() > selected.getVariantPriority() ||
                                candidate.getVariantPriority() == selected.getVariantPriority() &&
                                        candidate.getStableId().compareTo(selected.getStableId()) < 0)) {
                    selected = candidate;
                    selectedDeficit = deficit;
                }
            }
            return selected;
        }

        private long routeDeficit(QIOPlanningRoute route) {
            long deficit = 0;
            for (Map.Entry<PortableResourceDescriptor, Long> input :
                    route.getExactInputs().entrySet()) {
                long available = stock.getOrDefault(input.getKey(), 0L);
                Deque<SurplusLot> lots = surplus.get(input.getKey());
                if (lots != null) {
                    for (SurplusLot lot : lots) {
                        available = saturatedAdd(available, lot.amount);
                    }
                }
                if (available < input.getValue()) {
                    deficit = saturatedAdd(deficit, input.getValue() - available);
                }
            }
            return deficit;
        }

        private void addOperations(long operations,
                                   PortableResourceDescriptor resource) {
            plannedOperations = Math.addExact(plannedOperations, operations);
            if (plannedOperations > snapshot.getMaximumOperations()) {
                throw new PlanningFailure(Status.TOO_COMPLEX,
                        "QIO planning operation budget exceeded " +
                                snapshot.getMaximumOperations(), resource);
            }
        }

        private void checkCancelled() {
            if (cancellationToken.isCancelled() || Thread.currentThread().isInterrupted()) {
                throw new PlanningFailure(Status.CANCELLED, "QIO planning was cancelled",
                        activeResources.peekLast());
            }
        }

        private QIOPlanningTrace failureTrace(
                PortableResourceDescriptor errorResource) {
            List<QIOPlanStep> traceSteps = new ArrayList<>(steps);
            for (Frame frame : frames)
                if (frame.route != null) {
                    QIOPlanningRoute route = frame.route;
                    traceSteps.add(new QIOPlanStep(frame.nodeId, route.getProviderKind(), route.getProviderId(), route.getRouteId(),
                            route.getRecipeKey(), route.getVariantId(), route.getSignature(), frame.operations, route.getExactInputs(),
                            route.getGuaranteedOutputs(), route.getOptionalOutputs(), new ArrayList<>(frame.batchDependencies),
                            route.getCandidateInputs(), route.getConfigurationInputs()));
                    errorNodeIds.add(frame.nodeId);
                }
            Set<PortableResourceDescriptor> resources = errorResource == null ?
                    Collections.emptySet() : Collections.singleton(errorResource);
            return new QIOPlanningTrace(traceSteps, Collections.emptyList(), errorNodeIds,
                    resources, errorNodeIds.isEmpty());
        }

        private static long saturatedAdd(long left, long right) {
            if (right > Long.MAX_VALUE - left) {
                return Long.MAX_VALUE;
            }
            return left + right;
        }
    }

    private static long ceilDivide(long amount, long divisor) {
        return 1 + (amount - 1) / divisor;
    }

    private static final class SurplusLot {

        private final long sourceNodeId;
        private long amount;

        private SurplusLot(long sourceNodeId, long amount) {
            this.sourceNodeId = sourceNodeId;
            this.amount = amount;
        }
    }

    private static final class Consumption {

        private final long remaining;
        private final Set<Long> sources;

        private Consumption(long remaining, Collection<Long> sources) {
            this.remaining = remaining;
            this.sources = Collections.unmodifiableSet(new LinkedHashSet<>(sources));
        }
    }

    private static final class PlanningFailure extends RuntimeException {

        private final Status status;
        private final PortableResourceDescriptor resource;

        private PlanningFailure(Status status, String message,
                                PortableResourceDescriptor resource) {
            super(message, null, false, false);
            this.status = status;
            this.resource = resource;
        }
    }
}
