package mekanism.qioprocessing.common.planning;

import mekanism.api.processing.*;
import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.qioprocessing.common.machine.QIOAutomationDeviceRegistry;
import mekanism.qioprocessing.common.machine.QIOAutomationDeviceRegistry.LoadedDevice;
import mekanism.qioprocessing.common.util.QIOHashing;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Main-thread capture of exact scheduled routes and their currently usable devices.
 */

/**
 * QIO 处理模块中的 QIOProviderRouteCatalog 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class QIOProviderRouteCatalog {

    public static final int MAX_SOURCES = 4_096;
    public static final int MAX_ROUTES = 65_536;
    public static final int MAX_PORTS_PER_SOURCE = 128;

    @FunctionalInterface
    public interface RoutePriorityResolver {

        long priority(@Nonnull ResourceLocation providerId, @Nonnull String routeId,
                      @Nonnull String logicalRecipeKey);
    }

    private final Map<String, SourceSnapshot> sources = new LinkedHashMap<>();
    private final Map<String, String> sourceDiagnostics = new LinkedHashMap<>();
    private long revision;
    private long availabilityRevision;

    public QIOProviderRouteCatalog() {
    }

    public synchronized boolean observe(String sourceId, MachineRecipeProviderRegistry.BoundProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String checkedSourceId = requireSourceId(sourceId);
        if (!provider.isAvailable()) {
            boolean changed = forget(checkedSourceId);
            sourceDiagnostics.put(checkedSourceId, "provider unavailable");
            return changed;
        }
        try {
            ProviderConformanceReport report = provider.validateQIOConformance(
                    QIOAutomationMode.SCHEDULED);
            if (!report.isConformant()) {
                boolean changed = forget(checkedSourceId);
                sourceDiagnostics.put(checkedSourceId, String.join("; ", report.errors()));
                return changed;
            }
            boolean changed = observe(checkedSourceId, provider.id(), provider.getRecipeSourceKey(),
                    provider.getConfigurationRevision(), provider.getRecipeProfileScopeDiscriminator(),
                    provider.getRecipeRoutes(), provider.getPorts());
            sourceDiagnostics.remove(checkedSourceId);
            return changed;
        } catch (RuntimeException error) {
            forget(checkedSourceId);
            sourceDiagnostics.put(checkedSourceId, "provider observation failed: " +
                    error.getClass().getName());
            return false;
        }
    }

    public synchronized boolean forget(String sourceId) {
        String checkedSourceId = requireSourceId(sourceId);
        SourceSnapshot removed = sources.remove(checkedSourceId);
        sourceDiagnostics.remove(checkedSourceId);
        if (removed == null) {
            return false;
        }
        incrementRevision();
        incrementAvailabilityRevision();
        return true;
    }

    public synchronized boolean observe(String sourceId, ResourceLocation providerId,
                                        Object recipeSourceKey, int configurationRevision, String profileScopeDiscriminator,
                                        List<MachineRecipeRoute> routes, List<MachinePort> ports) {
        return observe(sourceId, null, providerId, recipeSourceKey, configurationRevision,
                profileScopeDiscriminator, routes, ports);
    }

    public synchronized boolean observe(String sourceId, @Nullable UUID frequencyUUID,
                                        ResourceLocation providerId, Object recipeSourceKey, int configurationRevision,
                                        String profileScopeDiscriminator, List<MachineRecipeRoute> routes,
                                        List<MachinePort> ports) {
        String checkedSourceId = requireSourceId(sourceId);
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(routes, "routes");
        Objects.requireNonNull(ports, "ports");
        if (sources.size() >= MAX_SOURCES && !sources.containsKey(checkedSourceId)) {
            throw new IllegalStateException("QIO Provider source catalog limit reached");
        }
        if (ports.size() > MAX_PORTS_PER_SOURCE) {
            throw new IllegalArgumentException("Too many Provider ports");
        }
        Set<String> routeKeys = new LinkedHashSet<>();
        for (MachineRecipeRoute route : routes) {
            if (route == null || !routeKeys.add(route.routeId() + "\u0000" + route.recipeKey())) {
                throw new IllegalArgumentException("Provider route list contains a null or duplicate route");
            }
        }
        Set<String> portIds = new LinkedHashSet<>();
        for (MachinePort port : ports) {
            if (port == null || !portIds.add(port.portId())) {
                throw new IllegalArgumentException("Provider port list contains a null or duplicate port");
            }
        }
        int otherRouteCount = 0;
        for (SourceSnapshot source : sources.values()) {
            if (!source.sourceId().equals(checkedSourceId)) {
                otherRouteCount += source.routes().size();
            }
        }
        if (otherRouteCount > MAX_ROUTES - routes.size()) {
            throw new IllegalStateException("QIO Provider route catalog limit reached");
        }
        SourceSnapshot candidate = new SourceSnapshot(checkedSourceId, frequencyUUID, providerId,
                recipeSourceKey == null ? "" : String.valueOf(recipeSourceKey), configurationRevision,
                normalizeScope(profileScopeDiscriminator), routes, ports);
        SourceSnapshot previous = sources.get(checkedSourceId);
        if (candidate.equals(previous)) {
            sourceDiagnostics.remove(checkedSourceId);
            return false;
        }
        sources.put(checkedSourceId, candidate);
        sourceDiagnostics.remove(checkedSourceId);
        incrementRevision();
        if (previous == null || !previous.providerId().equals(candidate.providerId())) {
            incrementAvailabilityRevision();
        }
        return true;
    }

    public synchronized Snapshot snapshot() {
        return snapshot(null);
    }

    /** Returns only sources bound to the supplied QIO frequency. Unbound test/projection
     * sources are included only in the unfiltered snapshot. */
    public synchronized Snapshot snapshot(@Nullable UUID frequencyUUID) {
        List<SourceSnapshot> sourceCopy = new ArrayList<>(sources.values());
        if (frequencyUUID != null) {
            sourceCopy.removeIf(source -> !frequencyUUID.equals(source.frequencyUUID()));
        }
        sourceCopy.sort(Comparator.comparing(SourceSnapshot::sourceId));
        return new Snapshot(revision, availabilityRevision, sourceCopy, sourceDiagnostics,
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet(),
                Collections.emptyList());
    }

    private static String requireSourceId(String sourceId) {
        Objects.requireNonNull(sourceId, "sourceId");
        String checked = sourceId.trim();
        if (checked.isEmpty() || checked.length() > 256) {
            throw new IllegalArgumentException("sourceId must contain 1..256 characters");
        }
        return checked;
    }

    private static String normalizeScope(String scope) {
        if (scope == null) {
            return "";
        }
        String checked = scope.trim();
        if (checked.length() > 128) {
            throw new IllegalArgumentException("profile scope is too long");
        }
        return checked;
    }

    private void incrementRevision() {
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO Provider route revision exhausted");
        }
        revision++;
    }

    private void incrementAvailabilityRevision() {
        if (availabilityRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("QIO Provider availability revision exhausted");
        }
        availabilityRevision++;
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
            return new Snapshot(0, 0, Collections.emptyList(), Collections.emptyMap(), routes,
                    devicesByRoute, observedDevices, diagnostics);
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

        private final long revision;
        private final long availabilityRevision;
        private final List<SourceSnapshot> sources;
        private final Map<String, String> sourceDiagnostics;
        private final List<QIOPlanningRoute> routes;
        private final Map<String, List<UUID>> devicesByRoute;
        private final Set<UUID> observedDevices;
        private final List<String> diagnostics;
        private final String structuralSignature;

        private Snapshot(long revision, long availabilityRevision, List<SourceSnapshot> sources,
                         Map<String, String> sourceDiagnostics, Map<String, QIOPlanningRoute> routes,
                         Map<String, Set<UUID>> devicesByRoute, Set<UUID> observedDevices,
                         List<String> diagnostics) {
            this.revision = revision;
            this.availabilityRevision = availabilityRevision;
            this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
            this.sourceDiagnostics = Collections.unmodifiableMap(new LinkedHashMap<>(sourceDiagnostics));
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
            structuralSignature = sources.isEmpty() ? calculateSignature(routeList) :
                    calculateSourceSignature(sources);
        }

        public long revision() {
            return revision;
        }

        public long availabilityRevision() {
            return availabilityRevision;
        }

        @Nonnull
        public List<SourceSnapshot> sources() {
            return sources;
        }

        @Nonnull
        public Map<String, String> sourceDiagnostics() {
            return sourceDiagnostics;
        }

        @Nonnull
        public Snapshot forFrequency(@Nonnull UUID frequencyUUID) {
            Objects.requireNonNull(frequencyUUID, "frequencyUUID");
            if (sources.isEmpty()) return this;
            List<SourceSnapshot> filtered = new ArrayList<>();
            for (SourceSnapshot source : sources) {
                if (source.frequencyUUID() == null || frequencyUUID.equals(source.frequencyUUID())) {
                    filtered.add(source);
                }
            }
            return new Snapshot(revision, availabilityRevision, filtered, sourceDiagnostics,
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet(),
                    Collections.emptyList());
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

        /** Compatibility projection used by the durable Provider planner. */
        @Nonnull
        public List<RouteSnapshot> routes() {
            if (!sources.isEmpty()) {
                List<RouteSnapshot> result = new ArrayList<>();
                for (SourceSnapshot source : sources) {
                    result.addAll(source.routeSnapshots());
                }
                result.sort(Comparator.comparing(RouteSnapshot::stableId));
                return Collections.unmodifiableList(result);
            }
            List<RouteSnapshot> result = new ArrayList<>();
            for (QIOPlanningRoute route : routes) result.add(RouteSnapshot.from(route));
            return Collections.unmodifiableList(result);
        }

        private static String calculateSignature(List<QIOPlanningRoute> routes) {
            StringBuilder canonical = new StringBuilder();
            for (QIOPlanningRoute route : routes) {
                canonical.append(route.getStableId()).append('|').append(route.getSignature())
                        .append('|').append(route.getRoutePriority()).append('\n');
            }
            return QIOHashing.sha256(canonical);
        }

        private static String calculateSourceSignature(List<SourceSnapshot> sources) {
            StringBuilder canonical = new StringBuilder();
            for (SourceSnapshot source : sources) {
                canonical.append(source.fingerprint()).append('\n');
            }
            return QIOHashing.sha256(canonical);
        }
    }

    public static final class SourceSnapshot {
        private final String sourceId;
        private final UUID frequencyUUID;
        private final ResourceLocation providerId;
        private final String recipeSourceKey;
        private final int configurationRevision;
        private final String profileScopeDiscriminator;
        private final List<MachineRecipeRoute> routes;
        private final List<MachinePort> ports;

        private SourceSnapshot(String sourceId, @Nullable UUID frequencyUUID,
                               ResourceLocation providerId, String recipeSourceKey,
                               int configurationRevision, String profileScopeDiscriminator,
                               List<MachineRecipeRoute> routes, List<MachinePort> ports) {
            this.sourceId = sourceId;
            this.frequencyUUID = frequencyUUID;
            this.providerId = providerId;
            this.recipeSourceKey = recipeSourceKey;
            this.configurationRevision = configurationRevision;
            this.profileScopeDiscriminator = profileScopeDiscriminator;
            this.routes = Collections.unmodifiableList(new ArrayList<>(routes));
            this.ports = Collections.unmodifiableList(new ArrayList<>(ports));
        }

        public String sourceId() {
            return sourceId;
        }

        @Nullable
        public UUID frequencyUUID() {
            return frequencyUUID;
        }

        public ResourceLocation providerId() {
            return providerId;
        }

        public String recipeSourceKey() {
            return recipeSourceKey;
        }

        public int configurationRevision() {
            return configurationRevision;
        }

        public String profileScopeDiscriminator() {
            return profileScopeDiscriminator;
        }

        public List<MachineRecipeRoute> routes() {
            return routes;
        }

        public List<MachinePort> ports() {
            return ports;
        }

        private List<RouteSnapshot> routeSnapshots() {
            List<RouteSnapshot> result = new ArrayList<>();
            for (MachineRecipeRoute route : routes) {
                result.add(new RouteSnapshot(sourceId, frequencyUUID, providerId, configurationRevision,
                        profileScopeDiscriminator, route));
            }
            return result;
        }

        @Override
        public boolean equals(Object value) {
            return value instanceof SourceSnapshot && fingerprint().equals(
                    ((SourceSnapshot) value).fingerprint());
        }

        @Override
        public int hashCode() {
            return fingerprint().hashCode();
        }

        private String fingerprint() {
            StringBuilder result = new StringBuilder(sourceId).append('|').append(frequencyUUID)
                    .append('|').append(providerId)
                    .append('|').append(recipeSourceKey).append('|').append(configurationRevision)
                    .append('|').append(profileScopeDiscriminator);
            for (MachineRecipeRoute route : routes) {
                result.append("|route=").append(route.routeId()).append('|')
                        .append(route.recipeKey()).append('|').append(route.logicalRecipeKey());
                appendStacks(result, route.configurationInputs());
                appendStacks(result, route.inputs());
                appendStacks(result, route.guaranteedOutputs());
                appendStacks(result, route.optionalOutputs());
            }
            for (MachinePort port : ports) {
                result.append("|port=").append(port.portId()).append('|')
                        .append(port.portGroupId()).append('|').append(port.laneId()).append('|')
                        .append(port.role()).append('|').append(port.purpose()).append('|')
                        .append(port.resourceMatcher().isAny());
                port.resourceMatcher().getFamilies().stream().sorted()
                        .forEach(family -> result.append("|family=").append(family));
                port.resourceMatcher().getCodecIds().stream().map(ResourceLocation::toString)
                        .sorted().forEach(codec -> result.append("|codec=").append(codec));
            }
            return result.toString();
        }

        private static void appendStacks(StringBuilder result,
                                         List<MachineResourceStack> stacks) {
            for (MachineResourceStack stack : stacks) {
                result.append('|').append(stack.portId()).append('@').append(stack.amount())
                        .append(':').append(stack.descriptor());
            }
        }
    }

    /** Detached route view for the newer Provider planner/execution manifest. */
    public static final class RouteSnapshot {
        private final String sourceId;
        @Nullable
        private final UUID frequencyUUID;
        private final ResourceLocation providerId;
        private final int configurationRevision;
        private final String profileScopeDiscriminator;
        private final MachineRecipeRoute route;

        private RouteSnapshot(String sourceId, @Nullable UUID frequencyUUID,
                              ResourceLocation providerId, int configurationRevision,
                              String profileScopeDiscriminator, MachineRecipeRoute route) {
            this.sourceId = sourceId;
            this.frequencyUUID = frequencyUUID;
            this.providerId = providerId;
            this.configurationRevision = configurationRevision;
            this.profileScopeDiscriminator = profileScopeDiscriminator;
            this.route = route;
        }

        static RouteSnapshot from(QIOPlanningRoute source) {
            MachineRecipeRoute.Builder builder = MachineRecipeRoute.builder(source.getRouteId())
                    .recipeKey(source.getRecipeKey()).logicalRecipeKey(source.getRecipeKey());
            source.getConfigurationInputs().forEach((resource, amount) ->
                    builder.configurationInput(MachineResourceStack.resource("qio", resource.getDescriptor(), amount)));
            source.getExactInputs().forEach((resource, amount) ->
                    builder.input(MachineResourceStack.resource("qio", resource.getDescriptor(), amount)));
            source.getGuaranteedOutputs().forEach((resource, amount) ->
                    builder.output(MachineResourceStack.resource("qio", resource.getDescriptor(), amount)));
            source.getOptionalOutputs().forEach((resource, amount) ->
                    builder.optionalOutput(MachineResourceStack.resource("qio", resource.getDescriptor(), amount)));
            return new RouteSnapshot(source.getStableId(), null, new ResourceLocation(source.getProviderId()), 0, "",
                    builder.build());
        }

        public String sourceId() {
            return sourceId;
        }

        @Nullable
        public UUID frequencyUUID() {
            return frequencyUUID;
        }

        public ResourceLocation providerId() {
            return providerId;
        }

        public int configurationRevision() {
            return configurationRevision;
        }

        public String profileScopeDiscriminator() {
            return profileScopeDiscriminator;
        }

        public MachineRecipeRoute route() {
            return route;
        }

        public String stableId() {
            return providerId + "/" + sourceId + "/" + route.routeId() + "/" + route.recipeKey();
        }
    }
}
