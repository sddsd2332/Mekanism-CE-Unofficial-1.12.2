package mekanism.common.tile.transmitter;

import cofh.redstoneflux.api.IEnergyProvider;
import cofh.redstoneflux.api.IEnergyReceiver;
import ic2.api.energy.EnergyNet;
import ic2.api.energy.tile.IEnergySource;
import ic2.api.energy.tile.IEnergyTile;
import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.TileNetworkList;
import mekanism.api.energy.*;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.base.EnergyAcceptorWrapper;
import mekanism.common.block.states.BlockStateTransmitter.TransmitterType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.CapabilityWrapperManager;
import mekanism.common.capabilities.energy.DynamicStrictEnergyHandler;
import mekanism.common.capabilities.holder.energy.ProxiedEnergyContainerHolder;
import mekanism.common.capabilities.resolver.manager.EnergyHandlerManager;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.integration.forgeenergy.ForgeEnergyCableIntegration;
import mekanism.common.integration.forgeenergy.ForgeEnergyIntegration;
import mekanism.common.integration.ic2.IC2Integration;
import mekanism.common.integration.redstoneflux.RFIntegration;
import mekanism.common.integration.tesla.TeslaCableIntegration;
import mekanism.common.integration.tesla.TeslaIntegration;
import mekanism.common.tier.AlloyTier;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.CableTier;
import mekanism.common.transmitters.grid.EnergyNetwork;
import mekanism.common.util.CableUtils;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.MekanismUtils;
import net.darkhax.tesla.api.ITeslaProducer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.common.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

