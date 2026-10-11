package mekanism.common.transmitters.grid;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import mekanism.api.Coord4D;
import mekanism.api.energy.EnergyStack;
import mekanism.api.transmitters.IGridTransmitter;
import mekanism.common.base.EnergyAcceptorWrapper;
import mekanism.common.content.network.distribution.EnergyAcceptorCache;
import mekanism.common.content.network.distribution.EnergyAcceptorTarget;
import mekanism.common.util.EmitUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.Event;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

public class EnergyNetwork extends BufferedNetwork<EnergyAcceptorWrapper, EnergyNetwork, EnergyStack> {

    public double clientEnergyScale = 0;
    public EnergyStack buffer = new EnergyStack(0);
    private double lastPowerScale = 0;
    private double joulesTransmitted = 0;
    private double jouleBufferLastTick = 0;

    private EnergyAcceptorTarget target;
    private EnergyAcceptorTarget reusableTarget;
    private final EnergyAcceptorCache acceptorCache = new EnergyAcceptorCache();
    @Nullable
    private AcceptorConnection[] targetConnections;
    // Only valid during one collection pass. Never retain a Chunk across ticks/unloads.
    private final Long2ObjectOpenHashMap<Chunk> collectionChunks = new Long2ObjectOpenHashMap<>();

    protected final void invalidateAcceptorConnections() {
        targetConnections = null;
    }

    @Override
    protected double getSaveAmount() {
        return getBufferAmount();
    }

    @Override
    protected boolean hasIntegerShares() {
        return false;
    }

    public EnergyNetwork() {
    }

    public EnergyNetwork(Collection<EnergyNetwork> networks) {
        networks.forEach(net -> {
            if (net != null) {
                adoptTransmittersAndAcceptorsFrom(net);
                net.deregister();
            }
        });
        register();
    }

    @Override
    public void adoptTransmittersAndAcceptorsFrom(EnergyNetwork net) {
        invalidateAcceptorConnections();
        super.adoptTransmittersAndAcceptorsFrom(net);
        if (net.jouleBufferLastTick > jouleBufferLastTick || net.clientEnergyScale > clientEnergyScale) {
            clientEnergyScale = net.clientEnergyScale;
            jouleBufferLastTick = net.jouleBufferLastTick;
            joulesTransmitted = net.joulesTransmitted;
            lastPowerScale = net.lastPowerScale;
        }
        growBuffer(net.buffer.amount);
        net.setBufferAmount(0);
        // Rebuild credentials in the resulting network; never transplant stale ownership.
        acceptorCache.clear();
    }

    @Override
    public void acceptorChanged(IGridTransmitter<EnergyAcceptorWrapper, EnergyNetwork, EnergyStack> transmitter, EnumFacing side) {
        invalidateAcceptorConnections();
        acceptorCache.invalidate(transmitter.coord().offset(side), side.getOpposite());
        super.acceptorChanged(transmitter, side);
    }

    @Override
    public void updateTransmitterOnSide(IGridTransmitter<EnergyAcceptorWrapper, EnergyNetwork, EnergyStack> transmitter, EnumFacing side) {
        invalidateAcceptorConnections();
        acceptorCache.invalidate(transmitter.coord().offset(side), side.getOpposite());
        super.updateTransmitterOnSide(transmitter, side);
    }

    @Override
    public void commit() {
        invalidateAcceptorConnections();
        super.commit();
        acceptorCache.retainConnections(acceptorDirections);
    }

    @Override
    public boolean removeTransmitter(IGridTransmitter<EnergyAcceptorWrapper, EnergyNetwork, EnergyStack> transmitter) {
        invalidateAcceptorConnections();
        acceptorCache.clear();
        return super.removeTransmitter(transmitter);
    }

    @Override
    public void deregister() {
        invalidateAcceptorConnections();
        collectionChunks.clear();
        acceptorCache.clear();
        target = null;
        if (reusableTarget != null) reusableTarget.reset();
        super.deregister();
    }

    public static double round(double d) {
        return Math.round(d * 10000) / 10000;
    }

    @Nullable
    @Override
    public EnergyStack getBuffer() {
        return buffer;
    }

    @Override
    public void absorbBuffer(IGridTransmitter<EnergyAcceptorWrapper, EnergyNetwork, EnergyStack> transmitter) {
        EnergyStack energy = transmitter.getBuffer();
        growBuffer(energy.amount);
        transmitter.clearBuffer();
    }

    @Override
    public void clampBuffer() {
        setBufferAmount(getCapacityAsDouble() <= 0 ? 0 : buffer.amount);
    }

