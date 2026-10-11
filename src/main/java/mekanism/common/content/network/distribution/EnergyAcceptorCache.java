package mekanism.common.content.network.distribution;

import mekanism.api.Coord4D;
import mekanism.common.base.EnergyAcceptorWrapper;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Network-owned positive cache. Unknown providers and missing capabilities are resolved live.
 */
public final class EnergyAcceptorCache {
    private final Map<Coord4D, EnumMap<EnumFacing, Entry>> entries = new HashMap<>();

    @Nullable
    public EnergyAcceptorWrapper resolve(Coord4D pos, EnumFacing side, TileEntity tile) {
        EnumMap<EnumFacing, Entry> sides = entries.get(pos);
        if (sides != null) {
            Entry entry = sides.get(side);
            if (entry != null && entry.tile == tile && !tile.isInvalid() &&
                    tile instanceof TileEntityContainerBlock container &&
                    container.getManagedEnergyAcceptorToken(side, null) == entry.token) return entry.wrapper;
            sides.remove(side);
            if (sides.isEmpty()) entries.remove(pos);
        }
        if (tile == null || tile.isInvalid()) return null;
        EnergyAcceptorWrapper wrapper = EnergyAcceptorWrapper.get(tile, side);
        Object token = wrapper == null ? null : wrapper.managedCacheToken(tile, side);
        if (token != null) {
            entries.computeIfAbsent(pos, ignored -> new EnumMap<>(EnumFacing.class))
                    .put(side, new Entry(tile, wrapper, token));
        }
        return wrapper;
    }

    public void invalidate(Coord4D pos, EnumFacing side) {
        EnumMap<EnumFacing, Entry> sides = entries.get(pos);
        if (sides != null) {
            sides.remove(side);
            if (sides.isEmpty()) entries.remove(pos);
        }
    }

    public void retainConnections(Map<Coord4D, EnumSet<EnumFacing>> connections) {
        entries.entrySet().removeIf(entry -> {
            EnumSet<EnumFacing> sides = connections.get(entry.getKey());
            if (sides == null) return true;
            entry.getValue().keySet().retainAll(sides);
            return entry.getValue().isEmpty();
        });
    }

    public void clear() {
        entries.clear();
    }

    private static final class Entry {
        final TileEntity tile;
        final EnergyAcceptorWrapper wrapper;
        final Object token;

        Entry(TileEntity tile, EnergyAcceptorWrapper wrapper, Object token) {
            this.tile = tile;
            this.wrapper = wrapper;
            this.token = token;
        }
    }
}
