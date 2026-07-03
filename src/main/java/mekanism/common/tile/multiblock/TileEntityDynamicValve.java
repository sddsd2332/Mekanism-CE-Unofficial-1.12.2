package mekanism.common.tile.multiblock;

import io.netty.buffer.ByteBuf;
import mekanism.api.EnumColor;
import mekanism.api.IConfigurable;
import mekanism.api.IContentsListener;
import mekanism.api.TileNetworkList;
import mekanism.common.Mekanism;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.fluid.ProxiedFluidTankHolder;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.gas.ProxiedGasTankHolder;
import mekanism.common.content.tank.DynamicFluidTank;
import mekanism.common.content.tank.DynamicGasTank;
import mekanism.common.util.FluidUtils;
import mekanism.common.util.GasUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.EnumSet;

public class TileEntityDynamicValve extends TileEntityDynamicTank implements IComparatorSupport, IConfigurable {

    public DynamicFluidTank fluidTank;
    public DynamicGasTank gasTank;
    private int currentRedstoneLevel;

    public boolean eject;

    public TileEntityDynamicValve() {
        super("Dynamic Valve");
        fluidTank = new DynamicFluidTank(this);
        gasTank = new DynamicGasTank(this);
        initializeInventorySlots();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        return ProxiedFluidTankHolder.create(
              side -> isFormed() && !eject && (structure == null || !structure.hasGas()),
              side -> isFormed() && structure != null && structure.hasFluid(),
              side -> isFormed() ? Collections.singletonList(fluidTank) : Collections.emptyList()
        );
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        return ProxiedGasTankHolder.create(
              side -> isFormed() && !eject && (structure == null || !structure.hasFluid()),
              side -> isFormed() && structure != null && structure.hasGas(),
              side -> isFormed() ? Collections.singletonList(gasTank) : Collections.emptyList()
        );
    }

    private boolean isFormed() {
        return (!isRemote() && structure != null) || (isRemote() && clientHasStructure);
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        eject = nbtTags.getBoolean("eject");
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setBoolean("eject", eject);
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        int newRedstoneLevel = getRedstoneLevel();
        if (newRedstoneLevel != currentRedstoneLevel) {
            updateComparatorOutputLevelSync();
            currentRedstoneLevel = newRedstoneLevel;
        }
        if (structure != null && eject) {
            if (fluidTank.getFluid() != null && fluidTank.getFluid().getFluid() != null) {
                FluidUtils.emit(EnumSet.allOf(EnumFacing.class), fluidTank, this);
            }
            GasUtils.emit(EnumSet.allOf(EnumFacing.class), gasTank, this);
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize("gui.dynamicTank");
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isFormed() && capability == Capabilities.CONFIGURABLE_CAPABILITY) {
            return true;
        }
        return super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isFormed() && capability == Capabilities.CONFIGURABLE_CAPABILITY) {
            return (T) this;
        }
        return super.getCapability(capability, side);
    }

    @Override
    protected boolean exposesInventoryToAutomation() {
        return true;
    }

    @Override
    public int getRedstoneLevel() {
        int stored = Math.max(fluidTank.getFluidAmount(), gasTank.getGasAmount());
        int capacity = Math.max(fluidTank.getCapacity(), gasTank.getMaxGas());
        return MekanismUtils.redstoneLevelFromContents(stored, capacity);
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            boolean prevEject = eject;
            eject = dataStream.readBoolean();
            if (prevEject != eject) {
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(eject);
        return data;
    }

    @Override
    public EnumActionResult onSneakRightClick(EntityPlayer player, EnumFacing side) {
        if (!isRemote()) {
            eject = !eject;
            String modeText = " " + (eject ? EnumColor.DARK_RED : EnumColor.DARK_GREEN) + LangUtils.transOutputInput(eject) + ".";
            player.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + " " + EnumColor.GREY +
                    LangUtils.localize("tooltip.configurator.reactorPortEject") + modeText));
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
    protected boolean shouldDumpRadiation() {
        return true;
    }
}
