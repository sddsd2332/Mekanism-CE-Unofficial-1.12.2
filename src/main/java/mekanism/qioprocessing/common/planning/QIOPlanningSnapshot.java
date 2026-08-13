package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingNbt;
import mekanism.qioprocessing.common.content.plan.QIOPlanSourceRevisions;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/** Immutable input captured on the server thread for one pure-data planning task. */
public final class QIOPlanningSnapshot {

    private static final Comparator<QIOPlanningRoute> ROUTE_ORDER = Comparator
          .comparingLong(QIOPlanningRoute::getRoutePriority).reversed()
          .thenComparing(QIOPlanningRoute::getLogicalId)
          .thenComparing(Comparator.comparingLong(
                QIOPlanningRoute::getVariantPriority).reversed())
          .thenComparing(QIOPlanningRoute::getStableId)
          .thenComparing(QIOPlanningRoute::getSignature);

    private final QIOPlanSourceRevisions sourceRevisions;
    private final Map<PortableResourceDescriptor, Long> availableResources;
    private final List<QIOPlanningRoute> routes;
    private final Map<PortableResourceDescriptor, List<QIOPlanningRoute>> routesByOutput;
    private final int maximumDepth;
    private final int maximumNodes;
    private final long maximumOperations;

    public QIOPlanningSnapshot(@Nonnull QIOPlanSourceRevisions sourceRevisions,
          @Nonnull Map<PortableResourceDescriptor, Long> availableResources,
          @Nonnull List<QIOPlanningRoute> routes, int maximumDepth, int maximumNodes,
          long maximumOperations) {
        this.sourceRevisions = Objects.requireNonNull(sourceRevisions, "sourceRevisions");
        this.availableResources = QIOProcessingNbt.copyAmounts(availableResources, true,
              "availableResources");
        if (maximumDepth <= 0 || maximumNodes <= 0 || maximumOperations <= 0) {
            throw new IllegalArgumentException("QIO planning budgets must be positive");
        }
        this.maximumDepth = maximumDepth;
        this.maximumNodes = maximumNodes;
        this.maximumOperations = maximumOperations;
        Objects.requireNonNull(routes, "routes");
        List<QIOPlanningRoute> routeCopy = new ArrayList<>(routes.size());
        Set<String> routeIds = new HashSet<>();
        for (QIOPlanningRoute route : routes) {
            QIOPlanningRoute checked = Objects.requireNonNull(route, "planning route");
            if (!routeIds.add(checked.getStableId())) {
                throw new IllegalArgumentException("Duplicate QIO planning route " +
                      checked.getStableId());
            }
            routeCopy.add(checked);
        }
        routeCopy.sort(Comparator.comparing(QIOPlanningRoute::getStableId));
        this.routes = Collections.unmodifiableList(routeCopy);
        Map<PortableResourceDescriptor, List<QIOPlanningRoute>> index = new LinkedHashMap<>();
        for (QIOPlanningRoute route : routeCopy) {
            for (PortableResourceDescriptor output : route.getGuaranteedOutputs().keySet()) {
                index.computeIfAbsent(output, ignored -> new ArrayList<>()).add(route);
            }
        }
        List<PortableResourceDescriptor> outputs = new ArrayList<>(index.keySet());
        Collections.sort(outputs);
        Map<PortableResourceDescriptor, List<QIOPlanningRoute>> orderedIndex = new LinkedHashMap<>();
        for (PortableResourceDescriptor output : outputs) {
            List<QIOPlanningRoute> candidates = index.get(output);
            candidates.sort(ROUTE_ORDER);
            orderedIndex.put(output, Collections.unmodifiableList(candidates));
        }
        routesByOutput = Collections.unmodifiableMap(orderedIndex);
    }

    @Nonnull
    public QIOPlanSourceRevisions getSourceRevisions() {
        return sourceRevisions;
    }

    @Nonnull
    public Map<PortableResourceDescriptor, Long> getAvailableResources() {
        return availableResources;
    }

    @Nonnull
    public List<QIOPlanningRoute> getRoutes() {
        return routes;
    }

    @Nonnull
    public List<QIOPlanningRoute> getRoutesProducing(PortableResourceDescriptor output) {
        return routesByOutput.getOrDefault(output, Collections.emptyList());
    }

    public int getMaximumDepth() {
        return maximumDepth;
    }

    public int getMaximumNodes() {
        return maximumNodes;
    }

    public long getMaximumOperations() {
        return maximumOperations;
    }
}
