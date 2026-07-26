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
