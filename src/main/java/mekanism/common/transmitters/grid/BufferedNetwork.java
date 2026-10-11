package mekanism.common.transmitters.grid;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import mekanism.api.transmitters.DynamicNetwork;
import mekanism.api.transmitters.IGridTransmitter;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Server-thread save maintenance shared by buffered networks. Existing per-tile NBT keys are retained.
 */
public abstract class BufferedNetwork<A, N extends BufferedNetwork<A, N, B>, B> extends DynamicNetwork<A, N, B> {
    private final Map<IGridTransmitter<A, N, B>, Double> saveShares = new IdentityHashMap<>();
    private final LongOpenHashSet saveChunks = new LongOpenHashSet();
    private boolean sharesDirty = true;
    private boolean chunksDirty = true;
    private boolean splitting;
    private int chunkMemberCount = -1;
    private long lastDirtyTick = Long.MIN_VALUE;
    private double savedAmount = Double.NaN;
    private int maintenanceDepth;

    protected abstract double getSaveAmount();

    protected abstract boolean hasIntegerShares();

    /**
     * Coalesce repeated content mutations into one chunk-dirty pass per network per tick.
     */
    protected final void bufferChanged() {
        sharesDirty = true;
        markNetworkDirty();
    }

    private void topologyChanged() {
        sharesDirty = true;
        chunksDirty = true;
        lastDirtyTick = Long.MIN_VALUE;
    }

    private void markNetworkDirty() {
        if (maintenanceDepth != 0 || world == null || world.isRemote || transmitters.isEmpty()) return;
        long tick = world.getTotalWorldTime();
        if (chunksDirty || chunkMemberCount != transmitters.size()) {
            saveChunks.clear();
            for (IGridTransmitter<A, N, B> transmitter : transmitters) {
                mekanism.api.Coord4D coord = transmitter.coord();
                saveChunks.add(ChunkPos.asLong(coord.x >> 4, coord.z >> 4));
            }
            chunkMemberCount = transmitters.size();
            chunksDirty = false;
        } else if (tick == lastDirtyTick) return;
        lastDirtyTick = tick;
        for (long pos : saveChunks) {
            Chunk chunk = world.getChunkProvider().getLoadedChunk((int) pos, (int) (pos >> 32));
            if (chunk != null) chunk.markDirty();
        }
    }

    /**
     * Rebuild at an actual save/detach boundary, never once per transmitter tick.
     */
    public final void validateSaveShares() {
        if (splitting) return;
        double amount = getSaveAmount();
        if (!sharesDirty && saveShares.size() == transmitters.size() && Double.compare(savedAmount, amount) == 0)
            return;
        rebuildSaveShares();
    }

    private void rebuildSaveShares() {
        saveShares.clear();
        double remaining = Math.max(0, getSaveAmount());
        double capacityRemaining = 0;
        for (IGridTransmitter<A, N, B> transmitter : transmitters)
            capacityRemaining += Math.max(0, transmitter.getCapacity());
        for (IGridTransmitter<A, N, B> transmitter : transmitters) {
            double capacity = Math.max(0, transmitter.getCapacity());
            double share = capacityRemaining <= 0 ? 0 : Math.min(capacity, remaining * (capacity / capacityRemaining));
            if (hasIntegerShares()) share = Math.floor(share);
            // The last positive-capacity member owns the remainder, including floating-point roundoff.
            if (capacity > 0 && capacity == capacityRemaining) share = Math.min(capacity, remaining);
            saveShares.put(transmitter, share);
            remaining -= share;
            capacityRemaining -= capacity;
        }
        savedAmount = getSaveAmount();
        sharesDirty = false;
    }

    public final double getSaveShare(IGridTransmitter<A, N, B> transmitter) {
        validateSaveShares();
        return saveShares.getOrDefault(transmitter, 0D);
    }

    @Override
    public synchronized void updateCapacity() {
        super.updateCapacity();
        // Tier/configuration changes can redistribute capacities without changing member count.
        sharesDirty = true;
    }

    @Override
    public void commit() {
        boolean added = !transmittersToAdd.isEmpty();
        maintenanceDepth++;
        try {
            super.commit();
        } finally {
            maintenanceDepth--;
            if (added) {
                topologyChanged();
                markNetworkDirty();
            }
        }
    }

    @Override
    public void adoptTransmittersAndAcceptorsFrom(N net) {
        super.adoptTransmittersAndAcceptorsFrom(net);
        topologyChanged();
        markNetworkDirty();
    }

    @Override
    public boolean addTransmitter(IGridTransmitter<A, N, B> transmitter) {
        boolean added = super.addTransmitter(transmitter);
        if (added) {
            topologyChanged();
            updateCapacity();
        }
        return added;
    }

    @Override
    public boolean removeTransmitter(IGridTransmitter<A, N, B> transmitter) {
        boolean removed = super.removeTransmitter(transmitter);
        if (removed) {
            topologyChanged();
            updateCapacity();
            markNetworkDirty();
        }
        return removed;
    }

    @Override
    public void detachTransmitter(IGridTransmitter<A, N, B> transmitter) {
        if (!transmitters.contains(transmitter)) return;
        validateSaveShares();
        // Chunk NBT may already contain this allocation. Withdrawing one member must not
        // redistribute rounding remainders among the other members about to unload.
        boolean wasSplitting = splitting;
        splitting = true;
        maintenanceDepth++;
        try {
            transmitter.takeShare();
            removeTransmitter(transmitter);
            saveShares.remove(transmitter);
            savedAmount = getSaveAmount();
            sharesDirty = false;
        } finally {
            splitting = wasSplitting;
            maintenanceDepth--;
            markNetworkDirty();
        }
    }

    @Override
    public void invalidate() {
        // Freeze a single allocation before sequential withdrawals; a shrinking buffer must not
        // change later members' shares. Invalid/destroyed members do not receive a share.
        transmitters.removeIf(transmitter -> !transmitter.isValid());
        topologyChanged();
        updateCapacity();
        clampBuffer();
        rebuildSaveShares();
        splitting = true;
        maintenanceDepth++;
        try {
            super.invalidate();
        } finally {
            splitting = false;
            maintenanceDepth--;
        }
    }

    /**
     * Match upstream's render-scale epsilon, always publishing entry/exit at empty and full.
     */
    protected static boolean scaleChanged(float current, float previous) {
        if (current == previous) return false;
        return Math.abs(current - previous) >= 1.0E-5F || current == 0 || current == 1 || previous == 0 || previous == 1;
    }

    @Override
    public void deregister() {
        super.deregister();
        saveShares.clear();
        saveChunks.clear();
        topologyChanged();
    }
}
