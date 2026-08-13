package mekanism.api.processing;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry used by addons to discover network-independent machine processing providers without bytecode injection.
 */
public final class MachineRecipeProviderRegistry {

    private static final Map<ResourceLocation, Entry<?>> ENTRIES = new LinkedHashMap<>();
    private static final Map<Class<?>, Entry<?>> RESOLUTION_CACHE = new ConcurrentHashMap<>();
    private static final Entry<TileEntity> NO_ENTRY = new Entry<>(new ResourceLocation("mekanism", "missing"), TileEntity.class,
          new MachineRecipeProvider<TileEntity>() {
          }, Integer.MIN_VALUE);
    private static volatile List<Entry<?>> snapshot = Collections.emptyList();

    private MachineRecipeProviderRegistry() {
    }

    public static <TILE extends TileEntity> void register(ResourceLocation id, Class<TILE> tileClass,
          MachineRecipeProvider<? super TILE> provider) {
        register(id, tileClass, provider, 0);
    }

    public static synchronized <TILE extends TileEntity> void register(ResourceLocation id, Class<TILE> tileClass,
          MachineRecipeProvider<? super TILE> provider, int priority) {
        Objects.requireNonNull(id, "Provider id cannot be null");
        if (ENTRIES.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate machine recipe provider id: " + id);
        }
        ENTRIES.put(id, new Entry<>(id, Objects.requireNonNull(tileClass, "Tile class cannot be null"),
              Objects.requireNonNull(provider, "Provider cannot be null"), priority));
        rebuildSnapshot();
    }

    public static synchronized boolean unregister(ResourceLocation id) {
        if (id == null || ENTRIES.remove(id) == null) {
            return false;
        }
        rebuildSnapshot();
        return true;
    }

    @Nullable
    public static BoundProvider find(@Nullable TileEntity tile) {
        if (tile == null) {
            return null;
        }
        Entry<?> entry = RESOLUTION_CACHE.computeIfAbsent(tile.getClass(), MachineRecipeProviderRegistry::resolve);
        return entry == NO_ENTRY ? null : new BoundProvider(tile, entry);
    }

    public static boolean hasProvider(@Nullable TileEntity tile) {
        return find(tile) != null;
    }

    public static synchronized Set<ResourceLocation> getRegisteredIds() {
        return Collections.unmodifiableSet(new HashSet<>(ENTRIES.keySet()));
    }

    private static Entry<?> resolve(Class<?> actualClass) {
        Entry<?> best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Entry<?> candidate : snapshot) {
            if (!candidate.tileClass.isAssignableFrom(actualClass)) {
                continue;
            }
            int distance = inheritanceDistance(actualClass, candidate.tileClass);
            if (best == null || candidate.priority > best.priority || candidate.priority == best.priority && distance < bestDistance ||
                  candidate.priority == best.priority && distance == bestDistance && candidate.id.toString().compareTo(best.id.toString()) < 0) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best == null ? NO_ENTRY : best;
    }

    private static int inheritanceDistance(Class<?> actualClass, Class<?> targetClass) {
        if (actualClass == targetClass) {
            return 0;
        }
        Set<Class<?>> visited = new HashSet<>();
        Deque<ClassDistance> queue = new ArrayDeque<>();
        queue.add(new ClassDistance(actualClass, 0));
        while (!queue.isEmpty()) {
            ClassDistance current = queue.removeFirst();
            if (!visited.add(current.type)) {
                continue;
            }
            if (current.type == targetClass) {
                return current.distance;
            }
            Class<?> parent = current.type.getSuperclass();
            if (parent != null) {
                queue.addLast(new ClassDistance(parent, current.distance + 1));
            }
            for (Class<?> iface : current.type.getInterfaces()) {
                queue.addLast(new ClassDistance(iface, current.distance + 1));
            }
        }
        return Integer.MAX_VALUE;
    }

    private static void rebuildSnapshot() {
        List<Entry<?>> entries = new ArrayList<>(ENTRIES.values());
        entries.sort(Comparator.comparing(entry -> entry.id.toString()));
        snapshot = Collections.unmodifiableList(entries);
        RESOLUTION_CACHE.clear();
    }

    public static final class BoundProvider {

        private final TileEntity tile;
        private final Entry<?> entry;

        private BoundProvider(TileEntity tile, Entry<?> entry) {
            this.tile = tile;
            this.entry = entry;
        }

        public ResourceLocation id() {
            return entry.id;
        }

        public TileEntity tile() {
            return tile;
        }

        public boolean isAvailable() {
            return entry.isAvailable(tile);
        }

        @Nullable
        public Object getRecipeSourceKey() {
            return entry.getRecipeSourceKey(tile);
        }

        public int getConfigurationRevision() {
            return entry.getConfigurationRevision(tile);
        }

