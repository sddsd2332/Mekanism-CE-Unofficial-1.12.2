package mekanism.common.tile.machine;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.common.Mekanism;
import mekanism.common.base.*;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

public class TileEntitySeismicVibrator extends TileEntityElectricBlock implements IActiveState, IRedstoneControl, ISecurityTile, IBoundingBlock, ISpecialSelectionWireframeTile {

    public boolean isActive;

    public boolean clientActive;

    public int updateDelay;

    public int clientPiston;

    public double BASE_ENERGY_PER_TICK = MachineType.SEISMIC_VIBRATOR.getUsage();

    public RedstoneControl controlType = RedstoneControl.DISABLED;

    public TileComponentSecurity securityComponent = new TileComponentSecurity(this);
    private MachineEnergyContainer energyContainer;
    private EnergyInventorySlot energySlot;

    public TileEntitySeismicVibrator() {
        super("SeismicVibrator", MachineType.SEISMIC_VIBRATOR.getStorage());
        initializeInventorySlots();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(energyContainer, this::getWorld, listener, 143, 35));
        return builder.build();
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        EnergyContainerHelper builder = createEnergyContainerHelper();
        builder.addContainer(energyContainer = MachineEnergyContainer.input(this::getEnergy, this::setEnergy, this::getMaxEnergy, () -> BASE_ENERGY_PER_TICK, listener),
              RelativeSide.BACK);
        return builder.build();
    }

    @Override
    public void onUpdateClient() {
        super.onUpdateClient();
        if (isActive) {
            clientPiston++;
        }
        if (updateDelay > 0) {
            updateDelay--;
            if (updateDelay == 0 && clientActive != isActive) {
                isActive = clientActive;
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    @Override
    public void onAsyncUpdateServer() {
        super.onAsyncUpdateServer();
        if (updateDelay > 0) {
            updateDelay--;
            if (updateDelay == 0 && clientActive != isActive) {
                Mekanism.packetHandler.sendUpdatePacket(this);
            }
        }
        energySlot.fillContainerOrConvert();
        if (MekanismUtils.canFunction(this)) {
            if (energyContainer.extract(BASE_ENERGY_PER_TICK, Action.SIMULATE, AutomationType.INTERNAL) == BASE_ENERGY_PER_TICK) {
                setActive(true);
                energyContainer.extract(BASE_ENERGY_PER_TICK, Action.EXECUTE, AutomationType.INTERNAL);
            } else {
                setActive(false);
            }
        } else {
            setActive(false);
        }
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (getActive()) {
            Mekanism.activeVibrators.add(Coord4D.get(this));
        } else {
            Mekanism.activeVibrators.remove(Coord4D.get(this));
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        Mekanism.activeVibrators.remove(Coord4D.get(this));
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setBoolean("isActive", isActive);
        nbtTags.setInteger("controlType", controlType.ordinal());
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        clientActive = isActive = nbtTags.getBoolean("isActive");
        controlType = MekanismUtils.getByIndex(RedstoneControl.values(), nbtTags.getInteger("controlType"), controlType);
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            clientActive = dataStream.readBoolean();
            controlType = MekanismUtils.getByIndex(RedstoneControl.values(), dataStream.readInt(), controlType);
            if (updateDelay == 0 && clientActive != isActive) {
                updateDelay = MekanismConfig.current().general.UPDATE_DELAY.val();
                isActive = clientActive;
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(isActive);
        data.add(controlType.ordinal());
        return data;
    }

    @Override
    public boolean getActive() {
        return isActive;
    }

    @Override
    public void setActive(boolean active) {
        isActive = active;
        if (clientActive != active && updateDelay == 0) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            updateDelay = 10;
            clientActive = active;
        }
    }

    @Override
    public boolean renderUpdate() {
        return false;
    }

    @Override
    public boolean lightUpdate() {
        return true;
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return facing != EnumFacing.DOWN && facing != EnumFacing.UP;
    }

    @Override
    public RedstoneControl getControlType() {
        return controlType;
    }

    @Override
    public void setControlType(RedstoneControl type) {
        controlType = type;
        MekanismUtils.saveChunk(this);
    }

    @Override
    public boolean sideIsConsumer(EnumFacing side) {
        return side == facing.getOpposite();
    }

    @Override
    public boolean canPulse() {
        return false;
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    public MachineEnergyContainer getEnergyContainer() {
        return energyContainer;
    }

    @Override
    public void onPlace() {
        MekanismUtils.makeBoundingBlock(world, getPos().up(), Coord4D.get(this));
    }

    @Override
    public void onBreak() {
        world.setBlockToAir(getPos().up());
        world.setBlockToAir(getPos());
    }

    @Nonnull
    @Override
    public BlockFaceShape getOffsetBlockFaceShape(@Nonnull EnumFacing face, @Nonnull Vec3i offset) {
        return BlockFaceShape.SOLID;
    }
@Override
    @SideOnly(Side.CLIENT)
    public Class<?> getSelectionWireframeModelClass() {
        return mekanism.client.model.ModelSeismicVibrator.class;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getSelectionWireframeAnimationCacheKey(IBlockState state, IBlockAccess world, BlockPos pos) {
        return Math.round(getSelectionWireframePiston() * 1000F);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void applySelectionWireframeModelState(Object model, IBlockState state, IBlockAccess world, BlockPos pos) {
        if (model instanceof mekanism.client.model.ModelSeismicVibrator seismicModel) {
            seismicModel.setPiston(getSelectionWireframePiston());
        }
    }

    @SideOnly(Side.CLIENT)
    private float getSelectionWireframePiston() {
        float partial = isActive ? Minecraft.getMinecraft().getRenderPartialTicks() : 0F;
        float actualRate = (float) Math.sin((clientPiston + partial) / 5F);
        return Math.max(0F, actualRate);
    }
}
