package mekanism.generators.common.tile.turbine;

import ic2.api.energy.event.EnergyTileLoadEvent;
import ic2.api.energy.event.EnergyTileUnloadEvent;
import ic2.api.energy.tile.IEnergyAcceptor;
import ic2.api.energy.tile.IEnergyConductor;
import ic2.api.energy.tile.IEnergyEmitter;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.Coord4D;
import mekanism.api.IContentsListener;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.base.IEnergyWrapper;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.CapabilityWrapperManager;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.energy.ProxiedEnergyContainerHolder;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.fluid.ProxiedFluidTankHolder;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.integration.computer.IComputerIntegration;
import mekanism.common.integration.forgeenergy.ForgeEnergyIntegration;
import mekanism.common.integration.ic2.IC2Integration;
import mekanism.common.integration.redstoneflux.RFIntegration;
import mekanism.common.integration.tesla.TeslaIntegration;
import mekanism.common.util.CableUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.generators.common.content.turbine.TurbineFluidTank;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fml.common.Optional.Method;

import javax.annotation.Nonnull;
import java.util.Collections;

public class TileEntityTurbineValve extends TileEntityTurbineCasing implements IEnergyWrapper, IComputerIntegration, IComparatorSupport {

    private static final String[] methods = new String[]{"isFormed", "getSteam", "getFlowRate", "getMaxFlow",
            "getSteamInput"};
    public boolean ic2Registered = false;
    public TurbineFluidTank fluidTank;
    private CapabilityWrapperManager<IEnergyWrapper, TeslaIntegration> teslaManager = new CapabilityWrapperManager<>(IEnergyWrapper.class, TeslaIntegration.class);
    private CapabilityWrapperManager<IEnergyWrapper, ForgeEnergyIntegration> forgeEnergyManager = new CapabilityWrapperManager<>(IEnergyWrapper.class, ForgeEnergyIntegration.class);
    private int currentRedstoneLevel;

    public TileEntityTurbineValve() {
        super("TurbineValve");
        fluidTank = new TurbineFluidTank(this);
        initializeInventorySlots();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        return ProxiedFluidTankHolder.create(
              side -> isFormed(),
              side -> false,
              side -> isFormed() ? Collections.singletonList(fluidTank) : Collections.emptyList()
        );
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        return ProxiedEnergyContainerHolder.create(
              side -> false,
              side -> side != null && sideIsOutput(side),
              side -> structure == null ? Collections.emptyList() : structure.getEnergyContainers(side)
        );
    }