        public List<MachineRecipeRoute> getRecipeRoutes() {
            return immutableCopy(entry.getRecipeRoutes(tile));
        }

        public List<MachinePort> getPorts() {
            return immutableCopy(entry.getPorts(tile));
        }

        public MachinePresentationDescriptor getPresentation() {
            MachinePresentationDescriptor fallback = MachinePresentationDescriptor.fallback(tile);
            try {
                MachinePresentationDescriptor presentation = entry.getPresentation(tile);
                return presentation == null ? fallback :
                      MachinePresentationDescriptor.read(presentation.write());
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }

        public String getRecipeProfileScopeDiscriminator() {
            try {
                String value = entry.getRecipeProfileScopeDiscriminator(tile);
                String checked = value == null ? "" : value.trim();
                if (checked.length() > 128) return "";
                for (int index = 0; index < checked.length(); index++) {
                    char character = checked.charAt(index);
                    if (!(character >= 'a' && character <= 'z') &&
                        !(character >= '0' && character <= '9') &&
                        character != '_' && character != '-' && character != '.' &&
                        character != '/') return "";
                }
                return checked;
            } catch (RuntimeException ignored) {
                return "";
            }
        }

        public ProviderConformanceDescriptor getQIOConformance() {
            ProviderConformanceDescriptor descriptor = entry.getQIOConformance(tile);
            return descriptor == null ? ProviderConformanceDescriptor.unregistered() : descriptor;
        }

        /**
         * Validates whether this provider is a structurally usable QIO endpoint. Dynamic
         * recipe routes may be empty while a template-driven machine is not configured.
         */
        public ProviderConformanceReport validateQIOEndpointConformance(
              QIOAutomationMode mode) {
            Objects.requireNonNull(mode, "QIO automation mode cannot be null");
            List<String> errors = new ArrayList<>();
            validateEndpointStructure(mode, getPorts(), errors);
            return new ProviderConformanceReport(mode, errors);
        }

        private void validateEndpointStructure(QIOAutomationMode mode,
              List<MachinePort> ports, List<String> errors) {
            ProviderConformanceDescriptor descriptor = getQIOConformance();
            if (!descriptor.isRegistered()) {
                errors.add("provider has no explicit QIO conformance declaration");
                return;
            }
            if (descriptor.version() != ProviderConformanceDescriptor.CURRENT_VERSION) {
                errors.add("unsupported conformance descriptor version " + descriptor.version());
            }
            if (!entry.id.getNamespace().equals(descriptor.ownerModId())) {
                errors.add("provider namespace does not match conformance owner");
            }
            if (!descriptor.supports(mode)) {
                errors.add("provider does not declare mode " + mode);
            }

            Map<String, MachinePort> portsById = new LinkedHashMap<>();
            for (MachinePort port : ports) {
                if (port == null) {
                    errors.add("provider returned a null port");
                    continue;
                }
                if (portsById.put(port.portId(), port) != null) {
                    errors.add("duplicate port id " + port.portId());
                }
                if (port.portGroupId().isEmpty()) {
                    errors.add("port " + port.portId() + " has no stable port group");
                }
                if (port.laneId() < MachinePort.SHARED_LANE) {
                    errors.add("port " + port.portId() + " has an invalid lane id");
                }
            }
            if (portsById.isEmpty()) {
                errors.add("provider exposes no machine ports");
            }

            boolean hasInput = false;
            boolean hasOutput = false;
            for (MachinePort port : portsById.values()) {
                if (!port.isConfiguration()) {
                    hasInput |= port.role().acceptsInput();
                    hasOutput |= port.role().allowsOutput();
                }
            }
            if (mode == QIOAutomationMode.OUTPUT_ONLY) {
                if (!hasOutput) {
                    errors.add("output-only provider exposes no output port");
                }
            } else {
                if (!hasInput) {
                    errors.add("processing provider exposes no input port");
                }
                if (!hasOutput) {
                    errors.add("processing provider exposes no output port");
                }
            }
        }

        /**
         * Strict runtime validation for consumers that publish, plan, or execute the
         * provider's current routes.
         */
        public ProviderConformanceReport validateQIOConformance(QIOAutomationMode mode) {
            Objects.requireNonNull(mode, "QIO automation mode cannot be null");
            List<MachinePort> ports = getPorts();
            List<String> errors = new ArrayList<>();
            validateEndpointStructure(mode, ports, errors);
            if (errors.isEmpty() && mode != QIOAutomationMode.OUTPUT_ONLY) {
                validateRoutes(getRecipeRoutes(), portsById(ports), errors);
            }
            return new ProviderConformanceReport(mode, errors);
        }

        private static Map<String, MachinePort> portsById(List<MachinePort> ports) {
            Map<String, MachinePort> portsById = new LinkedHashMap<>();
            for (MachinePort port : ports) {
                if (port != null) {
                    portsById.put(port.portId(), port);
                }
            }
            return portsById;
        }

        private static void validateRoutes(List<MachineRecipeRoute> routes, Map<String, MachinePort> portsById,
              List<String> errors) {
            if (routes.isEmpty()) {
                errors.add("processing provider exposes no recipe routes");
                return;
            }
            Set<String> recipeKeys = new HashSet<>();
            for (MachineRecipeRoute route : routes) {
                if (route == null) {
                    errors.add("provider returned a null route");
                    continue;
                }
                if (!recipeKeys.add(route.recipeKey())) {
                    errors.add("duplicate recipe key " + route.recipeKey());
                }
                validateConfigurationStacks(route.configurationInputs(), portsById,
                      route.routeId(), errors);
                validateRouteStacks(route.inputs(), portsById, true, route.routeId(), errors);
                validateRouteStacks(route.guaranteedOutputs(), portsById, false, route.routeId(), errors);
                validateRouteStacks(route.optionalOutputs(), portsById, false, route.routeId(), errors);
            }
        }

        private static void validateConfigurationStacks(List<MachineResourceStack> stacks,
              Map<String, MachinePort> portsById, String routeId, List<String> errors) {
            Set<String> referencedPorts = new HashSet<>();
            for (MachineResourceStack stack : stacks) {
                MachinePort port = portsById.get(stack.portId());
                if (!referencedPorts.add(stack.portId())) {
                    errors.add("route " + routeId + " repeats configuration port " +
                          stack.portId());
                } else if (port == null) {
                    errors.add("route " + routeId + " references missing configuration port " +
                          stack.portId());
                } else if (!port.isConfiguration()) {
                    errors.add("route " + routeId + " uses a processing port as configuration " +
                          stack.portId());
                } else if (port.kind() != stack.kind()) {
                    errors.add("route " + routeId + " uses the wrong resource kind for port " +
                          stack.portId());
                }
            }
        }

        private static void validateRouteStacks(List<MachineResourceStack> stacks, Map<String, MachinePort> portsById,
              boolean input, String routeId, List<String> errors) {
            for (MachineResourceStack stack : stacks) {
                MachinePort port = portsById.get(stack.portId());
                if (port == null) {
                    errors.add("route " + routeId + " references missing port " + stack.portId());
                } else if (port.isConfiguration()) {
                    errors.add("route " + routeId + " uses configuration port " + stack.portId() +
                          (input ? " as a consumable input" : " as an output"));
                } else if (port.kind() != stack.kind()) {
                    errors.add("route " + routeId + " uses the wrong resource kind for port " + stack.portId());
                } else if (input && !port.role().acceptsInput() || !input && !port.role().allowsOutput()) {
                    errors.add("route " + routeId + " uses port " + stack.portId() + " in the wrong direction");
                }
            }
        }

        private static <T> List<T> immutableCopy(@Nullable List<T> values) {
            return values == null || values.isEmpty() ? Collections.emptyList() :
                  Collections.unmodifiableList(new ArrayList<>(values));
        }
    }

