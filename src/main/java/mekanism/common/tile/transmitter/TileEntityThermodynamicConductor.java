package mekanism.common.tile.transmitter;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.api.NBTConstants;
import mekanism.api.TileNetworkList;
import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.IHeatHandler;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.transmitters.IGridTransmitter;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.ColourRGBA;
import mekanism.common.Mekanism;
import mekanism.common.block.states.BlockStateTransmitter.TransmitterType;
import mekanism.common.capabilities.heat.CachedAmbientTemperature;
import mekanism.common.capabilities.heat.ITileHeatHandler;
import mekanism.common.capabilities.heat.VariableHeatCapacitor;
import mekanism.common.capabilities.holder.heat.ProxiedHeatCapacitorHolder;
import mekanism.common.capabilities.resolver.manager.HeatHandlerManager;
import mekanism.common.tier.AlloyTier;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.ConductorTier;
import mekanism.common.transmitters.grid.HeatNetwork;
import mekanism.common.util.HeatCapabilityUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TileEntityThermodynamicConductor extends TileEntityTransmitter<IHeatHandler, HeatNetwork, Void> implements ITileHeatHandler {

    public ConductorTier tier = ConductorTier.BASIC;

    /** Relative-to-ambient temperature retained for the existing renderer. */
    public double temperature;
    private double clientTemperature = -1;
    private final CachedAmbientTemperature ambientTemperature = new CachedAmbientTemperature(this::getWorld, this::getPos);
    public final VariableHeatCapacitor buffer = VariableHeatCapacitor.create(tier.getHeatCapacity(), () -> tier.getInverseConduction(),
          () -> tier.getInverseConductionInsulation(), ambientTemperature, this);

    private final HeatHandlerManager heatHandlerManager = new HeatHandlerManager(this, ProxiedHeatCapacitorHolder.create(
          side -> true,
          side -> true,
          this::getConductorHeatTransfers
    ));

    @Override
    public BaseTier getBaseTier() {
        return tier.getBaseTier();
    }

    @Override
    public void setBaseTier(BaseTier baseTier) {
        tier = ConductorTier.get(baseTier);
        buffer.setHeatCapacity(tier.getHeatCapacity(), false);
    }

    @Override
    public HeatNetwork createNewNetwork() {
        return new HeatNetwork();
    }

    @Override
    public HeatNetwork createNetworkByMerging(Collection<HeatNetwork> networks) {
        return new HeatNetwork(networks);
    }

    @Override
    public int getCapacity() {
        return 0;
    }

    @Override
    public Void getBuffer() {
        return null;
    }

    @Override
    public void takeShare() {
    }

    @Override
    public void updateShare() {
    }

    @Override
    public TransmitterType getTransmitterType() {
        return TransmitterType.THERMODYNAMIC_CONDUCTOR;
    }

    @Override
    public boolean isValidAcceptor(TileEntity tile, EnumFacing side) {
        return HeatCapabilityUtils.hasHandler(tile, side.getOpposite());
    }

    @Override
    public TransmissionType getTransmissionType() {
        return TransmissionType.HEAT;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (nbtTags.hasKey("tier")) {
            tier = MekanismUtils.getByIndex(ConductorTier.values(), nbtTags.getInteger("tier"), tier);
        }
        buffer.setHeatCapacity(tier.getHeatCapacity(), false);
        if (nbtTags.hasKey(NBTConstants.HEAT_STORED, net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND)) {
            buffer.deserializeNBT(nbtTags.getCompoundTag(NBTConstants.HEAT_STORED));
            buffer.setHeatCapacity(tier.getHeatCapacity(), false);
        }
        updateRenderTemperature();
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setTag(NBTConstants.HEAT_STORED, buffer.serializeNBT());
        nbtTags.setInteger("tier", tier.ordinal());
    }

    public void sendTemp() {
        Mekanism.packetHandler.sendUpdatePacket(this);
    }

    @Override
    public IHeatHandler getCachedAcceptor(EnumFacing side) {
        TileEntity tile = getCachedTile(side);
        return HeatCapabilityUtils.getHandler(tile, side.getOpposite());
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) throws Exception {
        tier = MekanismUtils.getByIndex(ConductorTier.values(), dataStream.readInt(), tier);
        super.handlePacketData(dataStream);
        buffer.setHeatCapacityFromPacket(dataStream.readDouble());
        buffer.setHeat(dataStream.readDouble());
        updateRenderTemperature();
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        data.add(tier.ordinal());
        super.getNetworkedData(data);
        data.add(buffer.getHeatCapacity());
        data.add(buffer.getHeat());
        return data;
    }

    public ColourRGBA getBaseColour() {
        return tier.getBaseColour();
    }

    @Override
    public IHeatHandler getAdjacent(EnumFacing side) {
        if (connectionMapContainsSide(getAllCurrentConnections(), side)) {
            TileEntity adj = MekanismUtils.getTileEntity(world, getPos().offset(side));
            return HeatCapabilityUtils.getHandler(adj, side.getOpposite());
        }
        return null;
    }

    @Override
    public double getAmbientTemperature(EnumFacing side) {
        return ambientTemperature.getTemperature(side);
    }

    @Override
    public double simulateEnvironment(EnumFacing side) {
        // Connection modes only control external heat capabilities. The conductor's
        // physical surface still exchanges heat with the environment on every side.
        double heatCapacity = buffer.getHeatCapacity();
        if (!HeatAPI.isFinite(heatCapacity) || heatCapacity < 1) {
            return 0;
        }
        double invConduction = HeatAPI.AIR_INVERSE_COEFFICIENT + buffer.getInverseInsulation() + buffer.getInverseConduction();
        if (!HeatAPI.isFinite(invConduction) || invConduction <= 0) {
            invConduction = HeatAPI.MAX_HEAT;
        }
        double temperatureTransfer = (HeatAPI.sanitizeTemperature(buffer.getTemperature()) -
              HeatAPI.sanitizeTemperature(getAmbientTemperature(side))) / invConduction;
        if (!HeatAPI.isFinite(temperatureTransfer)) {
            return 0;
        }
        double heatToTransfer = HeatAPI.multiplyHeatSigned(temperatureTransfer, heatCapacity);
        if (HeatAPI.isFinite(heatToTransfer) && Math.abs(heatToTransfer) > HeatAPI.EPSILON) {
            double before = buffer.getHeat();
            buffer.handleHeat(-heatToTransfer);
            double after = buffer.getHeat();
            double actualHeat = heatToTransfer > 0 ? before - after : -(after - before);
            if (HeatAPI.isFinite(actualHeat)) {
                temperatureTransfer = actualHeat / heatCapacity;
            }
        }
        return temperatureTransfer > 0 ? temperatureTransfer : 0;
    }

    @Override
    public double incrementAdjacentTransfer(double currentAdjacentTransfer, double tempToTransfer, EnumFacing side) {
        TileEntity adjacent = MekanismUtils.getTileEntity(world, getPos().offset(side));
        if (!HeatAPI.isFinite(tempToTransfer) || tempToTransfer <= 0) {
            return currentAdjacentTransfer;
        }
        if (adjacent instanceof TileEntityThermodynamicConductor adjacentConductor) {
            // Transfers between conductors in the same network are still part of
            // that network's adjacent-flow statistic. Cross-network conductor
            // transfers are attributed to the network that owns the acceptor,
            // matching the 26.2 countsAsAdjacent rule.
            if (getTransmitter().hasTransmitterNetwork()) {
                HeatNetwork network = getTransmitter().getTransmitterNetwork();
                HeatNetwork adjacentNetwork = adjacentConductor.getTransmitter().hasTransmitterNetwork()
                      ? adjacentConductor.getTransmitter().getTransmitterNetwork() : null;
                if (!isSameNetworkForAdjacentTransfer(network, adjacentNetwork, Coord4D.get(adjacentConductor))) {
                    return currentAdjacentTransfer;
                }
            }
        }
        double current = HeatAPI.isFinite(currentAdjacentTransfer) ? Math.max(0, currentAdjacentTransfer) : 0;
        return tempToTransfer >= HeatAPI.MAX_HEAT - current ? HeatAPI.MAX_HEAT : current + tempToTransfer;
    }

    static boolean isSameNetworkForAdjacentTransfer(HeatNetwork network, @Nullable HeatNetwork adjacentNetwork, Coord4D adjacentCoord) {
        if (network == adjacentNetwork && adjacentNetwork != null) {
            return true;
        }
        // During a split or merge either pointer or member set may be transitional. Retain the old
        // coordinate lookup unless identity already proved the common, stable same-network case.
        for (IGridTransmitter<IHeatHandler, HeatNetwork, Void> transmitter : network.getTransmitters()) {
            if (transmitter != null && adjacentCoord.equals(transmitter.coord())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        return heatHandlerManager.canResolve(capability, side) || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (heatHandlerManager.canResolve(capability, side)) {
            return heatHandlerManager.resolve(capability, side);
        }
        return super.getCapability(capability, side);
    }

    @Nonnull
    private List<IHeatCapacitor> getConductorHeatTransfers(@Nullable EnumFacing side) {
        return isRedstoneActivated() || side != null && !canConnect(side) ? Collections.emptyList() : Collections.singletonList(buffer);
    }

    @Override
    public List<IHeatCapacitor> getHeatCapacitors(@Nullable EnumFacing side) {
        return getConductorHeatTransfers(side);
    }

    @Override
    public void onContentsChanged() {
        wakeNetwork();
        updateRenderTemperature();
        if (world != null && !world.isRemote) {
            markNoUpdateSync();
            double absoluteTemperature = buffer.getTemperature();
            if (clientTemperature < 0) {
                clientTemperature = ambientTemperature.getAsDouble();
            }
            if (Math.abs(absoluteTemperature - clientTemperature) > Math.max(HeatAPI.EPSILON, absoluteTemperature / 20)) {
                clientTemperature = absoluteTemperature;
                sendTemp();
            }
        }
    }

    @Override
    public void refreshConnections() {
        super.refreshConnections();
        wakeNetwork();
    }

    @Override
    public void refreshConnections(EnumFacing side) {
        super.refreshConnections(side);
        wakeNetwork();
    }

    private void wakeNetwork() {
        if (getTransmitter().hasTransmitterNetwork()) {
            getTransmitter().getTransmitterNetwork().wakeUp();
        }
    }

    private void updateRenderTemperature() {
        temperature = HeatAPI.sanitizeTemperature(buffer.getTemperature()) - HeatAPI.sanitizeTemperature(ambientTemperature.getAsDouble());
    }

    @Override
    public boolean upgrade(AlloyTier tierOrdinal) {
        if (tier.ordinal() < BaseTier.ULTIMATE.ordinal() && tierOrdinal.ordinal() == tier.ordinal()) {
            tier = ConductorTier.values()[tier.ordinal() + 1];
            buffer.setHeatCapacity(tier.getHeatCapacity(), false);
            markDirtyTransmitters();
            sendDesc = true;
            return true;
        }
        return false;
    }
}
