package mekanism.common.tile.prefab;

import com.google.common.util.concurrent.AtomicDouble;
import ic2.api.energy.event.EnergyTileLoadEvent;
import ic2.api.energy.event.EnergyTileUnloadEvent;
import ic2.api.energy.tile.IEnergyAcceptor;
import ic2.api.energy.tile.IEnergyConductor;
import ic2.api.energy.tile.IEnergyEmitter;
import io.netty.buffer.ByteBuf;
import mekanism.api.Action;
import mekanism.api.IContentsListener;
import mekanism.api.TileNetworkList;
import mekanism.common.Mekanism;
import mekanism.common.base.IEnergyWrapper;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.CapabilityWrapperManager;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.api.heat.HeatAPI;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.energy.ProxiedEnergyContainerHolder;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.integration.forgeenergy.ForgeEnergyIntegration;
import mekanism.common.integration.ic2.IC2Integration;
import mekanism.common.integration.redstoneflux.RFIntegration;
import mekanism.common.integration.tesla.TeslaIntegration;
import mekanism.common.lib.LastEnergyTracker;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Optional.Method;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;

/**
 * 可以存储电力的方块类型
 */

public abstract class TileEntityElectricBlock extends TileEntityContainerBlock implements IEnergyWrapper {

    /**
     * How much energy is stored in this block.
     */
    public final AtomicDouble electricityStored = new AtomicDouble(0);

    /**
     * Maximum amount of energy this machine can hold.
     */
    public double BASE_MAX_ENERGY;

    /**
     * Actual maximum energy storage, including upgrades
     */
    public double maxEnergy;

    private boolean ic2Registered = false;
    private final CapabilityWrapperManager<IEnergyWrapper, TeslaIntegration> teslaManager = new CapabilityWrapperManager<>(IEnergyWrapper.class, TeslaIntegration.class);
    private final CapabilityWrapperManager<IEnergyWrapper, ForgeEnergyIntegration> forgeEnergyManager = new CapabilityWrapperManager<>(IEnergyWrapper.class, ForgeEnergyIntegration.class);
    @Nullable
    private MachineEnergyContainer mainEnergyContainer;
    private final LastEnergyTracker lastEnergyTracker = new LastEnergyTracker();

    /**
     * The base of all blocks that deal with electricity. It has a facing state, initialized state, and a current amount of stored energy.
     *
     * @param name          - full name of this block
     * @param baseMaxEnergy - how much energy this block can store
     */
    public TileEntityElectricBlock(String name, double baseMaxEnergy) {
        super(name);
        BASE_MAX_ENERGY = baseMaxEnergy;
        maxEnergy = BASE_MAX_ENERGY;
    }

    protected double getMainEnergyPerTick() {
        return 0;
    }

    public final MachineEnergyContainer getMainEnergyContainer() {
        return getMainEnergyContainer(this);
    }

    protected final MachineEnergyContainer getMainEnergyContainer(@Nullable IContentsListener listener) {
        if (mainEnergyContainer == null) {
            mainEnergyContainer = MachineEnergyContainer.create(this::getEnergy, this::setEnergy, this::getMaxEnergy, this::getMainEnergyPerTick,
                  automationType -> true, automationType -> true, listener);
        }
        return mainEnergyContainer;
    }

