package mekanism.common.tile.multiblock;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IStrictEnergyAcceptor;
import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.Mekanism;
import mekanism.common.base.IActiveState;
import mekanism.common.capabilities.CapabilityWrapperManager;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.energy.ProxiedEnergyContainerHolder;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.gas.ProxiedGasTankHolder;
import mekanism.common.content.sps.SynchronizedSPSData;
import mekanism.common.integration.forgeenergy.ForgeEnergyIntegration;
import mekanism.common.util.GasUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class TileEntitySPSPort extends TileEntitySPSCasing implements IConfigurable, IActiveState, IStrictEnergyStorage, IStrictEnergyAcceptor, IEnergyContainer {

    private static final double MAX_PORT_ENERGY = SynchronizedSPSData.ENERGY_PER_INPUT * SynchronizedSPSData.INPUT_CAPACITY;

    private boolean outputMode;
    private double energy;
    private final CapabilityWrapperManager<TileEntitySPSPort, SPSPortForgeEnergyStorage> forgeEnergyManager = new CapabilityWrapperManager<>(TileEntitySPSPort.class, SPSPortForgeEnergyStorage.class);

    public TileEntitySPSPort() {
        super("SpsPort");
        initializeInventorySlots();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        return ProxiedGasTankHolder.create(
              side -> isFormed() && !outputMode,
              side -> isFormed() && outputMode,
              this::getSPSGasTanks
        );
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        return ProxiedEnergyContainerHolder.create(
              side -> side != null,
              side -> false,
              side -> Collections.singletonList(this)
        );
    }

    private List<IExtendedGasTank> getSPSGasTanks(EnumFacing side) {
        if (!isFormed()) {
            return Collections.emptyList();
        }
        return outputMode ? Collections.singletonList(structure.outputTank) : Collections.singletonList(structure.inputTank);
    }

    private boolean isFormed() {
        return structure != null && structure.isFormed();
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        if (structure != null && outputMode) {
            GasUtils.emit(java.util.EnumSet.allOf(EnumFacing.class), structure.outputTank, this, 16);
        }
        if (structure != null && energy > 0) {
            Coord4D portPos = Coord4D.get(this);
            if (structure.canSupplyPortEnergy(portPos)) {
                double toSupply = energy;
                structure.addEnergy(portPos, toSupply);
                extract(toSupply, Action.EXECUTE, AutomationType.INTERNAL);
            }
        }
    }

    @Override
    public double getEnergy() {
        return energy;
    }

    @Override
    public void setEnergy(double energy) {
        double clamped = Math.max(0, Math.min(energy, getMaxEnergy()));
        if (this.energy != clamped) {
            this.energy = clamped;
            markNoUpdateSync();
        }
    }

    @Override
    public double getMaxEnergy() {
        return MAX_PORT_ENERGY;
    }

    @Override
    public double acceptEnergy(EnumFacing side, double amount, boolean simulate) {
        if (amount <= 0 || !canReceiveEnergy(side)) {
            return 0;
        }
        return amount - insert(amount, Action.get(!simulate), AutomationType.handler(side));
    }

    @Override
    public boolean canReceiveEnergy(EnumFacing side) {
        return true;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == CapabilityEnergy.ENERGY) {
            return true;
        }
        return super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (capability == CapabilityEnergy.ENERGY) {
            return CapabilityEnergy.ENERGY.cast(forgeEnergyManager.getWrapper(this, side));
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        outputMode = nbtTags.getBoolean("spsOutputMode");
        energy = nbtTags.getDouble("spsPortEnergy");
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setBoolean("spsOutputMode", outputMode);
        nbtTags.setDouble("spsPortEnergy", energy);
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(outputMode);
        return data;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            boolean prevMode = outputMode;
            outputMode = dataStream.readBoolean();
            if (prevMode != outputMode) {
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    @Override
    public EnumActionResult onSneakRightClick(EntityPlayer player, EnumFacing side) {
        if (!isRemote()) {
            boolean oldMode = outputMode;
            setActive(!oldMode);
            String mode = outputMode ? LangUtils.localize("gui.output") : LangUtils.localize("gui.input");
            player.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + " " + EnumColor.GREY +
                  LangUtils.localize("tooltip.configurator.reactorPortEject") + " " + EnumColor.AQUA + mode));
            Mekanism.packetHandler.sendUpdatePacket(this);
            markNoUpdateSync();
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public EnumActionResult onRightClick(EntityPlayer player, EnumFacing side) {
        return EnumActionResult.PASS;
    }

    @Override
    public boolean getActive() {
        return outputMode;
    }

    @Override
    public void setActive(boolean active) {
        outputMode = active;
    }

    @Override
    public boolean renderUpdate() {
        return true;
    }

    @Override
    public boolean lightUpdate() {
        return false;
    }

    public static class SPSPortForgeEnergyStorage implements IEnergyStorage {

        private final TileEntitySPSPort tile;
        private final EnumFacing side;

        public SPSPortForgeEnergyStorage(TileEntitySPSPort tile, EnumFacing side) {
            this.tile = tile;
            this.side = side;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return ForgeEnergyIntegration.toForge(tile.acceptEnergy(side, ForgeEnergyIntegration.fromForge(maxReceive), simulate));
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return ForgeEnergyIntegration.toForge(tile.getEnergy());
        }

        @Override
        public int getMaxEnergyStored() {
            return ForgeEnergyIntegration.toForge(tile.getMaxEnergy());
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return tile.canReceiveEnergy(side);
        }
    }
}