    private static final class Entry<TILE extends TileEntity> {

        private final ResourceLocation id;
        private final Class<TILE> tileClass;
        private final MachineRecipeProvider<? super TILE> provider;
        private final int priority;

        private Entry(ResourceLocation id, Class<TILE> tileClass, MachineRecipeProvider<? super TILE> provider, int priority) {
            this.id = id;
            this.tileClass = tileClass;
            this.provider = provider;
            this.priority = priority;
        }

        private TILE cast(TileEntity tile) {
            return tileClass.cast(tile);
        }

        private boolean isAvailable(TileEntity tile) {
            return provider.isAvailable(cast(tile));
        }

        private Object getRecipeSourceKey(TileEntity tile) {
            return provider.getRecipeSourceKey(cast(tile));
        }

        private int getConfigurationRevision(TileEntity tile) {
            return provider.getConfigurationRevision(cast(tile));
        }

        private List<MachineRecipeRoute> getRecipeRoutes(TileEntity tile) {
            return provider.getRecipeRoutes(cast(tile));
        }

        private List<MachinePort> getPorts(TileEntity tile) {
            return provider.getPorts(cast(tile));
        }

        private MachinePresentationDescriptor getPresentation(TileEntity tile) {
            return provider.getPresentation(cast(tile));
        }

        private String getRecipeProfileScopeDiscriminator(TileEntity tile) {
            return provider.getRecipeProfileScopeDiscriminator(cast(tile));
        }

        private ProviderConformanceDescriptor getQIOConformance(TileEntity tile) {
            return provider.getQIOConformance(cast(tile));
        }
    }

    private static final class ClassDistance {

        private final Class<?> type;
        private final int distance;

        private ClassDistance(Class<?> type, int distance) {
            this.type = type;
            this.distance = distance;
        }
    }
}
