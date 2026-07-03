package mekanism.generators.common.tile.reactor;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.Mekanism;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.energy.ProxiedEnergyContainerHolder;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.fluid.ProxiedFluidTankHolder;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.gas.ProxiedGasTankHolder;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.common.capabilities.holder.heat.ProxiedHeatCapacitorHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.ProxiedInventorySlotHolder;
import mekanism.common.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class TileEntityReactorPort extends TileEntityReactorBlock implements IHeatTransfer, IConfigurable {

    public boolean fluidEject;

    public TileEntityReactorPort() {
        super("name", 1);
        initializeInventorySlots();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        return ProxiedInventorySlotHolder.create(
              side -> getReactor() != null && getReactor().isFormed(),
              side -> getReactor() != null && getReactor().isFormed(),
              side -> {
                  IInventorySlot slot = getReactorInventorySlot(0);
                  return slot == null ? Collections.emptyList() : Collections.singletonList(slot);
              }
        );
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        return ProxiedFluidTankHolder.create(
              side -> getReactor() != null && getReactor().isFormed() && !fluidEject,
              side -> getReactor() != null && getReactor().isFormed(),
              this::getReactorFluidTanks,
              this::getReactorFluidTanksForInsert,
              this::getReactorFluidTanksForExtract
        );
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        return ProxiedGasTankHolder.create(
              side -> getReactor() != null && getReactor().isFormed(),
              side -> false,
              this::getReactorGasTanks
        );
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        return ProxiedEnergyContainerHolder.create(
              side -> false,
              side -> getReactor() != null && getReactor().isFormed(),
              side -> getReactor() != null && getReactor().isFormed() ? Collections.singletonList(this) : Collections.emptyList()
        );
    }

    private List<IExtendedFluidTank> getReactorFluidTanks(EnumFacing side) {
        return getReactor() == null ? Collections.emptyList() : Arrays.asList(getReactor().getWaterTank(), getReactor().getSteamTank());
    }

    private List<IExtendedFluidTank> getReactorFluidTanksForInsert(EnumFacing side) {
        return getReactor() == null ? Collections.emptyList() : Collections.singletonList(getReactor().getWaterTank());
    }

    private List<IExtendedFluidTank> getReactorFluidTanksForExtract(EnumFacing side) {
        return getReactor() == null ? Collections.emptyList() : Collections.singletonList(getReactor().getSteamTank());
    }

    private List<IExtendedGasTank> getReactorGasTanks(EnumFacing side) {
        return getReactor() == null ? Collections.emptyList() :
              Arrays.asList(getReactor().getDeuteriumTank(), getReactor().getTritiumTank(), getReactor().getFuelTank());
    }

    @Override
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener) {
        return ProxiedHeatCapacitorHolder.create(
              side -> getReactor() != null && getReactor().isFormed(),
              side -> getReactor() != null && getReactor().isFormed(),
              side -> getReactor() == null ? Collections.emptyList() : Collections.singletonList(this)
        );
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        fluidEject = nbtTags.getBoolean("fluidEject");
    }


    @Override
   public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setBoolean("fluidEject", fluidEject);
    }

    @Override
    public boolean isFrame() {
        return false;
    }

    @Override
    public void onUpdate() {
        if (changed) {
            world.notifyNeighborsOfStateChange(getPos(), getBlockType(), true);
        }
        super.onUpdate();
    }

    @Override
    public void onUpdateServer(){
        super.onUpdateServer();
        CableUtils.emit(this);
        if (fluidEject && getReactor() != null && getReactor().getSteamTank().getFluid() != null) {
            IFluidTank tank = getReactor().getSteamTank();
            EmitUtils.forEachSide(getWorld(), getPos(), EnumSet.allOf(EnumFacing.class), (tile, side) -> {
                if (!(tile instanceof TileEntityReactorPort)) {
                    IFluidHandler handler = CapabilityUtils.getCapability(tile, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side.getOpposite());
                    if (handler != null && PipeUtils.canFill(handler, tank.getFluid())) {
                        tank.drain(handler.fill(tank.getFluid(), true), true);
                    }
                }
            });
        }
    }




    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        return capability == Capabilities.CONFIGURABLE_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        }
        if (capability == Capabilities.CONFIGURABLE_CAPABILITY) {
            return (T) this;
        }
        return super.getCapability(capability, side);
    }

    @Override
    public boolean canOutputEnergy(EnumFacing side) {
        return true;
    }

    @Override
    public double getEnergy() {
        if (getReactor() == null) {
            return 0;
        }
        return getReactor().getBufferedEnergy();
    }

    @Override
    public void setEnergy(double energy) {
        if (getReactor() != null) {
            getReactor().setBufferedEnergy(energy);
        }
    }

    @Override
    public double getMaxEnergy() {
        if (getReactor() == null) {
            return 0;
        }
        return getReactor().getBufferSize();
    }

    @Override
    public boolean sideIsOutput(EnumFacing side) {
        return true;
    }

    @Override
    public boolean sideIsConsumer(EnumFacing side) {
        return false;
    }

    @Override
    public double getMaxOutput() {
        return Double.MAX_VALUE;
    }

    @Override
    public double getTemp() {
        if (getReactor() != null) {
            return getReactor().getTemp();
        }
        return 0;
    }

    @Override
    public double getInverseConductionCoefficient() {
        return 5;
    }

    @Override
    public double getInsulationCoefficient(EnumFacing side) {
        if (getReactor() != null) {
            return getReactor().getInsulationCoefficient(side);
        }
        return 0;
    }

    @Override
    public void transferHeatTo(double heat) {
        if (getReactor() != null) {
            getReactor().transferHeatTo(heat);
        }
    }

    @Override
    public double[] simulateHeat() {
        return HeatUtils.simulate(this);
    }

    @Override
    public double applyTemperatureChange() {
        if (getReactor() != null) {
            return getReactor().applyTemperatureChange();
        }
        return 0;
    }

    @Override
    public boolean canConnectHeat(EnumFacing side) {
        return getReactor() != null;
    }

    @Override
    public IHeatTransfer getAdjacent(EnumFacing side) {
        TileEntity adj = Coord4D.get(this).offset(side).getTileEntity(world);
        if (CapabilityUtils.hasCapability(adj, Capabilities.HEAT_TRANSFER_CAPABILITY, side.getOpposite())) {
            if (!(adj instanceof TileEntityReactorBlock)) {
                return CapabilityUtils.getCapability(adj, Capabilities.HEAT_TRANSFER_CAPABILITY, side.getOpposite());
            }
        }
        return null;
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return getInventorySlotIdsForSide(side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            //Allow inserting
            return false;
        } else if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            return getReactor() == null;
        }
        return super.isCapabilityDisabled(capability, side);
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            boolean prevEject = fluidEject;
            fluidEject = dataStream.readBoolean();
            if (prevEject != fluidEject) {
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(fluidEject);
        return data;
    }

    @Override
    public EnumActionResult onSneakRightClick(EntityPlayer player, EnumFacing side) {
        if (!isRemote()) {
            fluidEject = !fluidEject;
            String modeText = " " + (fluidEject ? EnumColor.DARK_RED : EnumColor.DARK_GREEN) + LangUtils.transOutputInput(fluidEject) + ".";
            player.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + " " + EnumColor.GREY +
                    LangUtils.localize("tooltip.configurator.reactorPortEject") + modeText));
            Mekanism.packetHandler.sendUpdatePacket(this);
            markNoUpdateSync();
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public EnumActionResult onRightClick(EntityPlayer player, EnumFacing side) {
        if (!isRemote() && player.isCreative() && getReactor() != null) {
            getReactor().setPlasmaTemp(1_000_000_000);
        }
        return EnumActionResult.PASS;
    }

    @Override
    protected boolean shouldDumpRadiation() {
        return true;
    }

    @Nullable
    private IInventorySlot getReactorInventorySlot(int slotID) {
        return slotID == 0 && getReactor() != null ? getReactor().getHohlraumSlot() : null;
    }
}
