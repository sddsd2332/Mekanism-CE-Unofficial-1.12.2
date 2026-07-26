package mekanism.common.upgrade;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves external tier-upgrade adapters by tile class without modifying the target class.
 */
public final class TileUpgradeRegistry {

    private static final Map<ResourceLocation, Entry<?>> ENTRIES = new LinkedHashMap<>();
    private static final Map<Class<?>, Entry<?>> CACHE = new ConcurrentHashMap<>();
    private static final Entry<TileEntity> NO_ENTRY = new Entry<>(new ResourceLocation("mekanism", "missing"), TileEntity.class,
          new ITileUpgradeAdapter<TileEntity>() {
          }, Integer.MIN_VALUE);
    private static volatile List<Entry<?>> snapshot = Collections.emptyList();

    private TileUpgradeRegistry() {
    }

    public static <TILE extends TileEntity> void register(ResourceLocation id, Class<TILE> tileClass,
          ITileUpgradeAdapter<? super TILE> adapter) {
        register(id, tileClass, adapter, 0);
    }

    public static synchronized <TILE extends TileEntity> void register(ResourceLocation id, Class<TILE> tileClass,
          ITileUpgradeAdapter<? super TILE> adapter, int priority) {
        Objects.requireNonNull(id, "Adapter id cannot be null");
        if (ENTRIES.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate tile upgrade adapter id: " + id);
        }
        ENTRIES.put(id, new Entry<>(id, Objects.requireNonNull(tileClass, "Tile class cannot be null"),
              Objects.requireNonNull(adapter, "Adapter cannot be null"), priority));
        snapshot = Collections.unmodifiableList(new ArrayList<>(ENTRIES.values()));
        CACHE.clear();
    }

    public static synchronized boolean unregister(ResourceLocation id) {
        if (id == null || ENTRIES.remove(id) == null) {
            return false;
        }
        snapshot = Collections.unmodifiableList(new ArrayList<>(ENTRIES.values()));
        CACHE.clear();
        return true;
    }

    @Nullable
    public static BoundAdapter find(@Nullable TileEntity tile) {
        if (tile == null) {
            return null;
        }
        Entry<?> entry = CACHE.computeIfAbsent(tile.getClass(), TileUpgradeRegistry::resolve);
        return entry == NO_ENTRY ? null : new BoundAdapter(tile, entry);
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

    public static final class BoundAdapter {

        private final TileEntity tile;
        private final Entry<?> entry;

        private BoundAdapter(TileEntity tile, Entry<?> entry) {
            this.tile = tile;
            this.entry = entry;
        }

        public ResourceLocation id() {
            return entry.id;
        }

        public boolean canInstallUpgrade(mekanism.common.tier.BaseTier tier) {
            return entry.canInstallUpgrade(tile, tier);
        }

        @Nullable
        public net.minecraft.block.state.IBlockState getUpgradeResult(mekanism.common.tier.BaseTier tier) {
            return entry.getUpgradeResult(tile, tier);
        }

        public void prepareForUpgrade() {
            entry.prepareForUpgrade(tile);
        }

        @Nullable
        public IUpgradeData getUpgradeData(mekanism.common.tier.BaseTier tier) {
            return entry.getUpgradeData(tile, tier);
        }

        public boolean parseUpgradeData(IUpgradeData data) {
            return entry.parseUpgradeData(tile, data);
        }
    }

    private static final class Entry<TILE extends TileEntity> {

        private final ResourceLocation id;
        private final Class<TILE> tileClass;
        private final ITileUpgradeAdapter<? super TILE> adapter;
        private final int priority;

        private Entry(ResourceLocation id, Class<TILE> tileClass, ITileUpgradeAdapter<? super TILE> adapter, int priority) {
            this.id = id;
            this.tileClass = tileClass;
            this.adapter = adapter;
            this.priority = priority;
        }

        private TILE cast(TileEntity tile) {
            return tileClass.cast(tile);
        }

        private boolean canInstallUpgrade(TileEntity tile, mekanism.common.tier.BaseTier tier) {
            return adapter.canInstallUpgrade(cast(tile), tier);
        }

        private net.minecraft.block.state.IBlockState getUpgradeResult(TileEntity tile, mekanism.common.tier.BaseTier tier) {
            return adapter.getUpgradeResult(cast(tile), tier);
        }

        private void prepareForUpgrade(TileEntity tile) {
            adapter.prepareForUpgrade(cast(tile));
        }

        private IUpgradeData getUpgradeData(TileEntity tile, mekanism.common.tier.BaseTier tier) {
            return adapter.getUpgradeData(cast(tile), tier);
        }

        private boolean parseUpgradeData(TileEntity tile, IUpgradeData data) {
            return adapter.parseUpgradeData(cast(tile), data);
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
