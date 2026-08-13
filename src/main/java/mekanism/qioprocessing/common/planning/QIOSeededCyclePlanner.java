package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import mekanism.qioprocessing.common.content.plan.QIOCyclePlanNode;
import mekanism.qioprocessing.common.planning.QIOPlanningExecutor.CancellationToken;
import mekanism.qioprocessing.common.planning.QIOPlanningResult.Status;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Comparator;
import java.math.BigInteger;

/**
 * Bounded second-stage planner that turns a seeded recipe cycle into an executable acyclic
 * operation schedule. Each emitted node is one real recipe operation and only depends on nodes
 * that have already produced an input consumed by that operation.
 */
public final class QIOSeededCyclePlanner implements
      QIOPlanningExecutor.PlanningTask<QIOPlanningRequest, QIOPlanningResult> {

    public static final QIOSeededCyclePlanner INSTANCE = new QIOSeededCyclePlanner();

    private QIOSeededCyclePlanner() {
    }

    @Nonnull
    @Override
    public QIOPlanningResult plan(@Nonnull QIOPlanningRequest request,
          @Nonnull CancellationToken cancellationToken) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        PlannerState state = new PlannerState(request, cancellationToken);
        try {
            if (request.getSnapshot().getRoutesProducing(request.getRootResource()).isEmpty()) {
                return QIOPlanningResult.failure(Status.NO_ROUTE,
                      "No enabled route produces " + request.getRootResource(), 0, 0,
                      QIOPlanningTrace.rootFailure(request.getRootResource()));
            }
            long target = Math.addExact(state.available(request.getRootResource()),
                  request.getRootAmount());
            state.ensure(request.getRootResource(), target, 0, true);
            QIOCraftPlan plan = new QIOCraftPlan(request.getPlanId(), request.getPlanRevision(),
                  request.getSnapshot().getSourceRevisions(), request.getRootResource(),
                  request.getRootAmount(), state.externalRequirements, state.steps,
                  state.cycleNodes);
            return QIOPlanningResult.success(plan, state.exploredNodes, state.plannedOperations);
        } catch (PlanningFailure failure) {
            return QIOPlanningResult.failure(failure.status, failure.getMessage(),
                  state.exploredNodes, state.plannedOperations,
                  state.failureTrace());
        } catch (ArithmeticException failure) {
            return QIOPlanningResult.failure(Status.AMOUNT_OVERFLOW,
                  failure.getMessage() == null ? "QIO planning amount overflow" : failure.getMessage(),
                  state.exploredNodes, state.plannedOperations, state.failureTrace());
        } catch (RuntimeException failure) {
            return QIOPlanningResult.failure(Status.INTERNAL_ERROR,
                  failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(),
                  state.exploredNodes, state.plannedOperations, state.failureTrace());
        }
    }

    private static final class PlannerState {

        private final QIOPlanningSnapshot snapshot;
        private final CancellationToken cancellationToken;
        private final Map<PortableResourceDescriptor, Long> stock = new TreeMap<>();
        private final Map<PortableResourceDescriptor, Deque<GeneratedLot>> generated = new TreeMap<>();
        private final Map<PortableResourceDescriptor, Long> externalRequirements = new TreeMap<>();
        private final Map<PortableResourceDescriptor, Long> seedRequirements = new TreeMap<>();
        private final List<QIOPlanStep> steps = new ArrayList<>();
        private final List<QIOCyclePlanNode> cycleNodes = new ArrayList<>();
        private final Deque<PortableResourceDescriptor> activeResources = new ArrayDeque<>();
        private final PortableResourceDescriptor rootResource;
        private long nextNodeId;
        private long plannedOperations;
        private int exploredNodes;

        private PlannerState(QIOPlanningRequest request, CancellationToken cancellationToken) {
            snapshot = request.getSnapshot();
            this.cancellationToken = cancellationToken;
            rootResource = request.getRootResource();
            stock.putAll(snapshot.getAvailableResources());
        }

        private QIOPlanningTrace failureTrace() {
            return new QIOPlanningTrace(steps, cycleNodes, Collections.emptySet(),
                  Collections.singleton(rootResource), true);
        }

        private void ensure(PortableResourceDescriptor resource, long minimumAvailable,
              int depth, boolean netDemand) {
            checkCancelled();
            if (minimumAvailable <= 0) {
                throw new IllegalArgumentException("QIO required availability must be positive");
            }
            if (depth > snapshot.getMaximumDepth()) {
                throw new PlanningFailure(Status.TOO_COMPLEX,
                      "QIO cycle planning depth exceeded " + snapshot.getMaximumDepth());
            }
            exploredNodes = Math.addExact(exploredNodes, 1);
            if (exploredNodes > snapshot.getMaximumNodes()) {
                throw new PlanningFailure(Status.TOO_COMPLEX,
                      "QIO cycle planning node budget exceeded " + snapshot.getMaximumNodes());
            }
            if (available(resource) >= minimumAvailable) {
                return;
            }
            CycleAnalysis cycle = findCycleForDemand(resource);
            if (cycle.solution != null) {
                executeCompressedCycle(cycle.solution, resource, minimumAvailable, depth + 1);
                return;
            }
            if (cycle.circularPath != null) {
                if (netDemand) {
                    throw new PlanningFailure(Status.UNRESOLVABLE_CYCLE,
                          "Circular QIO recipe route: " + cycle.circularPath);
                }
                // A conserved recursive input is a seed, not a producible output. Match AE2's
                // seeded-recursion behavior by requesting it once from storage/external input.
                addExternalStock(resource, minimumAvailable - available(resource));
                return;
            }
            List<QIOPlanningRoute> candidates = snapshot.getRoutesProducing(resource);
            if (candidates.isEmpty()) {
                addExternalStock(resource, minimumAvailable - available(resource));
                return;
            }
            if (activeResources.contains(resource)) {
                throw new PlanningFailure(Status.UNRESOLVABLE_CYCLE,
                      "QIO recipe cycle cannot produce a net demand for " +
                            cyclePath(resource));
            }

            QIOPlanningRoute route = chooseVariant(candidates);
            activeResources.addLast(resource);
            try {
                while (available(resource) < minimumAvailable) {
                    checkCancelled();
                    long before = available(resource);
                    executeOne(route, depth + 1);
                    if (available(resource) <= before && plannedOperations >= snapshot.getMaximumOperations()) {
                        throw new PlanningFailure(Status.TOO_COMPLEX,
                              "QIO cycle made no progress within the operation budget");
                    }
                }
            } finally {
                PortableResourceDescriptor removed = activeResources.removeLast();
                if (!removed.equals(resource)) {
                    throw new IllegalStateException("QIO cycle planning recursion stack was corrupted");
                }
            }
        }

        private void executeOne(QIOPlanningRoute route, int depth) {
            addOperation();
            for (Map.Entry<PortableResourceDescriptor, Long> input :
                  route.getExactInputs().entrySet()) {
                ensure(input.getKey(), input.getValue(), depth, false);
            }
            executeResolved(route);
        }

        private void executeResolved(QIOPlanningRoute route) {
            Set<Long> dependencies = new LinkedHashSet<>();
            for (Map.Entry<PortableResourceDescriptor, Long> input :
                  route.getExactInputs().entrySet()) {
                dependencies.addAll(consume(input.getKey(), input.getValue()));
            }
            if (nextNodeId == Long.MAX_VALUE) {
                throw new ArithmeticException("QIO plan node identity overflow");
            }
            long nodeId = nextNodeId++;
            QIOPlanStep step = new QIOPlanStep(nodeId, route.getProviderKind(),
                  route.getProviderId(), route.getRouteId(), route.getRecipeKey(),
                  route.getVariantId(), route.getSignature(), 1, route.getExactInputs(),
                  route.getGuaranteedOutputs(), route.getOptionalOutputs(),
                  new ArrayList<>(dependencies), route.getCandidateInputs(),
                  route.getConfigurationInputs());
            steps.add(step);
            for (Map.Entry<PortableResourceDescriptor, Long> output :
                  route.getGuaranteedOutputs().entrySet()) {
                addGenerated(output.getKey(), output.getValue(), nodeId);
            }
        }

        private void executeCompressedCycle(CycleSolution solution,
              PortableResourceDescriptor demand, long minimumAvailable, int depth) {
            Map<PortableResourceDescriptor, Long> simulated = new TreeMap<>();
            for (PortableResourceDescriptor resource : solution.internalResources) {
                simulated.put(resource, available(resource));
            }
            Map<PortableResourceDescriptor, Long> cycleSeeds = new TreeMap<>();
            long[] remaining = solution.operations.clone();
            long unfinished = 0;
            for (long operations : remaining) unfinished = Math.addExact(unfinished, operations);
            List<Integer> schedule = new ArrayList<>();
            while (unfinished > 0) {
                checkCancelled();
                int ready = firstReadyInternal(solution, remaining, simulated);
                if (ready < 0) {
                    int blocked = -1;
                    for (int index = 0; index < remaining.length; index++) {
                        if (remaining[index] > 0) { blocked = index; break; }
                    }
                    if (blocked < 0) throw new PlanningFailure(Status.UNRESOLVABLE_CYCLE,
                          "QIO cycle has no schedulable member");
                    QIOPlanningRoute route = solution.routes.get(blocked);
                    for (Map.Entry<PortableResourceDescriptor, Long> input : route.getExactInputs().entrySet()) {
                        if (!solution.internalResources.contains(input.getKey())) continue;
                        long present = simulated.getOrDefault(input.getKey(), 0L);
                        long missing = input.getValue() - present;
                        if (missing > 0) {
                            simulated.put(input.getKey(), Math.addExact(present, missing));
                        }
                    }
                    ready = firstReadyInternal(solution, remaining, simulated);
                    if (ready < 0) throw new PlanningFailure(Status.UNRESOLVABLE_CYCLE,
                          "QIO cycle could not establish a seed schedule");
                }
                QIOPlanningRoute route = solution.routes.get(ready);
                for (Map.Entry<PortableResourceDescriptor, Long> input : route.getExactInputs().entrySet()) {
                    if (solution.internalResources.contains(input.getKey())) {
                        simulated.put(input.getKey(), Math.subtractExact(
                              simulated.getOrDefault(input.getKey(), 0L), input.getValue()));
                    }
                }
                for (Map.Entry<PortableResourceDescriptor, Long> output : route.getGuaranteedOutputs().entrySet()) {
                    if (solution.internalResources.contains(output.getKey())) {
                        simulated.merge(output.getKey(), output.getValue(), Math::addExact);
                    }
                }
                schedule.add(ready);
                remaining[ready]--;
                unfinished--;
            }

            Map<PortableResourceDescriptor, Long> roundBalance = new TreeMap<>();
            for (int routeIndex : schedule) {
                QIOPlanningRoute route = solution.routes.get(routeIndex);
                for (Map.Entry<PortableResourceDescriptor, Long> input :
                      route.getExactInputs().entrySet()) {
                    if (!solution.internalResources.contains(input.getKey())) continue;
                    long present = roundBalance.getOrDefault(input.getKey(), 0L);
                    long missing = Math.max(0, input.getValue() - present);
                    if (missing > 0) {
                        cycleSeeds.merge(input.getKey(), missing, Math::addExact);
                        present = Math.addExact(present, missing);
                    }
                    roundBalance.put(input.getKey(), Math.subtractExact(present,
                          input.getValue()));
                }
                for (Map.Entry<PortableResourceDescriptor, Long> output :
                      route.getGuaranteedOutputs().entrySet()) {
                    if (solution.internalResources.contains(output.getKey())) {
                        roundBalance.merge(output.getKey(), output.getValue(), Math::addExact);
                    }
                }
            }

            Set<Long> dependencies = new LinkedHashSet<>();
            for (Map.Entry<PortableResourceDescriptor, Long> seed : cycleSeeds.entrySet()) {
                long missing = Math.max(0, seed.getValue() - available(seed.getKey()));
                if (missing > 0) {
                    addExternalStock(seed.getKey(), missing);
                    seedRequirements.merge(seed.getKey(), missing, Math::addExact);
                }
                dependencies.addAll(consume(seed.getKey(), seed.getValue()));
                // A seed is transient cycle inventory and is available again after the cycle.
                addExternalStock(seed.getKey(), seed.getValue());
            }

            long netDemand = net(solution.routes, solution.operations, demand).longValueExact();
            if (netDemand <= 0) throw new PlanningFailure(Status.UNRESOLVABLE_CYCLE,
                  "QIO cycle does not produce its demanded resource");
            long required = Math.addExact(minimumAvailable,
                  seedRequirements.getOrDefault(demand, 0L));
            long deficit = Math.max(0, required - available(demand));
            long rounds = Math.max(1, Math.floorDiv(Math.addExact(deficit, netDemand - 1), netDemand));

            Map<PortableResourceDescriptor, Long> externalInputs = new TreeMap<>();
            for (int index = 0; index < solution.routes.size(); index++) {
                long operations = Math.multiplyExact(solution.operations[index], rounds);
                for (Map.Entry<PortableResourceDescriptor, Long> input : solution.routes.get(index).getExactInputs().entrySet()) {
                    if (!solution.internalResources.contains(input.getKey())) {
                        externalInputs.merge(input.getKey(), Math.multiplyExact(input.getValue(), operations), Math::addExact);
                    }
                }
            }
            for (Map.Entry<PortableResourceDescriptor, Long> input : externalInputs.entrySet()) {
                ensure(input.getKey(), input.getValue(), depth, false);
            }
            for (Map.Entry<PortableResourceDescriptor, Long> input : externalInputs.entrySet()) {
                dependencies.addAll(consume(input.getKey(), input.getValue()));
            }

            if (steps.size() + solution.routes.size() + cycleNodes.size() + 1 > snapshot.getMaximumNodes()) {
                throw new PlanningFailure(Status.TOO_COMPLEX, "QIO condensed cycle exceeds the node budget");
            }
            if (nextNodeId == Long.MAX_VALUE) throw new ArithmeticException("QIO cycle node identity overflow");
            long cycleNodeId = nextNodeId++;
            List<Long> memberIds = new ArrayList<>(solution.routes.size());
            Map<Long, Long> quotas = new LinkedHashMap<>();
            for (int index = 0; index < solution.routes.size(); index++) {
                if (nextNodeId == Long.MAX_VALUE) throw new ArithmeticException("QIO cycle member identity overflow");
                long memberId = nextNodeId++;
                long totalOperations = Math.multiplyExact(solution.operations[index], rounds);
                plannedOperations = Math.addExact(plannedOperations, totalOperations);
                if (plannedOperations > snapshot.getMaximumOperations()) {
                    throw new PlanningFailure(Status.TOO_COMPLEX,
                          "QIO cycle operation budget exceeded " + snapshot.getMaximumOperations());
                }
                QIOPlanningRoute route = solution.routes.get(index);
                steps.add(new QIOPlanStep(memberId, route.getProviderKind(), route.getProviderId(),
                      route.getRouteId(), route.getRecipeKey(), route.getVariantId(),
                      route.getSignature(), totalOperations, route.getExactInputs(),
                      route.getGuaranteedOutputs(), route.getOptionalOutputs(),
                      new ArrayList<>(dependencies), route.getCandidateInputs(),
                      route.getConfigurationInputs()));
                memberIds.add(memberId);
                quotas.put(memberId, solution.operations[index]);
            }
            List<Long> memberSchedule = new ArrayList<>(schedule.size());
            for (int routeIndex : schedule) memberSchedule.add(memberIds.get(routeIndex));
            long outputSource = memberSchedule.get(memberSchedule.size() - 1);
            Map<PortableResourceDescriptor, Long> netPerRound = new TreeMap<>();
            Set<PortableResourceDescriptor> all = new java.util.TreeSet<>();
            for (QIOPlanningRoute route : solution.routes) {
                all.addAll(route.getExactInputs().keySet());
                all.addAll(route.getGuaranteedOutputs().keySet());
            }
            for (PortableResourceDescriptor resource : all) {
                long delta = net(solution.routes, solution.operations, resource).longValueExact();
                if (delta > 0) netPerRound.put(resource, delta);
            }
            for (PortableResourceDescriptor resource : solution.internalResources) {
                long delta = net(solution.routes, solution.operations, resource).longValueExact();
                if (delta > 0) addGenerated(resource, Math.multiplyExact(delta, rounds), outputSource);
            }
            for (int index = 0; index < solution.routes.size(); index++) {
                long operations = Math.multiplyExact(solution.operations[index], rounds);
                for (Map.Entry<PortableResourceDescriptor, Long> output : solution.routes.get(index).getGuaranteedOutputs().entrySet()) {
                    if (!solution.internalResources.contains(output.getKey())) {
                        addGenerated(output.getKey(), Math.multiplyExact(output.getValue(), operations), outputSource);
                    }
                }
            }
            cycleNodes.add(new QIOCyclePlanNode(cycleNodeId, rounds, quotas, memberSchedule,
                  cycleSeeds, netPerRound));
        }

        private int firstReadyInternal(CycleSolution solution, long[] remaining,
              Map<PortableResourceDescriptor, Long> simulated) {
            for (int index = 0; index < solution.routes.size(); index++) {
                if (remaining[index] <= 0) continue;
                boolean ready = true;
                for (Map.Entry<PortableResourceDescriptor, Long> input : solution.routes.get(index).getExactInputs().entrySet()) {
                    if (solution.internalResources.contains(input.getKey()) &&
                          simulated.getOrDefault(input.getKey(), 0L) < input.getValue()) {
                        ready = false; break;
                    }
                }
                if (ready) return index;
            }
            return -1;
        }

        private void executeCycleRound(CycleSolution solution, int depth) {
            for (int routeIndex = 0; routeIndex < solution.routes.size(); routeIndex++) {
                QIOPlanningRoute route = solution.routes.get(routeIndex);
                long operations = solution.operations[routeIndex];
                for (Map.Entry<PortableResourceDescriptor, Long> input :
                      route.getExactInputs().entrySet()) {
                    if (!solution.internalResources.contains(input.getKey())) {
                        ensure(input.getKey(), Math.multiplyExact(input.getValue(), operations),
                              depth, false);
                    }
                }
            }
            long[] remaining = solution.operations.clone();
            long unfinished = 0;
            for (long operations : remaining) {
                unfinished = Math.addExact(unfinished, operations);
            }
            while (unfinished > 0) {
                checkCancelled();
                int ready = firstReadyRoute(solution.routes, remaining);
                if (ready < 0) {
                    seedFirstBlockedRoute(solution, remaining);
                    ready = firstReadyRoute(solution.routes, remaining);
                    if (ready < 0) {
                        throw new PlanningFailure(Status.UNRESOLVABLE_CYCLE,
                              "QIO cycle could not establish an executable seed schedule");
                    }
                }
                addOperation();
                executeResolved(solution.routes.get(ready));
                remaining[ready]--;
                unfinished--;
            }
        }

        private int firstReadyRoute(List<QIOPlanningRoute> routes, long[] remaining) {
            for (int index = 0; index < routes.size(); index++) {
                if (remaining[index] > 0 && hasInputs(routes.get(index))) {
                    return index;
                }
            }
            return -1;
        }

        private boolean hasInputs(QIOPlanningRoute route) {
            for (Map.Entry<PortableResourceDescriptor, Long> input :
                  route.getExactInputs().entrySet()) {
                if (available(input.getKey()) < input.getValue()) {
                    return false;
                }
            }
            return true;
        }

        private void seedFirstBlockedRoute(CycleSolution solution, long[] remaining) {
            for (int routeIndex = 0; routeIndex < solution.routes.size(); routeIndex++) {
                if (remaining[routeIndex] <= 0) {
                    continue;
                }
                QIOPlanningRoute route = solution.routes.get(routeIndex);
                for (Map.Entry<PortableResourceDescriptor, Long> input :
                      route.getExactInputs().entrySet()) {
                    if (!solution.internalResources.contains(input.getKey())) {
                        continue;
                    }
                    long missing = input.getValue() - available(input.getKey());
                    if (missing > 0) {
                        addExternalStock(input.getKey(), missing);
                        seedRequirements.merge(input.getKey(), missing, Math::addExact);
                    }
                }
                return;
            }
        }

        private CycleAnalysis findCycleForDemand(PortableResourceDescriptor demand) {
            Map<PortableResourceDescriptor, QIOPlanningRoute> selected = new TreeMap<>();
            collectSelectedRoutes(demand, selected, new HashSet<>(), 0);
            if (selected.isEmpty()) {
                return CycleAnalysis.none();
            }
            Map<PortableResourceDescriptor, List<PortableResourceDescriptor>> graph =
                  new TreeMap<>();
            for (Map.Entry<PortableResourceDescriptor, QIOPlanningRoute> entry :
                  selected.entrySet()) {
                List<PortableResourceDescriptor> edges = new ArrayList<>();
                for (PortableResourceDescriptor input :
                      entry.getValue().getExactInputs().keySet()) {
                    if (selected.containsKey(input)) {
                        edges.add(input);
                    }
                }
                Collections.sort(edges);
                graph.put(entry.getKey(), edges);
            }
            List<Set<PortableResourceDescriptor>> components = tarjan(graph);
            components.sort(Comparator.comparing(component -> component.iterator().next()));
            // A cycle containing the current demand is the authoritative recursive branch.
            for (Set<PortableResourceDescriptor> component : components) {
                if (!component.contains(demand) || !isCycle(component, graph)) continue;
                CycleSolution solution = solveCycle(component, selected, demand);
                if (solution != null) {
                    return CycleAnalysis.solved(solution);
                }
                return CycleAnalysis.circular(cyclePath(component, graph, demand));
            }
            // A lower SCC is relevant only when it has positive net output for the current
            // demand (for example a conserved catalyst loop that produces a side output).
            for (Set<PortableResourceDescriptor> component : components) {
                if (!isCycle(component, graph) ||
                    !canProduceDemand(component, selected, demand)) continue;
                CycleSolution solution = solveCycle(component, selected, demand);
                if (solution != null) return CycleAnalysis.solved(solution);
            }
            return CycleAnalysis.none();
        }

        private void collectSelectedRoutes(PortableResourceDescriptor resource,
              Map<PortableResourceDescriptor, QIOPlanningRoute> selected,
              Set<PortableResourceDescriptor> visiting, int depth) {
            checkCancelled();
            if (selected.containsKey(resource) || !visiting.add(resource)) {
                return;
            }
            if (depth > snapshot.getMaximumDepth() ||
                selected.size() >= snapshot.getMaximumNodes()) {
                throw new PlanningFailure(Status.TOO_COMPLEX,
                      "QIO cycle graph exceeded its planning budget");
            }
            List<QIOPlanningRoute> candidates = snapshot.getRoutesProducing(resource);
            if (!candidates.isEmpty()) {
                QIOPlanningRoute route = chooseVariant(candidates);
                selected.put(resource, route);
                for (PortableResourceDescriptor input : route.getExactInputs().keySet()) {
                    collectSelectedRoutes(input, selected, visiting, depth + 1);
                }
            }
            visiting.remove(resource);
        }

        @Nullable
        private CycleSolution solveCycle(Set<PortableResourceDescriptor> resources,
              Map<PortableResourceDescriptor, QIOPlanningRoute> selected,
              PortableResourceDescriptor demand) {
            Map<String, QIOPlanningRoute> uniqueRoutes = new TreeMap<>();
            for (PortableResourceDescriptor resource : resources) {
                QIOPlanningRoute route = selected.get(resource);
                if (route != null) {
                    uniqueRoutes.put(route.getStableId(), route);
                }
            }
            List<QIOPlanningRoute> routes = new ArrayList<>(uniqueRoutes.values());
            if (routes.isEmpty()) {
                return null;
            }
            boolean allAggregateNonPositive = true;
            for (QIOPlanningRoute route : routes) {
                BigInteger aggregate = BigInteger.ZERO;
                for (PortableResourceDescriptor resource : resources) {
                    aggregate = aggregate.add(routeNet(route, resource));
                }
                if (aggregate.signum() > 0) {
                    allAggregateNonPositive = false;
                    break;
                }
            }
            if (resources.contains(demand) && allAggregateNonPositive) return null;
            QIOPlanningRoute demandProducer = null;
            for (QIOPlanningRoute route : routes) {
                if (routeNet(route, demand).signum() > 0 && (demandProducer == null ||
                    route.getStableId().compareTo(demandProducer.getStableId()) < 0)) {
                    demandProducer = route;
                }
            }
            if (demandProducer == null) return null;
            long remainingBudget = snapshot.getMaximumOperations() - plannedOperations;
            long bounded = Math.min(remainingBudget, snapshot.getMaximumNodes());
            if (bounded <= 0) {
                throw new PlanningFailure(Status.TOO_COMPLEX,
                      "QIO cycle cannot fit within the operation budget");
            }
            long[] vector = new long[routes.size()];
            Map<String, Integer> routeIndices = new LinkedHashMap<>();
            for (int index = 0; index < routes.size(); index++) {
                routeIndices.put(routes.get(index).getStableId(), index);
            }
            Set<String> observedNets = new HashSet<>();
            int maximumIterations = (int) Math.min(128L, Math.max(1L,
                  snapshot.getMaximumNodes()));
            addToVector(vector, routeIndices.get(demandProducer.getStableId()), 1, bounded);
            for (int iteration = 0; iteration < maximumIterations; iteration++) {
                checkCancelled();
                if (exploredNodes < snapshot.getMaximumNodes()) exploredNodes++;
                String netState = netState(routes, resources, demand, vector);
                if (!observedNets.add(netState)) {
                    return null;
                }
                PortableResourceDescriptor deficitResource = null;
                BigInteger deficit = BigInteger.ZERO;
                for (PortableResourceDescriptor resource : resources) {
                    BigInteger current = net(routes, vector, resource);
                    if (current.signum() < 0 && (deficitResource == null ||
                        current.compareTo(deficit) < 0 || current.equals(deficit) &&
                              resource.compareTo(deficitResource) < 0)) {
                        deficitResource = resource;
                        deficit = current;
                    }
                }
                if (deficitResource == null) {
                    if (net(routes, vector, demand).signum() > 0) {
                        return usedSolution(resources, routes, vector);
                    }
                    addToVector(vector, routeIndices.get(demandProducer.getStableId()), 1,
                          bounded);
                    continue;
                }
                QIOPlanningRoute producer = selected.get(deficitResource);
                if (producer == null) return null;
                Integer producerIndex = routeIndices.get(producer.getStableId());
                long grossOutput = producer.getGuaranteedOutputs().getOrDefault(
                      deficitResource, 0L);
                if (producerIndex == null || grossOutput <= 0) return null;
                BigInteger amount = deficit.negate().add(BigInteger.valueOf(grossOutput - 1))
                      .divide(BigInteger.valueOf(grossOutput));
                if (amount.signum() <= 0 || amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                    return null;
                }
                addToVector(vector, producerIndex, amount.longValue(), bounded);
            }
            return null;
        }

        private CycleSolution usedSolution(Set<PortableResourceDescriptor> resources,
              List<QIOPlanningRoute> routes, long[] vector) {
            List<QIOPlanningRoute> usedRoutes = new ArrayList<>();
            List<Long> usedCounts = new ArrayList<>();
            for (int index = 0; index < vector.length; index++) {
                if (vector[index] > 0) {
                    usedRoutes.add(routes.get(index));
                    usedCounts.add(vector[index]);
                }
            }
            long[] counts = new long[usedCounts.size()];
            for (int index = 0; index < counts.length; index++) counts[index] = usedCounts.get(index);
            return new CycleSolution(resources, usedRoutes, counts);
        }

        private void addToVector(long[] vector, int index, long amount, long bounded) {
            vector[index] = Math.addExact(vector[index], amount);
            long total = 0;
            for (long coefficient : vector) total = Math.addExact(total, coefficient);
            if (total > bounded) {
                throw new PlanningFailure(Status.TOO_COMPLEX,
                      "QIO cycle cannot fit within the operation budget");
            }
        }

        private static boolean canProduceDemand(Set<PortableResourceDescriptor> resources,
              Map<PortableResourceDescriptor, QIOPlanningRoute> selected,
              PortableResourceDescriptor demand) {
            for (PortableResourceDescriptor resource : resources) {
                QIOPlanningRoute route = selected.get(resource);
                if (route != null && routeNet(route, demand).signum() > 0) return true;
            }
            return false;
        }

        private static BigInteger routeNet(QIOPlanningRoute route,
              PortableResourceDescriptor resource) {
            return BigInteger.valueOf(route.getGuaranteedOutputs().getOrDefault(resource, 0L))
                  .subtract(BigInteger.valueOf(route.getExactInputs().getOrDefault(resource, 0L)));
        }

        private static String netState(List<QIOPlanningRoute> routes,
              Set<PortableResourceDescriptor> resources, PortableResourceDescriptor demand,
              long[] vector) {
            StringBuilder state = new StringBuilder();
            for (PortableResourceDescriptor resource : resources) {
                state.append(resource).append('=').append(net(routes, vector, resource)).append(';');
            }
            if (!resources.contains(demand)) {
                state.append("demand=").append(net(routes, vector, demand));
            }
            return state.toString();
        }

        private static BigInteger net(List<QIOPlanningRoute> routes, long[] vector,
              PortableResourceDescriptor resource) {
            BigInteger result = BigInteger.ZERO;
            for (int index = 0; index < routes.size(); index++) {
                QIOPlanningRoute route = routes.get(index);
                BigInteger delta = BigInteger.valueOf(
                      route.getGuaranteedOutputs().getOrDefault(resource, 0L)).subtract(
                      BigInteger.valueOf(route.getExactInputs().getOrDefault(resource, 0L)));
                result = result.add(delta.multiply(BigInteger.valueOf(vector[index])));
            }
            return result;
        }

        private static boolean isCycle(Set<PortableResourceDescriptor> component,
              Map<PortableResourceDescriptor, List<PortableResourceDescriptor>> graph) {
            if (component.size() > 1) {
                return true;
            }
            PortableResourceDescriptor resource = component.iterator().next();
            return graph.getOrDefault(resource, Collections.emptyList()).contains(resource);
        }

        private static String cyclePath(Set<PortableResourceDescriptor> component,
              Map<PortableResourceDescriptor, List<PortableResourceDescriptor>> graph,
              PortableResourceDescriptor preferredStart) {
            PortableResourceDescriptor current = component.contains(preferredStart) ?
                  preferredStart : component.iterator().next();
            List<PortableResourceDescriptor> walked = new ArrayList<>();
            Map<PortableResourceDescriptor, Integer> positions = new LinkedHashMap<>();
            while (true) {
                Integer repeatedAt = positions.putIfAbsent(current, walked.size());
                if (repeatedAt != null) {
                    StringBuilder path = new StringBuilder();
                    for (int index = repeatedAt; index < walked.size(); index++) {
                        if (path.length() > 0) path.append(" -> ");
                        path.append(displayResource(walked.get(index)));
                    }
                    if (path.length() > 0) path.append(" -> ");
                    return path.append(displayResource(current)).toString();
                }
                walked.add(current);
                PortableResourceDescriptor next = null;
                for (PortableResourceDescriptor candidate : graph.getOrDefault(current,
                      Collections.emptyList())) {
                    if (component.contains(candidate) && (next == null ||
                        candidate.compareTo(next) < 0)) next = candidate;
                }
                if (next == null) {
                    return displayResource(current) + " -> " + displayResource(current);
                }
                current = next;
            }
        }

        private static String displayResource(PortableResourceDescriptor resource) {
            StringBuilder display = new StringBuilder();
            if (resource.getKind() != PortableResourceDescriptor.Kind.ITEM) {
                display.append(resource.getKind().name().toLowerCase(java.util.Locale.ROOT))
                      .append(':');
            }
            display.append(resource.getRegistryName());
            if (resource.getMetadata() != 0) display.append('@').append(resource.getMetadata());
            return display.toString();
        }

        private static List<Set<PortableResourceDescriptor>> tarjan(
              Map<PortableResourceDescriptor, List<PortableResourceDescriptor>> graph) {
            TarjanState state = new TarjanState(graph);
            for (PortableResourceDescriptor resource : graph.keySet()) {
                if (!state.indices.containsKey(resource)) {
                    state.visit(resource);
                }
            }
            return state.components;
        }

        private Set<Long> consume(PortableResourceDescriptor resource, long amount) {
            long remaining = amount;
            Set<Long> dependencies = new LinkedHashSet<>();
            Deque<GeneratedLot> lots = generated.get(resource);
            while (remaining > 0 && lots != null && !lots.isEmpty()) {
                GeneratedLot lot = lots.peekFirst();
                long consumed = Math.min(remaining, lot.amount);
                remaining -= consumed;
                lot.amount -= consumed;
                dependencies.add(lot.sourceNodeId);
                if (lot.amount == 0) {
                    lots.removeFirst();
                }
            }
            if (lots != null && lots.isEmpty()) {
                generated.remove(resource);
            }
            if (remaining > 0) {
                long stored = stock.getOrDefault(resource, 0L);
                if (stored < remaining) {
                    throw new IllegalStateException("QIO cycle input ledger was not funded");
                }
                long left = stored - remaining;
                if (left == 0) {
                    stock.remove(resource);
                } else {
                    stock.put(resource, left);
                }
                externalRequirements.merge(resource, remaining, Math::addExact);
            }
            return Collections.unmodifiableSet(dependencies);
        }

        private void addExternalStock(PortableResourceDescriptor resource, long amount) {
            if (amount <= 0) {
                return;
            }
            stock.put(resource, Math.addExact(stock.getOrDefault(resource, 0L), amount));
        }

        private void addGenerated(PortableResourceDescriptor resource, long amount, long nodeId) {
            if (amount <= 0) {
                throw new IllegalArgumentException("QIO generated amount must be positive");
            }
            generated.computeIfAbsent(resource, ignored -> new ArrayDeque<>())
                  .addLast(new GeneratedLot(nodeId, amount));
        }

        private long available(PortableResourceDescriptor resource) {
            long amount = stock.getOrDefault(resource, 0L);
            Deque<GeneratedLot> lots = generated.get(resource);
            if (lots != null) {
                for (GeneratedLot lot : lots) {
                    amount = Math.addExact(amount, lot.amount);
                }
            }
            return amount;
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
                long present;
                try {
                    present = available(input.getKey());
                } catch (ArithmeticException e) {
                    present = Long.MAX_VALUE;
                }
                if (present < input.getValue()) {
                    deficit = saturatedAdd(deficit, input.getValue() - present);
                }
            }
            return deficit;
        }

        private void addOperation() {
            plannedOperations = Math.addExact(plannedOperations, 1);
            if (plannedOperations > snapshot.getMaximumOperations() ||
                  plannedOperations > snapshot.getMaximumNodes()) {
                throw new PlanningFailure(Status.TOO_COMPLEX,
                      "QIO cycle planning operation budget exceeded " +
                            snapshot.getMaximumOperations());
            }
        }

        private String cyclePath(PortableResourceDescriptor repeated) {
            StringBuilder path = new StringBuilder();
            for (PortableResourceDescriptor active : activeResources) {
                if (path.length() > 0) {
                    path.append(" -> ");
                }
                path.append(active);
            }
            return path.append(" -> ").append(repeated).toString();
        }

        private void checkCancelled() {
            if (cancellationToken.isCancelled() || Thread.currentThread().isInterrupted()) {
                throw new PlanningFailure(Status.CANCELLED, "QIO planning was cancelled");
            }
        }

        private static long saturatedAdd(long left, long right) {
            if (right > Long.MAX_VALUE - left) {
                return Long.MAX_VALUE;
            }
            return left + right;
        }
    }

    private static final class GeneratedLot {

        private final long sourceNodeId;
        private long amount;

        private GeneratedLot(long sourceNodeId, long amount) {
            this.sourceNodeId = sourceNodeId;
            this.amount = amount;
        }
    }

    private static final class CycleSolution {

        private final Set<PortableResourceDescriptor> internalResources;
        private final List<QIOPlanningRoute> routes;
        private final long[] operations;

        private CycleSolution(Set<PortableResourceDescriptor> internalResources,
              List<QIOPlanningRoute> routes, long[] operations) {
            this.internalResources = Collections.unmodifiableSet(
                  new LinkedHashSet<>(internalResources));
            this.routes = Collections.unmodifiableList(new ArrayList<>(routes));
            this.operations = operations.clone();
        }
    }

    private static final class CycleAnalysis {

        @Nullable private final CycleSolution solution;
        @Nullable private final String circularPath;

        private CycleAnalysis(@Nullable CycleSolution solution, @Nullable String circularPath) {
            this.solution = solution;
            this.circularPath = circularPath;
        }

        private static CycleAnalysis none() {
            return new CycleAnalysis(null, null);
        }

        private static CycleAnalysis solved(CycleSolution solution) {
            return new CycleAnalysis(Objects.requireNonNull(solution, "solution"), null);
        }

        private static CycleAnalysis circular(String path) {
            return new CycleAnalysis(null, Objects.requireNonNull(path, "path"));
        }
    }

    private static final class TarjanState {

        private final Map<PortableResourceDescriptor, List<PortableResourceDescriptor>> graph;
        private final Map<PortableResourceDescriptor, Integer> indices = new HashMap<>();
        private final Map<PortableResourceDescriptor, Integer> lowLinks = new HashMap<>();
        private final Deque<PortableResourceDescriptor> stack = new ArrayDeque<>();
        private final Set<PortableResourceDescriptor> onStack = new HashSet<>();
        private final List<Set<PortableResourceDescriptor>> components = new ArrayList<>();
        private int nextIndex;

        private TarjanState(
              Map<PortableResourceDescriptor, List<PortableResourceDescriptor>> graph) {
            this.graph = graph;
        }

        private void visit(PortableResourceDescriptor resource) {
            indices.put(resource, nextIndex);
            lowLinks.put(resource, nextIndex);
            nextIndex++;
            stack.push(resource);
            onStack.add(resource);
            for (PortableResourceDescriptor neighbor :
                  graph.getOrDefault(resource, Collections.emptyList())) {
                if (!indices.containsKey(neighbor)) {
                    visit(neighbor);
                    lowLinks.put(resource, Math.min(lowLinks.get(resource),
                          lowLinks.get(neighbor)));
                } else if (onStack.contains(neighbor)) {
                    lowLinks.put(resource, Math.min(lowLinks.get(resource),
                          indices.get(neighbor)));
                }
            }
            if (!lowLinks.get(resource).equals(indices.get(resource))) {
                return;
            }
            Set<PortableResourceDescriptor> component = new java.util.TreeSet<>();
            PortableResourceDescriptor member;
            do {
                member = stack.pop();
                onStack.remove(member);
                component.add(member);
            } while (!member.equals(resource));
            components.add(Collections.unmodifiableSet(component));
        }
    }

    private static final class PlanningFailure extends RuntimeException {

        private final Status status;

        private PlanningFailure(Status status, String message) {
            super(message, null, false, false);
            this.status = status;
        }
    }
}