    public double getEnergyNeeded() {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            return 0;
        }
        return getCapacityAsDouble() - getBufferAmount();
    }

    public double getBufferAmount() {
        return buffer.amount;
    }

    public double emit(double energyToSend, boolean doEmit) {
        double toUse = Math.min(getEnergyNeeded(), energyToSend);
        if (doEmit) {
            growBuffer(toUse);
        }
        return energyToSend - toUse;
    }

    public void setBufferAmount(double amount) {
        double capacity = getCapacityAsDouble();
        double previous = buffer.amount;
        buffer.setAmountClamped(amount, capacity);
        if (Double.compare(previous, buffer.amount) != 0) bufferChanged();
    }

    public double growBuffer(double amount) {
        if (amount <= 0) {
            return 0;
        }
        double current = buffer.amount;
        setBufferAmount(current + amount);
        return buffer.amount - current;
    }

    public double shrinkBuffer(double amount) {
        if (amount <= 0) {
            return 0;
        }
        double current = buffer.amount;
        setBufferAmount(current - amount);
        return current - buffer.amount;
    }

    @Override
    public String toString() {
        return "[EnergyNetwork] " + transmitters.size() + " transmitters, " + possibleAcceptors.size() + " acceptors.";
    }

    @Override
    public void preTick() {
        super.onUpdate();
        clearJoulesTransmitted();
        // An idle network need not keep unloaded neighbors alive. The next powered pass
        // admits fresh credentials; this is not a timeout for using an old capability.
        if (buffer.amount <= 0) acceptorCache.clear();

        if (FMLCommonHandler.instance().getEffectiveSide() != null && FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            double currentPowerScale = getPowerScale();
            if (Math.abs(currentPowerScale - lastPowerScale) > 0.01 || (currentPowerScale != lastPowerScale && (currentPowerScale == 0 || currentPowerScale == 1))) {
                needsUpdate = true;
            }
            if (needsUpdate) {
                MinecraftForge.EVENT_BUS.post(new EnergyTransferEvent(this, currentPowerScale));
                lastPowerScale = currentPowerScale;
                needsUpdate = false;
            }
        }
    }

    @Override
    public void onParallelTick() {
        // World/capability work belongs beside distribution on the server thread.
    }

    @Override
    public void onUpdate() {
        if (FMLCommonHandler.instance().getEffectiveSide() != null && FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            if (buffer.amount > 0) {
                try {
                    collectTargets();
                    joulesTransmitted = tickEmit(buffer.amount);
                    shrinkBuffer(joulesTransmitted);
                } finally {
                    if (reusableTarget != null) reusableTarget.reset();
                    target = null;
                }
            }
        }
    }

    private double tickEmit(double energyToSend) {
        EnergyAcceptorTarget target = this.target;
        return target == null || target.getHandlerCount() == 0 ? 0 : EmitUtils.sendToAcceptors(target, energyToSend);
    }

    private void collectTargets() {
        target = null;
        EnergyAcceptorTarget collectedTarget = reusableTarget;
        if (collectedTarget == null) {
            collectedTarget = reusableTarget = new EnergyAcceptorTarget(possibleAcceptors.size() * 2);
        } else {
            collectedTarget.reset();
        }
        AcceptorConnection[] connections = targetConnections;
        if (connections == null) {
            List<AcceptorConnection> rebuilt = new ArrayList<>(possibleAcceptors.size());
            // Preserve the existing acceptor and side order used by the distributor.
            for (Coord4D coord : possibleAcceptors) {
                EnumSet<EnumFacing> sides = acceptorDirections.get(coord);
                if (sides != null && !sides.isEmpty()) rebuilt.add(new AcceptorConnection(coord, sides));
            }
            targetConnections = connections = rebuilt.toArray(new AcceptorConnection[0]);
        }
        IChunkProvider provider = getWorld().getChunkProvider();
        try {
            for (AcceptorConnection connection : connections) {
                Chunk chunk = collectionChunks.get(connection.chunkKey);
                if (chunk == null && !collectionChunks.containsKey(connection.chunkKey) || chunk != null && !chunk.isLoaded()) {
                    chunk = provider.getLoadedChunk(connection.chunkX, connection.chunkZ);
                    collectionChunks.put(connection.chunkKey, chunk);
                }
                // Identity stays live: replacing a tile without a neighbor notification must
                // never leave the old cached capability in use. CHECK cannot create a tile.
                TileEntity tile = chunk == null ? null : chunk.getTileEntity(connection.pos, Chunk.EnumCreateEntityType.CHECK);
                if (tile == null) {
                    for (EnumFacing side : connection.sides) acceptorCache.invalidate(connection.coord, side);
                    continue;
                }
                for (EnumFacing side : connection.sides) {
                    EnergyAcceptorWrapper acceptor = acceptorCache.resolve(connection.coord, side, tile);
                    // Demand and side permission remain live, including unknown third-party providers.
                    if (acceptor != null && acceptor.canReceiveEnergy(side)) {
                        collectedTarget.addHandler(side, acceptor);
                    }
                }
            }
        } finally {
            collectionChunks.clear();
        }
        target = collectedTarget;
    }

    private static final class AcceptorConnection {
        final Coord4D coord;
        final BlockPos pos;
        final EnumFacing[] sides;
        final int chunkX, chunkZ;
        final long chunkKey;

        AcceptorConnection(Coord4D coord, EnumSet<EnumFacing> sides) {
            this.coord = new Coord4D(coord.x, coord.y, coord.z, coord.dimensionId);
            pos = this.coord.getPos();
            this.sides = sides.toArray(new EnumFacing[0]);
            chunkX = coord.x >> 4;
            chunkZ = coord.z >> 4;
            chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        }
    }

    public double getPowerScale() {
        return Math.max(jouleBufferLastTick == 0 ? 0 : Math.min(Math.ceil(Math.log10(getPower()) * 2) / 10, 1), getCapacityAsDouble() == 0 ? 0 : buffer.amount / getCapacityAsDouble());
    }

    public void clearJoulesTransmitted() {
        jouleBufferLastTick = buffer.amount;
        joulesTransmitted = 0;
    }

    public double getPower() {
        return jouleBufferLastTick * 20;
    }

    @Override
    public String getNeededInfo() {
        return MekanismUtils.getEnergyDisplay(getEnergyNeeded());
    }

    @Override
    public String getStoredInfo() {
        return MekanismUtils.getEnergyDisplay(buffer.amount);
    }

    @Override
    public String getFlowInfo() {
        return MekanismUtils.getEnergyDisplay(joulesTransmitted) + "/t";
    }

    public static class EnergyTransferEvent extends Event {

        public final EnergyNetwork energyNetwork;

        public final double power;

        public EnergyTransferEvent(EnergyNetwork network, double currentPower) {
            energyNetwork = network;
            power = currentPower;
        }
    }
}
