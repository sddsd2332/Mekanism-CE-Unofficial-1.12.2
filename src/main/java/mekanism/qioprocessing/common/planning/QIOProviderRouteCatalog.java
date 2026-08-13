package mekanism.qioprocessing.common.planning;

import mekanism.api.processing.MachineRecipeProviderRegistry;
import mekanism.api.processing.MachineRecipeRoute;
import mekanism.api.processing.ProviderConformanceReport;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.qioprocessing.common.machine.QIOAutomationDeviceRegistry;
import mekanism.qioprocessing.common.machine.QIOAutomationDeviceRegistry.LoadedDevice;
import mekanism.qioprocessing.common.util.QIOHashing;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Main-thread capture of exact scheduled routes and their currently usable devices. */
public final class QIOProviderRouteCatalog {

    @FunctionalInterface
    public interface RoutePriorityResolver {

        long priority(@Nonnull ResourceLocation providerId, @Nonnull String routeId,
              @Nonnull String logicalRecipeKey);
    }

    private QIOProviderRouteCatalog() {
    }

    @Nonnull
    public static Snapshot capture(@Nonnull UUID frequencyUUID,
          @Nonnull QIOAutomationDeviceRegistry registry,
          @Nonnull RoutePriorityResolver priorityResolver) {
        Objects.requireNonNull(frequencyUUID, "frequencyUUID");
        Objects.requireNonNull(registry, "registry");
        Builder builder = new Builder(priorityResolver);
        for (LoadedDevice device : registry.getUsableDevices(QIOAutomationMode.SCHEDULED)) {
            QIOFrequencyReference reference = device.host().getFrequencyReference();
            if (reference == null || !frequencyUUID.equals(reference.getFrequencyUUID())) {
                continue;
            }
            MachineRecipeProviderRegistry.BoundProvider provider =
                  MachineRecipeProviderRegistry.find(device.tile());
            if (provider == null) {
                builder.addDiagnostic("Device " + device.host().getPersistentDeviceUUID() +
                      " lost its machine provider");
                continue;
            }
            ProviderConformanceReport report = strictRouteReport(provider,
                  QIOAutomationMode.SCHEDULED);
            if (report == null || !report.isConformant()) {
                builder.addDiagnostic("Provider " + provider.id() + " on device " +
                      device.host().getPersistentDeviceUUID() + " is not conformant: " +
                      (report == null ? "missing provider" :
                            String.join("; ", report.errors())));
                continue;
            }
            builder.addDevice(device.host().getPersistentDeviceUUID(), provider.id(),
                  provider.getRecipeRoutes());
        }
        return builder.build();
    }

    @Nullable
    static ProviderConformanceReport strictRouteReport(
          @Nullable MachineRecipeProviderRegistry.BoundProvider provider,
          @Nonnull QIOAutomationMode mode) {
        return provider == null ? null : provider.validateQIOConformance(mode);
    }

    public static final class Builder {

        private final RoutePriorityResolver priorityResolver;
        private final Map<String, QIOPlanningRoute> routes = new LinkedHashMap<>();
        private final Map<String, Set<UUID>> devicesByRoute = new LinkedHashMap<>();
        private final Set<UUID> observedDevices = new LinkedHashSet<>();
        private final Set<String> conflictedRoutes = new LinkedHashSet<>();
        private final List<String> diagnostics = new ArrayList<>();

        public Builder(@Nonnull RoutePriorityResolver priorityResolver) {
            this.priorityResolver = Objects.requireNonNull(priorityResolver, "priorityResolver");
        }

