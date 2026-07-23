package mekanism.common.tile.multiblock;

import io.netty.buffer.ByteBuf;
import mekanism.api.EnumColor;
import mekanism.api.IConfigurable;
import mekanism.api.IContentsListener;
import mekanism.api.TileNetworkList;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.Mekanism;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.block.states.BlockStateBasic.BoilerValveModeProperty;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.fluid.ProxiedFluidTankHolder;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.gas.ProxiedGasTankHolder;
import mekanism.common.content.boiler.*;
import mekanism.common.integration.computer.IComputerIntegration;
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
import java.util.List;

public class TileEntityBoilerValve extends TileEntityBoilerCasing implements IComputerIntegration, IComparatorSupport, IConfigurable {

    private static final String[] methods = new String[]{"isFormed", "getSteam", "getWater", "getBoilRate", "getMaxBoilRate", "getTemp"};
    public BoilerTank waterTank;
    public BoilerTank steamTank;
    public BoilerGasTank inputTank;
    public BoilerGasTank outputTank;
    private int currentRedstoneLevel;

    private PortMode mode = PortMode.INPUT;

    // Legacy field kept for compatibility with old saves and active-texture checks.
    public boolean Eject;

    public TileEntityBoilerValve() {
        super("BoilerValve");
        waterTank = new BoilerWaterTank(this);
        steamTank = new BoilerSteamTank(this);
        inputTank = new BoilerInputGasTank(this);
        outputTank = new BoilerOutputGasTank(this);
        initializeInventorySlots();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        return ProxiedFluidTankHolder.create(
              side -> isFormed() && mode == PortMode.INPUT,
              side -> isFormed() && mode == PortMode.OUTPUT_STEAM,
              this::getValveFluidTanks
        );
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        return ProxiedGasTankHolder.create(
              side -> isFormed() && mode == PortMode.INPUT,
              side -> isFormed() && mode == PortMode.OUTPUT_COOLANT,
              this::getValveGasTanks
        );
    }

    private List<IExtendedFluidTank> getValveFluidTanks(EnumFacing side) {
        if (!isFormed()) {
            return Collections.emptyList();
        }
        return switch (mode) {
            case INPUT -> Collections.singletonList(waterTank);
            case OUTPUT_STEAM -> Collections.singletonList(steamTank);
            case OUTPUT_COOLANT -> Collections.emptyList();
        };
    }

    private List<IExtendedGasTank> getValveGasTanks(EnumFacing side) {
        if (!isFormed()) {
            return Collections.emptyList();
        }
        return switch (mode) {
            case INPUT -> Collections.singletonList(inputTank);
            case OUTPUT_COOLANT -> Collections.singletonList(outputTank);
            case OUTPUT_STEAM -> Collections.emptyList();
        };
    }

    private boolean isFormed() {
        return (!isRemote() && structure != null) || (isRemote() && clientHasStructure);
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (nbtTags.hasKey("boilerValveMode")) {
            mode = PortMode.byIndex(nbtTags.getInteger("boilerValveMode"));
        } else {
            //Old saves only had input/output eject state
            mode = nbtTags.getBoolean("Eject") ? PortMode.OUTPUT_STEAM : PortMode.INPUT;
        }
        updateEjectFlag();
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("boilerValveMode", mode.ordinal());
        nbtTags.setBoolean("Eject", Eject);
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        if (structure != null) {
            if (mode == PortMode.OUTPUT_STEAM && structure.steamStored != null && structure.steamStored.amount > 0) {
                FluidUtils.emit(EnumSet.allOf(EnumFacing.class), steamTank, this);
            }
            if (mode == PortMode.OUTPUT_COOLANT) {
                GasUtils.emit(EnumSet.allOf(EnumFacing.class), outputTank, this);
            }
            int newRedstoneLevel = getRedstoneLevel();
            if (newRedstoneLevel != currentRedstoneLevel) {
                updateComparatorOutputLevelSync();
                currentRedstoneLevel = newRedstoneLevel;
            }
        }
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
                    return new Object[]{structure.steamStored != null ? structure.steamStored.amount : 0};
                }
                case 2 -> {
                    return new Object[]{structure.waterStored != null ? structure.waterStored.amount : 0};
                }
                case 3 -> {
                    return new Object[]{structure.lastBoilRate};
                }
                case 4 -> {
                    return new Object[]{structure.lastMaxBoil};
                }
                case 5 -> {
                    return new Object[]{structure.getTemperature()};
                }
            }
        }
        throw new NoSuchMethodException();
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
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(waterTank.getFluidAmount(), waterTank.getCapacity());
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            PortMode prevMode = mode;
            mode = PortMode.byIndex(dataStream.readInt());
            updateEjectFlag();
            if (prevMode != mode) {
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(mode.ordinal());
        return data;
    }

    @Override
    public EnumActionResult onSneakRightClick(EntityPlayer player, EnumFacing side) {
        if (!isRemote()) {
            mode = mode.next();
            updateEjectFlag();
            String modeText;
            switch (mode) {
                case INPUT:
                    modeText = LangUtils.localize("gui.input");
                    break;
                case OUTPUT_COOLANT:
                    modeText = LangUtils.localize("gui.output") + " " + LangUtils.localize("gui.coolant");
                    break;
                default:
                    modeText = LangUtils.localize("gui.output") + " " + LangUtils.localize("fluid.steam");
                    break;
            }
            player.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + " " + EnumColor.GREY +
                    LangUtils.localize("tooltip.configurator.reactorPortEject") + " " + EnumColor.AQUA + modeText));
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

    private void updateEjectFlag() {
        Eject = mode != PortMode.INPUT;
    }

    public PortMode getMode() {
        return mode;
    }

    public BoilerValveModeProperty getRenderMode() {
        return switch (mode) {
            case INPUT -> BoilerValveModeProperty.INPUT;
            case OUTPUT_STEAM -> BoilerValveModeProperty.OUTPUT_STEAM;
            case OUTPUT_COOLANT -> BoilerValveModeProperty.OUTPUT_COOLANT;
        };
    }

    public enum PortMode {
        INPUT,
        OUTPUT_STEAM,
        OUTPUT_COOLANT;

        private static final PortMode[] MODES = values();

        public PortMode next() {
            return MODES[(ordinal() + 1) % MODES.length];
        }

        public static PortMode byIndex(int index) {
            if (index < 0 || index >= MODES.length) {
                return INPUT;
            }
            return MODES[index];
        }
    }
}
