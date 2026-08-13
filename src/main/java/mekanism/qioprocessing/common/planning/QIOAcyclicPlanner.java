package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import mekanism.qioprocessing.common.planning.QIOPlanningExecutor.CancellationToken;
import mekanism.qioprocessing.common.planning.QIOPlanningResult.Status;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Deterministic first-stage planner for exact acyclic routes.
 *
 * <p>Cycles are deliberately surfaced as {@link Status#CYCLE_REQUIRES_SCC}; a lower-priority
 * route is never selected merely because the SCC solver has not handled the preferred route yet.</p>
 */
public final class QIOAcyclicPlanner implements
      QIOPlanningExecutor.PlanningTask<QIOPlanningRequest, QIOPlanningResult> {

    public static final QIOAcyclicPlanner INSTANCE = new QIOAcyclicPlanner();

    private QIOAcyclicPlanner() {
    }

    @Nonnull
    @Override
    public QIOPlanningResult plan(@Nonnull QIOPlanningRequest request,
          @Nonnull CancellationToken cancellationToken) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        QIOPlanningResult topological = QIOTopologicalPlanner.INSTANCE.tryPlan(request,
              cancellationToken);
        if (topological != null) return topological;
        PlannerState state = new PlannerState(request, cancellationToken);
        try {
            if (request.getSnapshot().getRoutesProducing(request.getRootResource()).isEmpty()) {
                return QIOPlanningResult.failure(Status.NO_ROUTE,
                      "No enabled route produces " + request.getRootResource(), 0, 0,
                      QIOPlanningTrace.rootFailure(request.getRootResource()));
            }
            state.produce(request.getRootResource(), request.getRootAmount(), 0);
            QIOCraftPlan plan = new QIOCraftPlan(request.getPlanId(), request.getPlanRevision(),
                  request.getSnapshot().getSourceRevisions(), request.getRootResource(),
                  request.getRootAmount(), state.externalRequirements, state.steps);
            return QIOPlanningResult.success(plan, state.exploredNodes, state.plannedOperations);
        } catch (PlanningFailure failure) {
            return QIOPlanningResult.failure(failure.status, failure.getMessage(),
                  state.exploredNodes, state.plannedOperations,
                  state.failureTrace(failure.resource));
        } catch (ArithmeticException failure) {
            return QIOPlanningResult.failure(Status.AMOUNT_OVERFLOW,
                  failure.getMessage() == null ? "QIO planning amount overflow" : failure.getMessage(),
                  state.exploredNodes, state.plannedOperations,
                  state.failureTrace(request.getRootResource()));
        } catch (RuntimeException failure) {
            return QIOPlanningResult.failure(Status.INTERNAL_ERROR,
                  failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(),
                  state.exploredNodes, state.plannedOperations,
                  state.failureTrace(request.getRootResource()));
        }
    }

    private static final class PlannerState {

        private final QIOPlanningRequest request;
        private final QIOPlanningSnapshot snapshot;
        private final CancellationToken cancellationToken;
        private final Map<PortableResourceDescriptor, Long> stock = new TreeMap<>();
        private final Map<PortableResourceDescriptor, Deque<SurplusLot>> surplus = new TreeMap<>();
        private final Map<PortableResourceDescriptor, Long> externalRequirements = new TreeMap<>();
        private final List<QIOPlanStep> steps = new ArrayList<>();
        private final Deque<PortableResourceDescriptor> activeResources = new ArrayDeque<>();
        private final Set<Long> errorNodeIds = new LinkedHashSet<>();
        private long nextNodeId;
        private long plannedOperations;
        private int exploredNodes;

        private PlannerState(QIOPlanningRequest request, CancellationToken cancellationToken) {
            this.request = request;
            snapshot = request.getSnapshot();
            this.cancellationToken = cancellationToken;
            stock.putAll(snapshot.getAvailableResources());
        }

        private Set<Long> produce(PortableResourceDescriptor resource, long amount, int depth) {
            checkCancelled();
            if (amount <= 0) {
                throw new IllegalArgumentException("QIO production amount must be positive");
            }
            if (depth > snapshot.getMaximumDepth()) {
                throw new PlanningFailure(Status.TOO_COMPLEX,
                      "QIO planning depth exceeded " + snapshot.getMaximumDepth(), resource);
            }
            if (++exploredNodes > snapshot.getMaximumNodes()) {
                throw new PlanningFailure(Status.TOO_COMPLEX,
                      "QIO planning node budget exceeded " + snapshot.getMaximumNodes(),
                      resource);
            }
            List<QIOPlanningRoute> candidates = snapshot.getRoutesProducing(resource);
            if (candidates.isEmpty()) {
                throw new PlanningFailure(Status.NO_ROUTE, "No route produces " + resource,
                      resource);
            }
            QIOPlanningRoute route = chooseVariant(candidates);
            long outputPerOperation = route.getGuaranteedOutputAmount(resource);
            if (outputPerOperation <= 0) {
                throw new IllegalStateException("Indexed QIO route does not produce its resource");
            }
            long operations = ceilDivide(amount, outputPerOperation);
            if (nextNodeId == Long.MAX_VALUE) {
                throw new ArithmeticException("QIO plan node identity overflow");
            }
            long nodeId = nextNodeId++;
            Set<Long> dependencies = new LinkedHashSet<>();
            boolean active = false;
            try {
                addOperations(operations, resource);
                activeResources.addLast(resource);
                active = true;
                for (Map.Entry<PortableResourceDescriptor, Long> input :
                      route.getExactInputs().entrySet()) {
                    dependencies.addAll(require(input.getKey(),
                          Math.multiplyExact(input.getValue(), operations), depth + 1));
                }
            } catch (PlanningFailure failure) {
                if (failure.traceNodeId >= 0) dependencies.add(failure.traceNodeId);
                addPartialStep(nodeId, route, operations, dependencies);
                if (failure.traceNodeId < 0) {
                    failure.traceNodeId = nodeId;
                    errorNodeIds.add(nodeId);
                }
                throw failure;
            } finally {
                if (active) {
                    PortableResourceDescriptor removed = activeResources.removeLast();
                    if (!removed.equals(resource)) {
                        throw new IllegalStateException(
                              "QIO planning recursion stack was corrupted");
                    }
                }
            }
            QIOPlanStep step = new QIOPlanStep(nodeId, route.getProviderKind(),
                  route.getProviderId(), route.getRouteId(), route.getRecipeKey(),
                  route.getVariantId(), route.getSignature(), operations, route.getExactInputs(),
                  route.getGuaranteedOutputs(), route.getOptionalOutputs(),
                  new ArrayList<>(dependencies), route.getCandidateInputs(),
                  route.getConfigurationInputs());
            steps.add(step);
            for (Map.Entry<PortableResourceDescriptor, Long> output :
                  route.getGuaranteedOutputs().entrySet()) {
                long produced = Math.multiplyExact(output.getValue(), operations);
                addSurplus(output.getKey(), produced, nodeId, output.getKey().equals(resource));
            }
            Consumption consumed = consumeSurplus(resource, amount);
            if (consumed.remaining != 0) {
                throw new IllegalStateException("QIO route output was not credited to the planner ledger");
            }
            return consumed.sources;
        }

        private Set<Long> require(PortableResourceDescriptor resource, long amount, int depth) {
            checkCancelled();
            if (activeResources.contains(resource)) {
                StringBuilder cycle = new StringBuilder();
                for (PortableResourceDescriptor active : activeResources) {
                    if (cycle.length() > 0) {
                        cycle.append(" -> ");
                    }
                    cycle.append(active);
                }
                cycle.append(" -> ").append(resource);
                throw new PlanningFailure(Status.CYCLE_REQUIRES_SCC,
                      "Preferred QIO route enters a cycle: " + cycle, resource);
            }
            Consumption generated = consumeSurplus(resource, amount);
            long remaining = generated.remaining;
            Set<Long> dependencies = new LinkedHashSet<>(generated.sources);
            if (remaining == 0) {
                return dependencies;
            }

            long stored = stock.getOrDefault(resource, 0L);
            long fromStock = Math.min(stored, remaining);
            if (fromStock > 0) {
                remaining -= fromStock;
                long left = stored - fromStock;
                if (left == 0) {
                    stock.remove(resource);
                } else {
                    stock.put(resource, left);
                }
                addExternalRequirement(resource, fromStock);
            }
            if (remaining == 0) {
                return dependencies;
            }

            if (snapshot.getRoutesProducing(resource).isEmpty()) {
                addExternalRequirement(resource, remaining);
                return dependencies;
            }
            dependencies.addAll(produce(resource, remaining, depth));
            return dependencies;
        }

        private Consumption consumeSurplus(PortableResourceDescriptor resource, long amount) {
            long remaining = amount;
            Set<Long> sources = new LinkedHashSet<>();
            Deque<SurplusLot> lots = surplus.get(resource);
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
            if (lots != null && lots.isEmpty()) {
                surplus.remove(resource);
            }
            return new Consumption(remaining, sources);
        }

        private void addSurplus(PortableResourceDescriptor resource, long amount, long sourceNodeId,
              boolean preferForCurrentDemand) {
            if (amount <= 0) {
                throw new IllegalArgumentException("QIO surplus amount must be positive");
            }
            Deque<SurplusLot> lots = surplus.computeIfAbsent(resource,
                  ignored -> new ArrayDeque<>());
            SurplusLot lot = new SurplusLot(sourceNodeId, amount);
            if (preferForCurrentDemand) {
                lots.addFirst(lot);
            } else {
                lots.addLast(lot);
            }
        }

        private void addExternalRequirement(PortableResourceDescriptor resource, long amount) {
            externalRequirements.merge(resource, amount, Math::addExact);
        }

        private QIOPlanningRoute chooseVariant(List<QIOPlanningRoute> candidates) {
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

        private void addPartialStep(long nodeId, QIOPlanningRoute route, long operations,
              Set<Long> dependencies) {
            steps.add(new QIOPlanStep(nodeId, route.getProviderKind(), route.getProviderId(),
                  route.getRouteId(), route.getRecipeKey(), route.getVariantId(),
                  route.getSignature(), operations, route.getExactInputs(),
                  route.getGuaranteedOutputs(), route.getOptionalOutputs(),
                  new ArrayList<>(dependencies), route.getCandidateInputs(),
                  route.getConfigurationInputs()));
        }

        private QIOPlanningTrace failureTrace(
              PortableResourceDescriptor errorResource) {
            Set<PortableResourceDescriptor> resources = errorResource == null ?
                  Collections.emptySet() : Collections.singleton(errorResource);
            return new QIOPlanningTrace(steps, Collections.emptyList(), errorNodeIds,
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
        private long traceNodeId = -1;

        private PlanningFailure(Status status, String message,
              PortableResourceDescriptor resource) {
            super(message, null, false, false);
            this.status = status;
            this.resource = resource;
        }
    }
}
