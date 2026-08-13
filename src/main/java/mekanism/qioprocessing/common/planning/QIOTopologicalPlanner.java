package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.plan.QIOCraftPlan;
import mekanism.qioprocessing.common.content.plan.QIOPlanSourceRevisions;
import mekanism.qioprocessing.common.content.plan.QIOPlanStep;
import mekanism.qioprocessing.common.planning.QIOPlanningExecutor.CancellationToken;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;

/**
 * Optional acyclic fast path that aggregates shared demands in topological order.
 * Structure is cached independently from per-request storage and claim state.
 */
final class QIOTopologicalPlanner {

    static final QIOTopologicalPlanner INSTANCE = new QIOTopologicalPlanner();

    private static final int MAX_CACHE_ENTRIES = 128;
    private static final int MAX_CACHE_WEIGHT = 131_072;
    private static final long ROOT_CONSUMER = -1;

    private final Map<TopologyKey, CompiledTopology> cache =
          new LinkedHashMap<>(32, 0.75F, true);
    private int cacheWeight;

    private QIOTopologicalPlanner() {
    }

    /** Returns null when the exact request should use the compatibility planner. */
    @Nullable
    QIOPlanningResult tryPlan(@Nonnull QIOPlanningRequest request,
          @Nonnull CancellationToken cancellationToken) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        try {
            CompiledTopology topology = topology(request.getSnapshot(),
                  request.getRootResource(), cancellationToken);
            if (topology == null) return null;
            PlanningState state = new PlanningState(request, cancellationToken);
            return state.plan(topology);
        } catch (FastPathDeclined | ArithmeticException ignored) {
            return null;
        }
    }

    synchronized void clearCache() {
        cache.clear();
        cacheWeight = 0;
    }

    synchronized int cachedTopologyCount() {
        return cache.size();
    }

    @Nullable
    private CompiledTopology topology(QIOPlanningSnapshot snapshot,
          PortableResourceDescriptor root, CancellationToken cancellationToken) {
        TopologyKey key = TopologyKey.create(snapshot, root);
        synchronized (this) {
            CompiledTopology cached = cache.get(key);
            if (cached != null) {
                if (cached.matches(snapshot)) return cached;
                cache.remove(key);
                cacheWeight -= cached.weight;
            }
        }
        CompiledTopology compiled = CompiledTopology.compile(snapshot, root,
              cancellationToken);
        if (compiled == null || compiled.weight > MAX_CACHE_WEIGHT) return compiled;
        synchronized (this) {
            CompiledTopology previous = cache.put(key, compiled);
            if (previous != null) cacheWeight -= previous.weight;
            cacheWeight += compiled.weight;
            trimCache();
        }
        return compiled;
    }

    private void trimCache() {
        while (cache.size() > MAX_CACHE_ENTRIES || cacheWeight > MAX_CACHE_WEIGHT) {
            Map.Entry<TopologyKey, CompiledTopology> eldest =
                  cache.entrySet().iterator().next();
            cacheWeight -= eldest.getValue().weight;
            cache.remove(eldest.getKey());
        }
    }

    private static List<QIOPlanningRoute> preferredRoutes(QIOPlanningSnapshot snapshot,
          PortableResourceDescriptor resource) {
        List<QIOPlanningRoute> candidates = snapshot.getRoutesProducing(resource);
        if (candidates.isEmpty()) return Collections.emptyList();
        QIOPlanningRoute first = candidates.get(0);
        List<QIOPlanningRoute> preferred = new ArrayList<>();
        preferred.add(first);
        for (int index = 1; index < candidates.size(); index++) {
            QIOPlanningRoute candidate = candidates.get(index);
            if (candidate.getRoutePriority() != first.getRoutePriority() ||
                  !candidate.getLogicalId().equals(first.getLogicalId())) {
                break;
            }
            preferred.add(candidate);
        }
        return Collections.unmodifiableList(preferred);
    }

    private static boolean sameRoute(QIOPlanningRoute left, QIOPlanningRoute right) {
        return left.getStableId().equals(right.getStableId()) &&
              left.getSignature().equals(right.getSignature()) &&
              left.getRoutePriority() == right.getRoutePriority() &&
              left.getVariantPriority() == right.getVariantPriority();
    }

    private static boolean sameRouteFamily(List<QIOPlanningRoute> left,
          List<QIOPlanningRoute> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            if (!sameRoute(left.get(index), right.get(index))) return false;
        }
        return true;
    }

    private static void checkpoint(CancellationToken cancellationToken) {
        if (cancellationToken.isCancelled() || Thread.currentThread().isInterrupted()) {
            throw FastPathDeclined.INSTANCE;
        }
    }

    private static final class PlanningState {

        private final QIOPlanningRequest request;
        private final QIOPlanningSnapshot snapshot;
        private final CancellationToken cancellationToken;
        private final Map<PortableResourceDescriptor, Long> stock = new TreeMap<>();
        private final Map<PortableResourceDescriptor, Deque<DemandLot>> demands =
              new TreeMap<>();
        private final Map<PortableResourceDescriptor, Deque<GeneratedLot>> generated =
              new TreeMap<>();
        private final Map<PortableResourceDescriptor, Long> externalRequirements =
              new TreeMap<>();
        private final Map<String, RouteExecution> executions = new LinkedHashMap<>();
        private final Map<Long, RouteExecution> executionsByNode = new HashMap<>();
        private long nextNodeId;
        private long plannedOperations;
        private int exploredNodes;

        private PlanningState(QIOPlanningRequest request,
              CancellationToken cancellationToken) {
            this.request = request;
            snapshot = request.getSnapshot();
            this.cancellationToken = cancellationToken;
            stock.putAll(snapshot.getAvailableResources());
        }

        private QIOPlanningResult plan(CompiledTopology topology) {
            addDemand(request.getRootResource(), request.getRootAmount(), ROOT_CONSUMER, 0);
            for (TopologyNode node : topology.order) {
                checkpoint(cancellationToken);
                process(node, node.resource.equals(request.getRootResource()));
            }
            for (Deque<DemandLot> pending : demands.values()) {
                if (!pending.isEmpty()) throw FastPathDeclined.INSTANCE;
            }
            List<RouteExecution> orderedExecutions = new ArrayList<>(executions.values());
            orderedExecutions.sort(Comparator.comparingLong(
                  (RouteExecution execution) -> execution.nodeId).reversed());
            List<QIOPlanStep> steps = new ArrayList<>(orderedExecutions.size());
            for (RouteExecution execution : orderedExecutions) {
                steps.add(execution.toStep());
            }
            QIOCraftPlan plan = new QIOCraftPlan(request.getPlanId(),
                  request.getPlanRevision(), snapshot.getSourceRevisions(),
                  request.getRootResource(), request.getRootAmount(),
                  externalRequirements, steps);
            return QIOPlanningResult.success(plan, exploredNodes, plannedOperations);
        }

        private void process(TopologyNode node, boolean root) {
            Deque<DemandLot> pending = demands.get(node.resource);
            if (pending == null || pending.isEmpty()) return;
            consumeGenerated(node.resource, pending);
            if (!root && !pending.isEmpty()) consumeStock(node.resource, pending);
            if (pending.isEmpty()) {
                demands.remove(node.resource);
                return;
            }
            if (node.routes.isEmpty()) {
                if (root) throw FastPathDeclined.INSTANCE;
                consumeExternal(node.resource, pending);
                demands.remove(node.resource);
                return;
            }

            QIOPlanningRoute route = chooseVariant(node.routes);
            long required = totalDemand(pending);
            long outputPerOperation = route.getGuaranteedOutputAmount(node.resource);
            if (required <= 0 || outputPerOperation <= 0) throw FastPathDeclined.INSTANCE;
            int requiredDepth = maximumDepth(pending);
            if (requiredDepth > snapshot.getMaximumDepth()) throw FastPathDeclined.INSTANCE;

            RouteExecution execution = execution(route);
            long operations = ceilDivide(required, outputPerOperation);
            addOperations(operations);
            execution.operations = Math.addExact(execution.operations, operations);

            for (Map.Entry<PortableResourceDescriptor, Long> output :
                  route.getGuaranteedOutputs().entrySet()) {
                addGenerated(output.getKey(), Math.multiplyExact(output.getValue(), operations),
                      execution.nodeId);
            }
            for (Map.Entry<PortableResourceDescriptor, Long> input :
                  route.getExactInputs().entrySet()) {
                addDemand(input.getKey(), Math.multiplyExact(input.getValue(), operations),
                      execution.nodeId, Math.addExact(requiredDepth, 1));
            }
            consumeGenerated(node.resource, pending);
            if (!pending.isEmpty()) throw FastPathDeclined.INSTANCE;
            demands.remove(node.resource);
        }

        private QIOPlanningRoute chooseVariant(List<QIOPlanningRoute> candidates) {
            QIOPlanningRoute selected = candidates.get(0);
            long selectedDeficit = routeDeficit(selected);
            for (int index = 1; index < candidates.size(); index++) {
                QIOPlanningRoute candidate = candidates.get(index);
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
                Deque<GeneratedLot> lots = generated.get(input.getKey());
                if (lots != null) {
                    for (GeneratedLot lot : lots) {
                        available = saturatedAdd(available, lot.amount);
                    }
                }
                if (available < input.getValue()) {
                    deficit = saturatedAdd(deficit, input.getValue() - available);
                }
            }
            return deficit;
        }

        private RouteExecution execution(QIOPlanningRoute route) {
            RouteExecution existing = executions.get(route.getStableId());
            if (existing != null) return existing;
            if (nextNodeId == Long.MAX_VALUE) throw new ArithmeticException(
                  "QIO plan node identity overflow");
            if (++exploredNodes > snapshot.getMaximumNodes()) {
                throw FastPathDeclined.INSTANCE;
            }
            RouteExecution created = new RouteExecution(nextNodeId++, route);
            executions.put(route.getStableId(), created);
            executionsByNode.put(created.nodeId, created);
            return created;
        }

        private void addOperations(long operations) {
            plannedOperations = Math.addExact(plannedOperations, operations);
            if (plannedOperations > snapshot.getMaximumOperations()) {
                throw FastPathDeclined.INSTANCE;
            }
        }

        private void addDemand(PortableResourceDescriptor resource, long amount,
              long consumerNodeId, int depth) {
            if (amount <= 0 || depth < 0) throw FastPathDeclined.INSTANCE;
            Deque<DemandLot> queue = demands.computeIfAbsent(resource,
                  ignored -> new ArrayDeque<>());
            DemandLot tail = queue.peekLast();
            if (tail != null && tail.consumerNodeId == consumerNodeId && tail.depth == depth) {
                tail.amount = Math.addExact(tail.amount, amount);
            } else {
                queue.addLast(new DemandLot(consumerNodeId, amount, depth));
            }
        }

        private void addGenerated(PortableResourceDescriptor resource, long amount,
              long sourceNodeId) {
            if (amount <= 0) throw FastPathDeclined.INSTANCE;
            Deque<GeneratedLot> queue = generated.computeIfAbsent(resource,
                  ignored -> new ArrayDeque<>());
            GeneratedLot tail = queue.peekLast();
            if (tail != null && tail.sourceNodeId == sourceNodeId) {
                tail.amount = Math.addExact(tail.amount, amount);
            } else {
                queue.addLast(new GeneratedLot(sourceNodeId, amount));
            }
        }

        private void consumeGenerated(PortableResourceDescriptor resource,
              Deque<DemandLot> pending) {
            Deque<GeneratedLot> supply = generated.get(resource);
            while (!pending.isEmpty() && supply != null && !supply.isEmpty()) {
                DemandLot demand = pending.peekFirst();
                GeneratedLot lot = supply.peekFirst();
                long consumed = Math.min(demand.amount, lot.amount);
                demand.amount -= consumed;
                lot.amount -= consumed;
                if (demand.consumerNodeId != ROOT_CONSUMER &&
                      demand.consumerNodeId != lot.sourceNodeId) {
                    RouteExecution consumer = executionsByNode.get(demand.consumerNodeId);
                    if (consumer == null) throw FastPathDeclined.INSTANCE;
                    consumer.dependencies.add(lot.sourceNodeId);
                }
                if (demand.amount == 0) pending.removeFirst();
                if (lot.amount == 0) supply.removeFirst();
            }
            if (supply != null && supply.isEmpty()) generated.remove(resource);
        }

        private void consumeStock(PortableResourceDescriptor resource,
              Deque<DemandLot> pending) {
            long stored = stock.getOrDefault(resource, 0L);
            while (stored > 0 && !pending.isEmpty()) {
                DemandLot demand = pending.peekFirst();
                long consumed = Math.min(stored, demand.amount);
                stored -= consumed;
                demand.amount -= consumed;
                externalRequirements.merge(resource, consumed, Math::addExact);
                if (demand.amount == 0) pending.removeFirst();
            }
            if (stored == 0) stock.remove(resource);
            else stock.put(resource, stored);
        }

        private void consumeExternal(PortableResourceDescriptor resource,
              Deque<DemandLot> pending) {
            long amount = totalDemand(pending);
            externalRequirements.merge(resource, amount, Math::addExact);
            pending.clear();
        }

        private static long totalDemand(Deque<DemandLot> pending) {
            long total = 0;
            for (DemandLot demand : pending) total = Math.addExact(total, demand.amount);
            return total;
        }

        private static int maximumDepth(Deque<DemandLot> pending) {
            int depth = 0;
            for (DemandLot demand : pending) depth = Math.max(depth, demand.depth);
            return depth;
        }

        private static long saturatedAdd(long left, long right) {
            return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
        }
    }

    private static final class CompiledTopology {

        private static final Comparator<MutableNode> READY_ORDER = Comparator
              .comparingInt((MutableNode node) -> node.routes.isEmpty() ? 0 :
                    node.routes.get(0).getGuaranteedOutputs().size()).reversed()
              .thenComparing(node -> node.resource);

        private final List<TopologyNode> order;
        private final int weight;

        private CompiledTopology(List<TopologyNode> order, int weight) {
            this.order = Collections.unmodifiableList(order);
            this.weight = weight;
        }

        @Nullable
        private static CompiledTopology compile(QIOPlanningSnapshot snapshot,
              PortableResourceDescriptor root, CancellationToken cancellationToken) {
            Map<PortableResourceDescriptor, MutableNode> nodes = new TreeMap<>();
            Deque<PortableResourceDescriptor> pending = new ArrayDeque<>();
            Set<PortableResourceDescriptor> expanded = new HashSet<>();
            nodes.put(root, new MutableNode(root));
            pending.add(root);
            int edges = 0;
            while (!pending.isEmpty()) {
                checkpoint(cancellationToken);
                PortableResourceDescriptor resource = pending.removeFirst();
                if (!expanded.add(resource)) continue;
                MutableNode node = nodes.get(resource);
                node.routes = preferredRoutes(snapshot, resource);
                if (node.routes.isEmpty()) continue;
                for (QIOPlanningRoute route : node.routes) {
                    if (route.getGuaranteedOutputAmount(resource) <= 0) return null;
                    for (PortableResourceDescriptor input : route.getExactInputs().keySet()) {
                        MutableNode child = nodes.computeIfAbsent(input, MutableNode::new);
                        if (node.children.add(child)) {
                            child.inDegree++;
                            edges++;
                        }
                        pending.addLast(input);
                    }
                }
            }
            if (nodes.get(root).routes.isEmpty()) return null;

            PriorityQueue<MutableNode> ready = new PriorityQueue<>(READY_ORDER);
            for (MutableNode node : nodes.values()) {
                if (node.inDegree == 0) ready.add(node);
            }
            List<TopologyNode> order = new ArrayList<>(nodes.size());
            while (!ready.isEmpty()) {
                checkpoint(cancellationToken);
                MutableNode node = ready.remove();
                order.add(new TopologyNode(node.resource, node.routes));
                for (MutableNode child : node.children) {
                    if (--child.inDegree == 0) ready.add(child);
                }
            }
            if (order.size() != nodes.size()) return null;
            return new CompiledTopology(order, Math.addExact(nodes.size(), edges));
        }

        private boolean matches(QIOPlanningSnapshot snapshot) {
            for (TopologyNode node : order) {
                if (!sameRouteFamily(node.routes,
                      preferredRoutes(snapshot, node.resource))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class MutableNode {

        private final PortableResourceDescriptor resource;
        private final Set<MutableNode> children = new LinkedHashSet<>();
        private List<QIOPlanningRoute> routes = Collections.emptyList();
        private int inDegree;

        private MutableNode(PortableResourceDescriptor resource) {
            this.resource = resource;
        }
    }

    private static final class TopologyNode {

        private final PortableResourceDescriptor resource;
        private final List<QIOPlanningRoute> routes;

        private TopologyNode(PortableResourceDescriptor resource,
              List<QIOPlanningRoute> routes) {
            this.resource = resource;
            this.routes = routes;
        }
    }

    private static final class RouteExecution {

        private final long nodeId;
        private final QIOPlanningRoute route;
        private final Set<Long> dependencies = new LinkedHashSet<>();
        private long operations;

        private RouteExecution(long nodeId, QIOPlanningRoute route) {
            this.nodeId = nodeId;
            this.route = route;
        }

        private QIOPlanStep toStep() {
            List<Long> orderedDependencies = new ArrayList<>(dependencies);
            Collections.sort(orderedDependencies);
            return new QIOPlanStep(nodeId, route.getProviderKind(), route.getProviderId(),
                  route.getRouteId(), route.getRecipeKey(), route.getVariantId(),
                  route.getSignature(), operations, route.getExactInputs(),
                  route.getGuaranteedOutputs(), route.getOptionalOutputs(),
                  orderedDependencies, route.getCandidateInputs(),
                  route.getConfigurationInputs());
        }
    }

    private static final class DemandLot {

        private final long consumerNodeId;
        private final int depth;
        private long amount;

        private DemandLot(long consumerNodeId, long amount, int depth) {
            this.consumerNodeId = consumerNodeId;
            this.amount = amount;
            this.depth = depth;
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

    private static final class TopologyKey {

        private final PortableResourceDescriptor root;
        private final long recipeRevision;
        private final long providerRevision;
        private final long policyRevision;
        private final int routeCount;
        private final long routeHashA;
        private final long routeHashB;

        private TopologyKey(PortableResourceDescriptor root, long recipeRevision,
              long providerRevision, long policyRevision, int routeCount,
              long routeHashA, long routeHashB) {
            this.root = root;
            this.recipeRevision = recipeRevision;
            this.providerRevision = providerRevision;
            this.policyRevision = policyRevision;
            this.routeCount = routeCount;
            this.routeHashA = routeHashA;
            this.routeHashB = routeHashB;
        }

        private static TopologyKey create(QIOPlanningSnapshot snapshot,
              PortableResourceDescriptor root) {
            long hashA = 0xCBF29CE484222325L;
            long hashB = 0x9E3779B97F4A7C15L;
            for (QIOPlanningRoute route : snapshot.getRoutes()) {
                hashA = mix(hashA, route.getStableId().hashCode());
                hashA = mix(hashA, route.getSignature().hashCode());
                hashA = mix(hashA, route.getRoutePriority());
                hashA = mix(hashA, route.getVariantPriority());
                hashB = mix(hashB, route.getSignature().hashCode());
                hashB = mix(hashB, route.getStableId().hashCode());
                hashB = mix(hashB, route.getVariantPriority());
                hashB = mix(hashB, route.getRoutePriority());
            }
            QIOPlanSourceRevisions revisions = snapshot.getSourceRevisions();
            return new TopologyKey(root, revisions.getRecipeCatalogRevision(),
                  revisions.getProviderCatalogRevision(), revisions.getPolicyRevision(),
                  snapshot.getRoutes().size(), hashA, hashB);
        }

        private static long mix(long current, long value) {
            long mixed = current ^ value;
            mixed *= 0x100000001B3L;
            return Long.rotateLeft(mixed, 17) ^ 0xBF58476D1CE4E5B9L;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof TopologyKey key)) return false;
            return recipeRevision == key.recipeRevision &&
                  providerRevision == key.providerRevision &&
                  policyRevision == key.policyRevision && routeCount == key.routeCount &&
                  routeHashA == key.routeHashA && routeHashB == key.routeHashB &&
                  root.equals(key.root);
        }

        @Override
        public int hashCode() {
            return Objects.hash(root, recipeRevision, providerRevision, policyRevision,
                  routeCount, routeHashA, routeHashB);
        }
    }

    private static long ceilDivide(long amount, long divisor) {
        return 1 + (amount - 1) / divisor;
    }

    private static final class FastPathDeclined extends RuntimeException {

        private static final FastPathDeclined INSTANCE = new FastPathDeclined();

        private FastPathDeclined() {
            super(null, null, false, false);
        }
    }
}