        public Builder addDevice(@Nonnull UUID deviceUUID, @Nonnull ResourceLocation providerId,
              @Nonnull List<MachineRecipeRoute> machineRoutes) {
            Objects.requireNonNull(deviceUUID, "deviceUUID");
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(machineRoutes, "machineRoutes");
            observedDevices.add(deviceUUID);
            for (MachineRecipeRoute machineRoute : machineRoutes) {
                if (machineRoute == null) {
                    addDiagnostic("Provider " + providerId + " returned a null route");
                    continue;
                }
                try {
                    long priority = priorityResolver.priority(providerId, machineRoute.routeId(),
                          machineRoute.logicalRecipeKey());
                    QIOPlanningRoute route = QIOPlanningRoute.fromMachineRoute(providerId,
                          machineRoute, priority);
                    merge(deviceUUID, route);
                } catch (RuntimeException e) {
                    addDiagnostic("Unable to capture route " + providerId + '/' +
                          machineRoute.routeId() + ": " +
                          (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                }
            }
            return this;
        }

        public Builder addDiagnostic(@Nonnull String diagnostic) {
            diagnostics.add(Objects.requireNonNull(diagnostic, "diagnostic"));
            return this;
        }

        @Nonnull
        public Snapshot build() {
            return new Snapshot(routes, devicesByRoute, observedDevices, diagnostics);
        }

        private void merge(UUID deviceUUID, QIOPlanningRoute route) {
            String stableId = route.getStableId();
            if (conflictedRoutes.contains(stableId)) {
                return;
            }
            QIOPlanningRoute existing = routes.get(stableId);
            if (existing != null && (!existing.getSignature().equals(route.getSignature()) ||
                  existing.getRoutePriority() != route.getRoutePriority())) {
                routes.remove(stableId);
                devicesByRoute.remove(stableId);
                conflictedRoutes.add(stableId);
                addDiagnostic("Conflicting QIO route identity " + stableId);
                return;
            }
            if (existing == null) {
                routes.put(stableId, route);
            }
            devicesByRoute.computeIfAbsent(stableId, ignored -> new LinkedHashSet<>())
                  .add(deviceUUID);
        }
    }

    public static final class Snapshot {

        private final List<QIOPlanningRoute> routes;
        private final Map<String, List<UUID>> devicesByRoute;
        private final Set<UUID> observedDevices;
        private final List<String> diagnostics;
        private final String structuralSignature;

        private Snapshot(Map<String, QIOPlanningRoute> routes,
              Map<String, Set<UUID>> devicesByRoute, Set<UUID> observedDevices,
              List<String> diagnostics) {
            List<QIOPlanningRoute> routeList = new ArrayList<>(routes.values());
            routeList.sort(Comparator.comparing(QIOPlanningRoute::getStableId));
            this.routes = Collections.unmodifiableList(routeList);
            Map<String, List<UUID>> deviceCopy = new LinkedHashMap<>();
            for (QIOPlanningRoute route : routeList) {
                List<UUID> devices = new ArrayList<>(devicesByRoute.getOrDefault(
                      route.getStableId(), Collections.emptySet()));
                devices.sort(Comparator.comparing(UUID::toString));
                deviceCopy.put(route.getStableId(), Collections.unmodifiableList(devices));
            }
            this.devicesByRoute = Collections.unmodifiableMap(deviceCopy);
            List<UUID> orderedDevices = new ArrayList<>(observedDevices);
            orderedDevices.sort(Comparator.comparing(UUID::toString));
            this.observedDevices = Collections.unmodifiableSet(new LinkedHashSet<>(orderedDevices));
            this.diagnostics = Collections.unmodifiableList(new ArrayList<>(diagnostics));
            structuralSignature = calculateSignature(routeList);
        }

        @Nonnull
        public List<QIOPlanningRoute> getRoutes() {
            return routes;
        }

        @Nonnull
        public List<UUID> getDeviceIds(@Nonnull String stableRouteId) {
            return devicesByRoute.getOrDefault(Objects.requireNonNull(stableRouteId,
                  "stableRouteId"), Collections.emptyList());
        }

        @Nonnull
        public Set<UUID> getObservedDeviceIds() {
            return observedDevices;
        }

        @Nonnull
        public List<String> getDiagnostics() {
            return diagnostics;
        }

        @Nonnull
        public String getStructuralSignature() {
            return structuralSignature;
        }

        private static String calculateSignature(List<QIOPlanningRoute> routes) {
            StringBuilder canonical = new StringBuilder();
            for (QIOPlanningRoute route : routes) {
                canonical.append(route.getStableId()).append('|').append(route.getSignature())
                      .append('|').append(route.getRoutePriority()).append('\n');
            }
            return QIOHashing.sha256(canonical);
        }
    }
}