    private boolean isFormed() {
        return (!isRemote() && structure != null) || (isRemote() && clientHasStructure);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (MekanismUtils.useIC2() && !ic2Registered) {
            register();
        }
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        if (structure != null) {
            CableUtils.emit(this);
        }
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            updateComparatorOutputLevelSync();
            currentRedstoneLevel = newRedstoneLevel;
        }
    }

    @Override
    public boolean sideIsOutput(EnumFacing side) {
        if (structure != null) {
            return !structure.locations.contains(Coord4D.get(this).offset(side));
        }
        return false;
    }

    @Override
    public boolean sideIsConsumer(EnumFacing side) {
        return false;
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
    public double getMaxOutput() {
        return structure != null ? structure.getEnergyCapacity() : 0;
    }

    @Override
    public void onAdded() {
        super.onAdded();
        if (MekanismUtils.useIC2()) {
            register();
        }
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
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int receiveEnergy(EnumFacing from, int maxReceive, boolean simulate) {
        return 0;
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int extractEnergy(EnumFacing from, int maxExtract, boolean simulate) {
        if (sideIsOutput(from)) {
            double toSend = Math.min(getEnergy(), Math.min(getMaxOutput(), RFIntegration.fromRF(maxExtract)));
            double extracted = structure == null ? 0 : structure.extract(toSend, Action.get(!simulate), AutomationType.EXTERNAL);
            return RFIntegration.toRF(extracted);
        }
        return 0;
    }

    @Override
    @Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public boolean canConnectEnergy(EnumFacing from) {
        return structure != null;
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
        return IC2Integration.getConfiguredInputTier();
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public int getSourceTier() {
        return IC2Integration.getOutputTierForJoules(getMaxOutput());
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public int addEnergy(int amount) {
        return 0;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public boolean isTeleporterCompatible(EnumFacing side) {
        return canOutputEnergy(side);
    }

    @Override
    public boolean canOutputEnergy(EnumFacing side) {
        return sideIsOutput(side);
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public boolean acceptsEnergyFrom(IEnergyEmitter emitter, EnumFacing direction) {
        return false;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public boolean emitsEnergyTo(IEnergyAcceptor receiver, EnumFacing direction) {
        return sideIsOutput(direction) && receiver instanceof IEnergyConductor;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public int getStored() {
        return IC2Integration.toEUAsInt(getEnergy());
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public void setStored(int energy) {
        setEnergy(IC2Integration.fromEU(energy));
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
        return 0;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public double getOfferedEnergy() {
        return IC2Integration.toEU(Math.min(getEnergy(), getMaxOutput()));
    }

    @Override
    public boolean canReceiveEnergy(EnumFacing side) {
        return false;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public double getOutputEnergyUnitsPerTick() {
        return IC2Integration.toEU(getMaxOutput());
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public double injectEnergy(EnumFacing direction, double amount, double voltage) {
        return amount;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public void drawEnergy(double amount) {
        if (structure != null) {
            double toDraw = Math.min(IC2Integration.fromEU(amount), getMaxOutput());
            structure.extract(toDraw, Action.EXECUTE, AutomationType.EXTERNAL);
        }
    }

    @Override
    public double acceptEnergy(EnumFacing side, double amount, boolean simulate) {
        return 0;
    }

    @Override
    public double pullEnergy(EnumFacing side, double amount, boolean simulate) {
        double toGive = Math.min(getEnergy(), amount);
        if (toGive < 0.0001 || (side != null && !sideIsOutput(side))) {
            return 0;
        }
        return structure == null ? 0 : structure.extract(toGive, Action.get(!simulate), AutomationType.EXTERNAL);
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize("gui.industrialTurbine");
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        if (method == 0) {
            return new Object[]{structure != null};
        } else {
            if (structure == null) {
                return new Object[]{"Unformed"};
            }
            switch (method) {
                case 1 -> {
                    return new Object[]{structure.fluidStored != null ? structure.fluidStored.amount : 0};
                }
                case 2 -> {
                    return new Object[]{structure.clientFlow};
                }
                case 3 -> {
                    double rate = structure.lowerVolume * (structure.clientDispersers * MekanismConfig.current().generators.turbineDisperserGasFlow.val());
                    rate = Math.min(rate, structure.vents * MekanismConfig.current().generators.turbineVentGasFlow.val());
                    return new Object[]{rate};
                }
                case 4 -> {
                    return new Object[]{structure.lastSteamInput};
                }
            }
        }
        throw new NoSuchMethodException();
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isFormed()) {
            if (capability == Capabilities.TESLA_HOLDER_CAPABILITY
                    || (capability == Capabilities.TESLA_PRODUCER_CAPABILITY && sideIsOutput(side)) || capability == CapabilityEnergy.ENERGY) {
                return true;
            }
        }
        return super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isFormed()) {
            if (capability == Capabilities.TESLA_HOLDER_CAPABILITY || (capability == Capabilities.TESLA_PRODUCER_CAPABILITY && sideIsOutput(side))) {
                return (T) teslaManager.getWrapper(this, facing);
            } else if (capability == CapabilityEnergy.ENERGY) {
                return CapabilityEnergy.ENERGY.cast(forgeEnergyManager.getWrapper(this, facing));
            }
        }
        return super.getCapability(capability, side);
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(fluidTank.getFluidAmount(), fluidTank.getCapacity());
    }
}
