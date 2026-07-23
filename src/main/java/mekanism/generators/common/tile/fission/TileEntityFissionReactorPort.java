package mekanism.generators.common.tile.fission;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.Mekanism;
import mekanism.common.base.IActiveState;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.fluid.ProxiedFluidTankHolder;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.capabilities.holder.gas.ProxiedGasTankHolder;
import mekanism.common.util.*;
import mekanism.generators.common.block.states.BlockStateGenerator.FissionPortModeProperty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class TileEntityFissionReactorPort extends TileEntityFissionReactorCasing implements IConfigurable, IActiveState {

    private PortMode mode = PortMode.INPUT;

    public TileEntityFissionReactorPort() {
        super("FissionReactorPort");
        initializeInventorySlots();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        return ProxiedFluidTankHolder.create(
              side -> structure != null && mode == PortMode.INPUT && structure.gasCoolantTank.getStored() == 0,
              side -> structure != null && mode == PortMode.OUTPUT_COOLANT,
              this::getFissionFluidTanks
        );
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        return ProxiedGasTankHolder.create(
              side -> structure != null && mode == PortMode.INPUT,
              side -> structure != null && mode != PortMode.INPUT,
              this::getFissionGasTanks
        );
    }

    private List<IExtendedFluidTank> getFissionFluidTanks(EnumFacing side) {
        if (structure == null) {
            return Collections.emptyList();
        }
        return switch (mode) {
            case INPUT -> Collections.singletonList(structure.coolantTank);
            case OUTPUT_COOLANT -> Collections.singletonList(structure.steamTank);
            case OUTPUT_WASTE -> Collections.emptyList();
        };
    }

    private List<IExtendedGasTank> getFissionGasTanks(EnumFacing side) {
        if (structure == null) {
            return Collections.emptyList();
        }
        return switch (mode) {
            case INPUT -> Arrays.asList(structure.fuelTank, structure.gasCoolantTank);
            case OUTPUT_COOLANT -> Collections.singletonList(structure.heatedCoolantTank);
            case OUTPUT_WASTE -> Collections.singletonList(structure.wasteTank);
        };
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        if (structure == null || mode == PortMode.INPUT) {
            return;
        }

        if (mode == PortMode.OUTPUT_WASTE && structure.wasteTank.getGas() != null && structure.wasteTank.getGas().amount > 0) {
            GasStack toSend = structure.wasteTank.getGas().copy();
            int sent = GasUtils.emit(toSend, this, EnumSet.allOf(EnumFacing.class));
            if (sent > 0) {
                structure.wasteTank.extract(sent, Action.EXECUTE, AutomationType.INTERNAL);
            }
        }

        if (mode == PortMode.OUTPUT_COOLANT && structure.steamTank.getFluidAmount() > 0) {
            EmitUtils.forEachSide(getWorld(), getPos(), EnumSet.allOf(EnumFacing.class), (tile, side) -> {
                if (tile instanceof TileEntityFissionReactorPort) {
                    return;
                }
                IFluidHandler handler = CapabilityUtils.getCapability(tile, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side.getOpposite());
                FluidStack steam = structure.steamTank.getFluid();
                if (handler != null && steam != null && PipeUtils.canFill(handler, steam)) {
                    int filled = handler.fill(steam, true);
                    if (filled > 0) {
                        structure.steamTank.drain(filled, true);
                    }
                }
            });
        }
        if (mode == PortMode.OUTPUT_COOLANT && structure.heatedCoolantTank.getGas() != null && structure.heatedCoolantTank.getGas().amount > 0) {
            GasStack toSend = structure.heatedCoolantTank.getGas().copy();
            int sent = GasUtils.emit(toSend, this, EnumSet.allOf(EnumFacing.class));
            if (sent > 0) {
                structure.heatedCoolantTank.extract(sent, Action.EXECUTE, AutomationType.INTERNAL);
            }
        }
        syncCachedDataFromStructure();
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        mode = PortMode.byIndex(nbtTags.getInteger("fissionPortMode"));
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setInteger("fissionPortMode", mode.ordinal());
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(mode.ordinal());
        return data;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            PortMode previousMode = mode;
            mode = PortMode.byIndex(dataStream.readInt());
            if (previousMode != mode) {
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == Capabilities.CONFIGURABLE_CAPABILITY) {
            return true;
        }
        return super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (capability == Capabilities.CONFIGURABLE_CAPABILITY) {
            return (T) this;
        }
        return super.getCapability(capability, side);
    }

    @Override
    public EnumActionResult onSneakRightClick(EntityPlayer player, EnumFacing side) {
        if (!isRemote()) {
            mode = mode.next();
            String modeText = switch (mode) {
                case INPUT -> LangUtils.localize("gui.input");
                case OUTPUT_COOLANT -> LangUtils.localize("fission.port.mode.output_coolant");
                case OUTPUT_WASTE -> LangUtils.localize("fission.port.mode.output_waste");
            };
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
    public boolean getActive() {
        return mode != PortMode.INPUT;
    }

    @Override
    public void setActive(boolean active) {
        mode = active ? PortMode.OUTPUT_WASTE : PortMode.INPUT;
    }

    @Override
    public boolean renderUpdate() {
        return true;
    }

    @Override
    public boolean lightUpdate() {
        return false;
    }

    public enum PortMode {
        INPUT,
        OUTPUT_COOLANT,
        OUTPUT_WASTE;

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

    public FissionPortModeProperty getRenderMode() {
        return switch (mode) {
            case INPUT -> FissionPortModeProperty.INPUT;
            case OUTPUT_COOLANT -> FissionPortModeProperty.OUTPUT_COOLANT;
            case OUTPUT_WASTE -> FissionPortModeProperty.OUTPUT_WASTE;
        };
    }

    public PortMode getMode() {
        return mode;
    }
}
