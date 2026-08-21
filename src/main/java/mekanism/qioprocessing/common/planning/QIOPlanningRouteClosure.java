package mekanism.qioprocessing.common.planning;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.workbench.QIOWorkbenchConfiguration;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Builds a bounded exact-route dependency closure around one requested output. */
/**
 * QIO 处理模块中的 QIOPlanningRouteClosure 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOPlanningRouteClosure {

    private static final Comparator<QIOPlanningRoute> ROUTE_ORDER = Comparator
          .comparingLong(QIOPlanningRoute::getRoutePriority).reversed()
          .thenComparing(QIOPlanningRoute::getLogicalId)
          .thenComparing(Comparator.comparingLong(
                QIOPlanningRoute::getVariantPriority).reversed())
          .thenComparing(QIOPlanningRoute::getStableId);

    private QIOPlanningRouteClosure() {
    }

    @Nonnull
    public static ProviderIndex indexProviders(@Nonnull List<QIOPlanningRoute> routes) {
        return new ProviderIndex(routes);
    }

    @Nonnull
    public static List<QIOPlanningRoute> collect(@Nonnull PortableResourceDescriptor target,
          @Nonnull Map<PortableResourceDescriptor, Long> available,
          @Nonnull ProviderIndex providers,
          @Nonnull QIOWorkbenchRecipeCatalog.Snapshot workbench,
          @Nonnull Map<ResourceLocation, Long> workbenchPriorities,
          @Nonnull Set<PortableResourceDescriptor> producibleResources, int maximumRoutes) {
        return collect(target, available, providers, workbench, workbenchPriorities,
              producibleResources, maximumRoutes, null);
    }

    @Nonnull
    public static List<QIOPlanningRoute> collect(@Nonnull PortableResourceDescriptor target,
          @Nonnull Map<PortableResourceDescriptor, Long> available,
          @Nonnull ProviderIndex providers,
          @Nonnull QIOWorkbenchRecipeCatalog.Snapshot workbench,
          @Nonnull Map<ResourceLocation, Long> workbenchPriorities,
          @Nonnull Set<PortableResourceDescriptor> producibleResources, int maximumRoutes,
          @Nullable QIOWorkbenchConfiguration workbenchConfiguration) {
        return collect(target, available, providers, workbench, workbenchPriorities,
              producibleResources, maximumRoutes, workbenchConfiguration, () -> false);
    }

    @Nonnull
    public static List<QIOPlanningRoute> collect(@Nonnull PortableResourceDescriptor target,
          @Nonnull Map<PortableResourceDescriptor, Long> available,
          @Nonnull ProviderIndex providers,
          @Nonnull QIOWorkbenchRecipeCatalog.Snapshot workbench,
          @Nonnull Map<ResourceLocation, Long> workbenchPriorities,
          @Nonnull Set<PortableResourceDescriptor> producibleResources, int maximumRoutes,
          @Nullable QIOWorkbenchConfiguration workbenchConfiguration,
          @Nonnull BooleanSupplier cancelled) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(available, "available");
        Objects.requireNonNull(providers, "providers");
        Objects.requireNonNull(workbench, "workbench");
        Objects.requireNonNull(workbenchPriorities, "workbenchPriorities");
        Objects.requireNonNull(producibleResources, "producibleResources");
        Objects.requireNonNull(cancelled, "cancelled");
        if (maximumRoutes <= 0) {
            throw new IllegalArgumentException("maximumRoutes must be positive");
        }
        Map<String, QIOPlanningRoute> selected = new LinkedHashMap<>();
        Set<PortableResourceDescriptor> visited = new LinkedHashSet<>();
        Deque<PortableResourceDescriptor> pending = new ArrayDeque<>();
        pending.add(target);
        while (!pending.isEmpty() && selected.size() < maximumRoutes) {
            checkpoint(cancelled);
            PortableResourceDescriptor resource = pending.removeFirst();
            if (!visited.add(resource)) {
                continue;
            }
            List<QIOPlanningRoute> candidates = new ArrayList<>(providers.get(resource));
            int remaining = maximumRoutes - selected.size();
            candidates.addAll(workbench.materialize(resource, available, producibleResources,
                  workbenchPriorities, remaining, workbenchConfiguration, cancelled));
            candidates.sort(ROUTE_ORDER);
            for (QIOPlanningRoute route : candidates) {
                checkpoint(cancelled);
                if (selected.size() >= maximumRoutes) {
                    break;
                }
                if (selected.putIfAbsent(route.getStableId(), route) == null) {
                    pending.addAll(route.getExactInputs().keySet());
                }
            }
        }
        List<QIOPlanningRoute> result = new ArrayList<>(selected.values());
        result.sort(Comparator.comparing(QIOPlanningRoute::getStableId));
        return Collections.unmodifiableList(result);
    }

    private static void checkpoint(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException(
                  "QIO route closure cancelled");
        }
    }

    public static final class ProviderIndex {

        private final Map<PortableResourceDescriptor, List<QIOPlanningRoute>> routesByOutput;
        private final Set<PortableResourceDescriptor> outputs;

        private ProviderIndex(List<QIOPlanningRoute> routes) {
            Objects.requireNonNull(routes, "routes");
            Map<PortableResourceDescriptor, List<QIOPlanningRoute>> mutable =
                  new LinkedHashMap<>();
            for (QIOPlanningRoute route : routes) {
                QIOPlanningRoute checked = Objects.requireNonNull(route, "route");
                for (PortableResourceDescriptor output : checked.getGuaranteedOutputs().keySet()) {
                    mutable.computeIfAbsent(output, ignored -> new ArrayList<>()).add(checked);
                }
            }
            List<PortableResourceDescriptor> orderedOutputs = new ArrayList<>(mutable.keySet());
            Collections.sort(orderedOutputs);
            Map<PortableResourceDescriptor, List<QIOPlanningRoute>> index = new LinkedHashMap<>();
            for (PortableResourceDescriptor output : orderedOutputs) {
                List<QIOPlanningRoute> candidates = mutable.get(output);
                candidates.sort(ROUTE_ORDER);
                index.put(output, Collections.unmodifiableList(candidates));
            }
            routesByOutput = Collections.unmodifiableMap(index);
            outputs = Collections.unmodifiableSet(new LinkedHashSet<>(orderedOutputs));
        }

        @Nonnull
        public List<QIOPlanningRoute> get(@Nonnull PortableResourceDescriptor output) {
            return routesByOutput.getOrDefault(Objects.requireNonNull(output, "output"),
                  Collections.emptyList());
        }

        @Nonnull
        public Set<PortableResourceDescriptor> getOutputs() {
            return outputs;
        }
    }
}