    @Override
    public void invalidateCapability(@Nullable Capability<?> capability, @Nullable EnumFacing side) {
        super.invalidateCapability(capability, side);
        if (capability == CapabilityEnergy.ENERGY) {
            forgeEnergyManager.invalidate(side);
        } else if (capability == Capabilities.TESLA_CONSUMER_CAPABILITY || capability == Capabilities.TESLA_PRODUCER_CAPABILITY ||
              capability == Capabilities.TESLA_HOLDER_CAPABILITY) {
            teslaManager.invalidate(side);
        } else if (capability == null) {
            forgeEnergyManager.invalidateAll();
            teslaManager.invalidateAll();
        }
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        if (this instanceof ISideConfiguration configurable && configurable.getConfig() != null) {
            EnergyContainerHelper builder = createEnergyContainerHelper();
            builder.addContainer(getMainEnergyContainer(listener));
            return builder.build();
        }
        return ProxiedEnergyContainerHolder.create(
              side -> side != null && sideIsConsumer(side),
              side -> side != null && sideIsOutput(side),
              side -> side == null || sideIsConsumer(side) || sideIsOutput(side) ? Collections.singletonList(getMainEnergyContainer(listener)) : Collections.emptyList());
    }

    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public void register() {
        if (!isRemote() && world != null && !ic2Registered) {
            MinecraftForge.EVENT_BUS.post(new EnergyTileLoadEvent(this));
            ic2Registered = true;
        }
    }

    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public void deregister() {
        if (!isRemote() && world != null && ic2Registered) {
            MinecraftForge.EVENT_BUS.post(new EnergyTileUnloadEvent(this));
            ic2Registered = false;
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (MekanismUtils.useIC2()) {
            register();
        }
    }


    @Override
    public void onAsyncUpdateServer(){
        super.onAsyncUpdateServer();
        Mekanism.EXECUTE_MANAGER.addSyncTask(this::addTileSyncTask);
    }


    public void addTileSyncTask(){
    }

    public void trackEnergyInputRate() {
        lastEnergyTracker.received(world == null ? 0 : world.getTotalWorldTime(), 0);
    }

    protected void trackEnergyInput(double amount, Action action, double remainder) {
        if (action.execute()) {
            lastEnergyTracker.received(world == null ? 0 : world.getTotalWorldTime(), amount - remainder);
        }
    }

    public double getInputRate() {
        return lastEnergyTracker.getLastEnergyReceived();
    }

    @Override
    public boolean sideIsOutput(EnumFacing side) {
        return false;
    }

    @Override
    public boolean sideIsConsumer(EnumFacing side) {
        return true;
    }

    @Override
    public double getMaxOutput() {
        return 0;
    }

    @Override
    public double getEnergy() {
        return electricityStored.get();
    }

    @Override
    public void setEnergy(double energy) {
        runContainerTransaction(() -> {
            double max = getMaxEnergy();
            double sanitized = HeatAPI.isFinite(energy) ? Math.max(0, Math.min(energy, max)) : 0;
            electricityStored.set(sanitized);
            MekanismUtils.saveChunk(this);
        });
    }

    @Override
    public double getMaxEnergy() {
        return HeatAPI.isFinite(maxEnergy) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, maxEnergy)) : 0;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            setEnergy(dataStream.readDouble());
            lastEnergyTracker.setLastEnergyReceived(dataStream.readDouble());
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(getEnergy());
        data.add(getInputRate());
        return data;
    }

    @Override
    public void onChunkUnload() {
        if (MekanismUtils.useIC2()) {
            deregister();
        }
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (MekanismUtils.useIC2()) {
            deregister();
        }
    }

    @Override
    public void validate() {
        boolean wasInvalid = this.tileEntityInvalid;//workaround for pending tile entity invalidate/revalidate cycle
        super.validate();
        if (wasInvalid && MekanismUtils.useIC2()) {//re-register if we got invalidated
            register();
        }
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        electricityStored.set(nbtTags.getDouble("electricityStored"));
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setDouble("electricityStored", getEnergy());
    }

    /**
     * Gets the scaled energy level for the GUI.
     *
     * @param i - multiplier
     * @return scaled energy
     */
    public int getScaledEnergyLevel(int i) {
        double maxEnergy = getMaxEnergy();
        return maxEnergy <= 0 ? 0 : (int) (getEnergy() * i / maxEnergy);
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int receiveEnergy(EnumFacing from, int maxReceive, boolean simulate) {
        return RFIntegration.toRF(acceptEnergy(from, RFIntegration.fromRF(maxReceive), simulate));
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int extractEnergy(EnumFacing from, int maxExtract, boolean simulate) {
        return RFIntegration.toRF(pullEnergy(from, RFIntegration.fromRF(maxExtract), simulate));
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public boolean canConnectEnergy(EnumFacing from) {
        return canInsertExternalEnergy(from) || canExtractExternalEnergy(from);
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int getEnergyStored(EnumFacing from) {
        return RFIntegration.toRF(getEnergy());
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int getMaxEnergyStored(EnumFacing from) {
        return RFIntegration.toRF(getMaxEnergy());
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public int getSinkTier() {
        return !MekanismConfig.current().general.blacklistIC2.val() ? IC2Integration.getConfiguredInputTier() : 0;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public int getSourceTier() {
        return !MekanismConfig.current().general.blacklistIC2.val() ? IC2Integration.getOutputTierForJoules(getMaxOutput()) : 0;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public int addEnergy(int amount) {
        if (!MekanismConfig.current().general.blacklistIC2.val()) {
            return tryCallContainerTransaction(() -> {
                setEnergy(getEnergy() + IC2Integration.fromEU(amount));
                return IC2Integration.toEUAsInt(getEnergy());
            }, () -> IC2Integration.toEUAsInt(getEnergy()));
        }
        return 0;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public boolean isTeleporterCompatible(EnumFacing side) {
        return !MekanismConfig.current().general.blacklistIC2.val() && sideIsOutput(side);
    }

    @Override
    public boolean canOutputEnergy(EnumFacing side) {
        return canExtractExternalEnergy(side);
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public boolean acceptsEnergyFrom(IEnergyEmitter emitter, EnumFacing direction) {
        return !MekanismConfig.current().general.blacklistIC2.val() && canInsertExternalEnergy(direction);
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public boolean emitsEnergyTo(IEnergyAcceptor receiver, EnumFacing direction) {
        return !MekanismConfig.current().general.blacklistIC2.val() && canExtractExternalEnergy(direction) && receiver instanceof IEnergyConductor;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public int getStored() {
        return IC2Integration.toEUAsInt(getEnergy());
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public void setStored(int energy) {
        if (!MekanismConfig.current().general.blacklistIC2.val()) {
            setEnergy(IC2Integration.fromEU(energy));
        }
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public int getCapacity() {
        return IC2Integration.toEUAsInt(getMaxEnergy());
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public int getOutput() {
        return IC2Integration.toEUAsInt(getMaxOutput());
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public double getDemandedEnergy() {
        return !MekanismConfig.current().general.blacklistIC2.val() ? IC2Integration.toEU((getMaxEnergy() - getEnergy())) : 0;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public double getOfferedEnergy() {
        return !MekanismConfig.current().general.blacklistIC2.val() ? IC2Integration.toEU(Math.min(getEnergy(), getMaxOutput())) : 0;
    }

    @Override
    public boolean canReceiveEnergy(EnumFacing side) {
        return canInsertExternalEnergy(side);
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public double getOutputEnergyUnitsPerTick() {
        return !MekanismConfig.current().general.blacklistIC2.val() ? IC2Integration.toEU(getMaxOutput()) : 0;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public double injectEnergy(EnumFacing pushDirection, double amount, double voltage) {
        // nb: the facing param contains the side relative to the pushing block
        TileEntity tile = MekanismUtils.getTileEntity(world, getPos().offset(pushDirection.getOpposite()));
        if (MekanismConfig.current().general.blacklistIC2.val() || CapabilityUtils.hasCapability(tile, Capabilities.GRID_TRANSMITTER_CAPABILITY, pushDirection)) {
            return amount;
        }
        return amount - IC2Integration.toEU(acceptEnergy(pushDirection.getOpposite(), IC2Integration.fromEU(amount), false));
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public void drawEnergy(double amount) {
        runContainerTransaction(() -> setEnergy(Math.max(getEnergy() - IC2Integration.fromEU(amount), 0)));
    }

    @Override
    public double acceptEnergy(EnumFacing side, double amount, boolean simulate) {
        return tryCallContainerTransaction(() -> {
            double toUse = Math.min(getMaxEnergy() - getEnergy(), amount);
            if (toUse < 0.0001 || (side != null && !canInsertExternalEnergy(side))) {
                return 0D;
            }
            if (!simulate) {
                setEnergy(getEnergy() + toUse);
                lastEnergyTracker.received(world == null ? 0 : world.getTotalWorldTime(), toUse);
            }
            return toUse;
        }, () -> 0D);
    }

    @Override
    public double insertEnergy(int container, double amount, @Nullable EnumFacing side, Action action) {
        return tryCallContainerTransaction(() -> {
            double remainder = super.insertEnergy(container, amount, side, action);
            trackEnergyInput(amount, action, remainder);
            return remainder;
        }, () -> amount);
    }

    @Override
    public double insertEnergy(double amount, @Nullable EnumFacing side, Action action) {
        return tryCallContainerTransaction(() -> {
            double remainder = super.insertEnergy(amount, side, action);
            trackEnergyInput(amount, action, remainder);
            return remainder;
        }, () -> amount);
    }

    @Override
    public double pullEnergy(EnumFacing side, double amount, boolean simulate) {
        return tryCallContainerTransaction(() -> {
            double toGive = Math.min(getEnergy(), amount);
            if (toGive < 0.0001 || (side != null && !canExtractExternalEnergy(side))) {
                return 0D;
            }
            if (!simulate) {
                setEnergy(getEnergy() - toGive);
            }
            return toGive;
        }, () -> 0D);
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        if (isStrictEnergy(capability)) {
            return super.hasCapability(capability, side) || !canHandleEnergy();
        }
        return capability == CapabilityEnergy.ENERGY || isTesla(capability, side) || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        } else if (isStrictEnergy(capability)) {
            T resolved = super.getCapability(capability, side);
            return resolved != null ? resolved : canHandleEnergy() ? null : (T) this;
        } else if (isTesla(capability, side)) {
            return (T) getTeslaEnergyWrapper(side);
        } else if (capability == CapabilityEnergy.ENERGY) {
            return CapabilityEnergy.ENERGY.cast(getForgeEnergyWrapper(side));
        }
        return super.getCapability(capability, side);
    }

    protected boolean isStrictEnergy(@Nonnull Capability<?> capability) {
        return capability == Capabilities.ENERGY_STORAGE_CAPABILITY || capability == Capabilities.ENERGY_ACCEPTOR_CAPABILITY || capability == Capabilities.ENERGY_OUTPUTTER_CAPABILITY;
    }

    protected boolean isTesla(@Nonnull Capability<?> capability, EnumFacing side) {
        return capability == Capabilities.TESLA_HOLDER_CAPABILITY || (capability == Capabilities.TESLA_CONSUMER_CAPABILITY && canInsertExternalEnergy(side))
                || (capability == Capabilities.TESLA_PRODUCER_CAPABILITY && canExtractExternalEnergy(side));
    }

    protected ForgeEnergyIntegration getForgeEnergyWrapper(EnumFacing side) {
        return forgeEnergyManager.getWrapper(this, side);
    }

    protected TeslaIntegration getTeslaEnergyWrapper(EnumFacing side) {
        return teslaManager.getWrapper(this, side);
    }

    protected boolean canInsertExternalEnergy(EnumFacing side) {
        return canHandleEnergy() ? canInsertEnergy(side) : sideIsConsumer(side);
    }

    protected boolean canExtractExternalEnergy(EnumFacing side) {
        return canHandleEnergy() ? canExtractEnergy(side) : sideIsOutput(side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isStrictEnergy(capability) && this instanceof ISideConfiguration) {
            return super.isCapabilityDisabled(capability, side);
        } else if (isStrictEnergy(capability) || capability == CapabilityEnergy.ENERGY || isTesla(capability, side)) {
            return side != null && !canInsertExternalEnergy(side) && !canExtractExternalEnergy(side);
        }
        return super.isCapabilityDisabled(capability, side);
    }
}