@Optional.InterfaceList({
        @Optional.Interface(iface = "cofh.redstoneflux.api.IEnergyReceiver", modid = MekanismHooks.REDSTONEFLUX_MOD_ID),
        @Optional.Interface(iface = "cofh.redstoneflux.api.IEnergyProvider", modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
})
public class TileEntityUniversalCable extends TileEntityTransmitter<EnergyAcceptorWrapper, EnergyNetwork, EnergyStack> implements IStrictEnergyAcceptor,
        IStrictEnergyStorage, IStrictEnergyOutputter, IEnergyReceiver, IEnergyProvider {

    public CableTier tier = CableTier.BASIC;

    public double currentPower = 0;
    public double lastWrite = 0;

    private int nextTransfer = 0;

    public EnergyStack buffer = new EnergyStack(0);
    private CapabilityWrapperManager teslaManager = new CapabilityWrapperManager<>(getClass(), TeslaCableIntegration.class);
    private CapabilityWrapperManager forgeEnergyManager = new CapabilityWrapperManager<>(getClass(), ForgeEnergyCableIntegration.class);
    private final Map<EnumFacing, UniversalCableEnergyHandler> energyHandlers = new EnumMap<>(EnumFacing.class);
    private UniversalCableEnergyHandler readOnlyEnergyHandler;
    private final EnergyHandlerManager energyHandlerManager;

    public TileEntityUniversalCable() {
        addCapabilityResolver(energyHandlerManager = new EnergyHandlerManager(ProxiedEnergyContainerHolder.create(
              this::canInsertEnergyContainer, this::canExtractEnergyContainer, this::getEnergyContainers
        ), new DynamicStrictEnergyHandler(this::getEnergyContainers, this::canExtractEnergyContainer, this::canInsertEnergyContainer, null)));
    }

    @Override
    public BaseTier getBaseTier() {
        return tier.getBaseTier();
    }

    @Override
    public void setBaseTier(BaseTier baseTier) {
        tier = CableTier.get(baseTier);
    }

    @Override
    public void doRestrictedTick() {
        super.doRestrictedTick();
        doCableTick();
    }

    protected void doCableTick() {
        if (getWorld().isRemote) {
            double targetPower = getTransmitter().hasTransmitterNetwork() ? getTransmitter().getTransmitterNetwork().clientEnergyScale : 0;
            if (Math.abs(currentPower - targetPower) > 0.01) {
                currentPower = (9 * currentPower + targetPower) / 10;
            }
            return;
        }

        updateShare();

        if (nextTransfer > 0) {
            nextTransfer--;
            return;
        }

        List<EnumFacing> sides = getConnections(ConnectionType.PULL);
        if (sides.isEmpty()) {
            nextTransfer = 40;
            return;
        }

        boolean successAtLeastOnce = false;

        TileEntity[] connected = CableUtils.getConnectedTileEntities(sides, getPos(), getWorld());
        double maxDraw = tier.getCableCapacity();
        for (EnumFacing side : sides) {
            TileEntity outputter = connected[side.ordinal()];
            if (outputter == null) {
                continue;
            }

            //pre declare some variables for inline assignment & checks
            IStrictEnergyOutputter strictOutputter;
            ITeslaProducer teslaProducer;//do not assign anything to this here, or classloader issues may happen
            IEnergyStorage forgeStorage;
            EnumFacing accessSide = side.getOpposite();
            if ((strictOutputter = CapabilityUtils.getCapability(outputter, Capabilities.ENERGY_OUTPUTTER_CAPABILITY, accessSide)) != null &&
                strictOutputter.canOutputEnergy(accessSide)) {
                double received = draw(strictOutputter.pullEnergy(accessSide, maxDraw, true));
                if (received > 0) {
                    strictOutputter.pullEnergy(accessSide, received, false);
                    successAtLeastOnce = true;
                }
            } else if (MekanismUtils.useTesla() && (teslaProducer = CapabilityUtils.getCapability(outputter, Capabilities.TESLA_PRODUCER_CAPABILITY, accessSide)) != null) {
                double received = draw(TeslaIntegration.fromTesla(teslaProducer.takePower(TeslaIntegration.toTesla(maxDraw), true)));
                if (received > 0) {
                    teslaProducer.takePower(TeslaIntegration.toTesla(received), false);
                    successAtLeastOnce = true;
                }
            } else if (MekanismUtils.useForge() && (forgeStorage = CapabilityUtils.getCapability(outputter, CapabilityEnergy.ENERGY, accessSide)) != null) {
                double received = draw(ForgeEnergyIntegration.fromForge(forgeStorage.extractEnergy(ForgeEnergyIntegration.toForge(maxDraw), true)));
                if (received > 0) {
                    forgeStorage.extractEnergy(ForgeEnergyIntegration.toForge(received), false);
                    successAtLeastOnce = true;
                }
            } else if (MekanismUtils.useRF() && outputter instanceof IEnergyProvider rfProvider) {
                double received = draw(RFIntegration.fromRF(rfProvider.extractEnergy(side.getOpposite(), RFIntegration.toRF(maxDraw), true)));
                if (received > 0) {
                    rfProvider.extractEnergy(side.getOpposite(), RFIntegration.toRF(received), false);
                    successAtLeastOnce = true;
                }
            } else if (MekanismUtils.useIC2()) {
                IEnergyTile tile = EnergyNet.instance.getSubTile(outputter.getWorld(), outputter.getPos());
                if (tile instanceof IEnergySource source) {
                    double received = draw(Math.min(IC2Integration.fromEU(source.getOfferedEnergy()), maxDraw));
                    if (received > 0) {
                        source.drawEnergy(IC2Integration.toEU(received));
                        successAtLeastOnce = true;
                    }
                }
            }
        }

        if (!successAtLeastOnce) {
            nextTransfer = 10;
        }
    }

    /**
     * Takes a certain amount of energy and returns how much was actually taken
     *
     * @param toDraw Amount to take
     * @return Amount actually taken
     */
    private double draw(double toDraw) {
        if (toDraw > 0) {
            toDraw -= takeEnergy(toDraw, true);
        }
        return toDraw;
    }

    @Override
    public void updateShare() {
        if (getTransmitter().hasTransmitterNetwork() && getTransmitter().getTransmitterNetworkSize() > 0) {
            double last = getSaveShare();
            if (last != lastWrite) {
                lastWrite = last;
                markChunkDirty();
                //markDirty();
//                this.world.markChunkDirty(this.pos, this);
            }
        }
    }

    private double getSaveShare() {
        if (getTransmitter().hasTransmitterNetwork()) {
            EnergyNetwork transmitterNetwork = getTransmitter().getTransmitterNetwork();
            return EnergyNetwork.round(transmitterNetwork.getBufferAmount() * (1F / transmitterNetwork.transmittersSize()));
        }
        return buffer.amount;
    }

    @Override
    public TransmitterType getTransmitterType() {
        return TransmitterType.UNIVERSAL_CABLE;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (nbtTags.hasKey("tier")) {
            tier = MekanismUtils.getByIndex(CableTier.values(), nbtTags.getInteger("tier"), tier);
        }
        setLocalBufferAmount(nbtTags.getDouble("cacheEnergy"));
        lastWrite = buffer.amount;
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setDouble("cacheEnergy", lastWrite);
        nbtTags.setInteger("tier", tier.ordinal());
    }

    @Override
    public TransmissionType getTransmissionType() {
        return TransmissionType.ENERGY;
    }

    @Override
    public EnergyNetwork createNetworkByMerging(Collection<EnergyNetwork> networks) {
        return new EnergyNetwork(networks);
    }

    @Override
    public boolean isValidAcceptor(TileEntity acceptor, EnumFacing side) {
        return CableUtils.isValidAcceptorOnSide(MekanismUtils.getTileEntity(world, getPos()), acceptor, side);
    }

    @Override
    public EnergyNetwork createNewNetwork() {
        return new EnergyNetwork();
    }

    @Override
    public EnergyStack getBuffer() {
        return buffer;
    }

    @Override
    public void clearBuffer() {
        setLocalBufferAmount(0);
    }

    @Override
    public void takeShare() {
        if (getTransmitter().hasTransmitterNetwork()) {
            getTransmitter().getTransmitterNetwork().shrinkBuffer(lastWrite);
            setLocalBufferAmount(lastWrite);
        }
    }

    @Override
    @Optional.Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int receiveEnergy(EnumFacing from, int maxReceive, boolean simulate) {
        return RFIntegration.toRF(acceptEnergy(from, RFIntegration.fromRF(maxReceive), simulate));
    }

    @Override
    @Optional.Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int extractEnergy(EnumFacing from, int maxExtract, boolean simulate) {
        return RFIntegration.toRF(pullEnergy(from, RFIntegration.fromRF(maxExtract), simulate));
    }

    @Override
    @Optional.Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public boolean canConnectEnergy(EnumFacing from) {
        return hasEnergyContainer(from) && (canReceiveEnergy(from) || canOutputEnergy(from));
    }

    @Override
    @Optional.Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int getEnergyStored(EnumFacing from) {
        return RFIntegration.toRF(getEnergy());
    }

    @Override
    @Optional.Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int getMaxEnergyStored(EnumFacing from) {
        return RFIntegration.toRF(getMaxEnergy());
    }

    @Override
    public int getCapacity() {
        return tier.getCableCapacity();
    }

    @Override
    public double acceptEnergy(EnumFacing side, double amount, boolean simulate) {
        return amount - insertEnergyToBuffer(amount, side, Action.get(!simulate));
    }

    @Override
    public boolean canReceiveEnergy(EnumFacing side) {
        if (side == null) {
            return !isRedstoneActivated();
        }
        ConnectionType connectionType = getConnectionType(side);
        return !isRedstoneActivated() && (connectionType == ConnectionType.NORMAL || connectionType == ConnectionType.PULL);
    }

    @Override
    public double pullEnergy(EnumFacing side, double amount, boolean simulate) {
        return extractEnergyFromBuffer(amount, side, Action.get(!simulate));
    }

    @Override
    public boolean canOutputEnergy(EnumFacing side) {
        if (side == null) {
            return !isRedstoneActivated();
        }
        ConnectionType connectionType = getConnectionType(side);
        return !isRedstoneActivated() && (connectionType == ConnectionType.NORMAL || connectionType == ConnectionType.PUSH);
    }

    public double getMaxOutput() {
        return tier.getCableCapacity();
    }

    @Override
    public double getMaxEnergy() {
        if (getTransmitter().hasTransmitterNetwork()) {
            return getTransmitter().getTransmitterNetwork().getCapacityAsDouble();
        }
        return getCapacity();
    }

    @Override
    public double getEnergy() {
        if (getTransmitter().hasTransmitterNetwork()) {
            return getTransmitter().getTransmitterNetwork().getBufferAmount();
        }
        return buffer.amount;
    }

    @Override
    public void setEnergy(double energy) {
        if (getTransmitter().hasTransmitterNetwork()) {
            getTransmitter().getTransmitterNetwork().setBufferAmount(energy);
        } else {
            setLocalBufferAmount(energy);
        }
    }

    /**
     * @return Amount of left over energy
     */
    public double takeEnergy(double energy, boolean doEmit) {
        return insertEnergyToBuffer(energy, null, Action.get(doEmit));
    }

    private double insertEnergyToBuffer(double amount, @Nullable EnumFacing side, Action action) {
        double toUse = Math.min(getMaxEnergy() - getEnergy(), amount);
        if (toUse < 0.0001 || side != null && !canReceiveEnergy(side)) {
            return amount;
        }
        if (action.execute()) {
            if (getTransmitter().hasTransmitterNetwork()) {
                getTransmitter().getTransmitterNetwork().growBuffer(toUse);
            } else {
                setLocalBufferAmount(buffer.amount + toUse);
            }
        }
        return amount - toUse;
    }

    private double extractEnergyFromBuffer(double amount, @Nullable EnumFacing side, Action action) {
        double toGive = Math.min(Math.min(getEnergy(), amount), getMaxOutput());
        if (toGive < 0.0001 || side != null && !canOutputEnergy(side)) {
            return 0;
        }
        if (action.execute()) {
            if (getTransmitter().hasTransmitterNetwork()) {
                getTransmitter().getTransmitterNetwork().shrinkBuffer(toGive);
            } else {
                setLocalBufferAmount(buffer.amount - toGive);
            }
        }
        return toGive;
    }

    private void setLocalBufferAmount(double energy) {
        buffer.setAmountClamped(energy, getCapacity());
    }

    @Override
    public EnergyAcceptorWrapper getCachedAcceptor(EnumFacing side) {
        return EnergyAcceptorWrapper.get(getCachedTile(side), side.getOpposite());
    }

    @Override
    public boolean upgrade(AlloyTier tierOrdinal) {
        if (tier.ordinal() < BaseTier.ULTIMATE.ordinal() && tierOrdinal.ordinal() == tier.ordinal()) {
            tier = CableTier.values()[tier.ordinal() + 1];
            markDirtyTransmitters();
            sendDesc = true;
            return true;
        }
        return false;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) throws Exception {
        tier = MekanismUtils.getByIndex(CableTier.values(), dataStream.readInt(), tier);
        super.handlePacketData(dataStream);
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        data.add(tier.ordinal());
        super.getNetworkedData(data);
        return data;
    }

    @Override
    public void refreshConnections() {
        invalidateEnergyCapabilities(null);
        super.refreshConnections();
    }

    @Override
    public void refreshConnections(EnumFacing side) {
        invalidateEnergyCapabilities(side);
        super.refreshConnections(side);
    }

    @Override
    protected void onModeChange(EnumFacing side) {
        invalidateEnergyCapabilities(side);
        super.onModeChange(side);
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing facing) {
        return canResolveTesla(capability, facing) || capability == CapabilityEnergy.ENERGY && hasEnergyContainer(facing) ||
              super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing facing) {
        if (canResolveTesla(capability, facing)) {
            return (T) teslaManager.getWrapper(this, facing);
        } else if (capability == CapabilityEnergy.ENERGY) {
            return hasEnergyContainer(facing) ? (T) forgeEnergyManager.getWrapper(this, facing) : null;
        }
        return super.getCapability(capability, facing);
    }

    private boolean hasEnergyContainer(@Nullable EnumFacing side) {
        return !isRedstoneActivated() && (side == null || canConnect(side));
    }

    private boolean canResolveTesla(@Nonnull Capability<?> capability, @Nullable EnumFacing side) {
        return MekanismUtils.useTesla() && (capability == Capabilities.TESLA_HOLDER_CAPABILITY && hasEnergyContainer(side) ||
               capability == Capabilities.TESLA_CONSUMER_CAPABILITY && side != null && canReceiveEnergy(side) ||
               capability == Capabilities.TESLA_PRODUCER_CAPABILITY && side != null && canOutputEnergy(side));
    }

    private boolean canInsertEnergyContainer(@Nullable EnumFacing side) {
        return side == null || canReceiveEnergy(side);
    }

    private boolean canExtractEnergyContainer(@Nullable EnumFacing side) {
        return side == null || canOutputEnergy(side);
    }

    @Nonnull
    private List<IEnergyContainer> getEnergyContainers(@Nullable EnumFacing side) {
        return hasEnergyContainer(side) ? Collections.singletonList(getEnergyContainer(side)) : Collections.emptyList();
    }

    private UniversalCableEnergyHandler getEnergyContainer(@Nullable EnumFacing side) {
        if (side == null) {
            if (readOnlyEnergyHandler == null) {
                readOnlyEnergyHandler = new UniversalCableEnergyHandler(null);
            }
            return readOnlyEnergyHandler;
        }
        UniversalCableEnergyHandler handler = energyHandlers.get(side);
        if (handler == null) {
            handler = new UniversalCableEnergyHandler(side);
            energyHandlers.put(side, handler);
        }
        return handler;
    }

    private void invalidateEnergyCapabilities(@Nullable EnumFacing side) {
        invalidateCapability(Capabilities.STRICT_ENERGY_CAPABILITY, side);
        invalidateCapability(Capabilities.ENERGY_STORAGE_CAPABILITY, side);
        invalidateCapability(Capabilities.ENERGY_ACCEPTOR_CAPABILITY, side);
        invalidateCapability(Capabilities.ENERGY_OUTPUTTER_CAPABILITY, side);
    }

    private class UniversalCableEnergyHandler implements IEnergyContainer {

        @Nullable
        private final EnumFacing side;

        private UniversalCableEnergyHandler(@Nullable EnumFacing side) {
            this.side = side;
        }

        private EnumFacing sideFor(@Nullable EnumFacing requestedSide) {
            return side == null ? requestedSide : side;
        }

        @Override
        public double getEnergy() {
            return TileEntityUniversalCable.this.getEnergy();
        }

        @Override
        public void setEnergy(double energy) {
            TileEntityUniversalCable.this.setEnergy(energy);
        }

        @Override
        public double getMaxEnergy() {
            return TileEntityUniversalCable.this.getMaxEnergy();
        }

        @Override
        public double acceptEnergy(EnumFacing side, double amount, boolean simulate) {
            return TileEntityUniversalCable.this.acceptEnergy(sideFor(side), amount, simulate);
        }

        @Override
        public double insert(double amount, Action action, AutomationType automationType) {
            return TileEntityUniversalCable.this.insertEnergyToBuffer(amount, side, action);
        }

        @Override
        public double insert(double amount, @Nullable EnumFacing side, Action action, AutomationType automationType) {
            return TileEntityUniversalCable.this.insertEnergyToBuffer(amount, sideFor(side), action);
        }

        @Override
        public boolean canReceiveEnergy(EnumFacing side) {
            return TileEntityUniversalCable.this.canReceiveEnergy(sideFor(side));
        }

        @Override
        public double pullEnergy(EnumFacing side, double amount, boolean simulate) {
            return TileEntityUniversalCable.this.pullEnergy(sideFor(side), amount, simulate);
        }

        @Override
        public double extract(double amount, Action action, AutomationType automationType) {
            return TileEntityUniversalCable.this.extractEnergyFromBuffer(amount, side, action);
        }

        @Override
        public double extract(double amount, @Nullable EnumFacing side, Action action, AutomationType automationType) {
            return TileEntityUniversalCable.this.extractEnergyFromBuffer(amount, sideFor(side), action);
        }

        @Override
        public boolean canOutputEnergy(EnumFacing side) {
            return TileEntityUniversalCable.this.canOutputEnergy(sideFor(side));
        }
    }
}
