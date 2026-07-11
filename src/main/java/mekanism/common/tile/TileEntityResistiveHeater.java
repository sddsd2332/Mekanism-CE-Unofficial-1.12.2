package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.client.render.bloom.BloomRenderResistiveHeater;
import mekanism.common.Mekanism;
import mekanism.common.base.IRedstoneControl;
import mekanism.common.base.ISpecialSelectionWireframeTile;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import mekanism.common.capabilities.holder.heat.HeatCapacitorHelper;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.computer.IComputerIntegration;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.prefab.TileEntityEffectsBlock;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.HeatUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

public class TileEntityResistiveHeater extends TileEntityEffectsBlock implements IHeatTransfer, IComputerIntegration, IRedstoneControl, ISecurityTile, ISpecialSelectionWireframeTile {

    private static final String[] methods = new String[]{"getEnergy", "getMaxEnergy", "getTemperature", "setEnergyUsage"};
    public double energyUsage = 100;
    public double temperature;
    /**
     * Whether or not this machine is in it's active state.
     */
    public boolean isActive;
    /**
     * The client's current active state.
     */
    public boolean clientActive;
    /**
     * How many ticks must pass until this block's active state can sync with the client.
     */
    public int updateDelay;
    public float soundScale = 1;
    public double lastEnvironmentLoss;
    public double lastTransferLoss;
    public double clientEnergyUsed;
    public RedstoneControl controlType = RedstoneControl.DISABLED;
    public TileComponentSecurity securityComponent = new TileComponentSecurity(this);
    private EnergyInventorySlot energySlot;
    private BasicHeatCapacitor heatCapacitor;

    public TileEntityResistiveHeater() {
        super("machine.resistiveheater", "ResistiveHeater", MachineType.RESISTIVE_HEATER.getStorage());
        initializeInventorySlots();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        energySlot = builder.addSlot(EnergyInventorySlot.fillOrConvert(getMainEnergyContainer(), this::getWorld, listener, 15, 35));
        return builder.build();
    }

    @Override
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener) {
        HeatCapacitorHelper builder = createHeatCapacitorHelper();
        heatCapacitor = builder.addCapacitor(BasicHeatCapacitor.create(100, 5, 1_000, () -> IHeatTransfer.AMBIENT_TEMP, listener));
        return builder.build();
    }

    @Override
    protected double getMainEnergyPerTick() {
        return energyUsage;
    }

    @Override
    public void onUpdateClient() {
        super.onUpdateClient();
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
        boolean packet = false;
        if (updateDelay > 0) {
            updateDelay--;
            if (updateDelay == 0 && clientActive != isActive) {
                packet = true;
            }
        }
        energySlot.fillContainerOrConvert();
        double toUse = 0;
        if (MekanismUtils.canFunction(this)) {
            toUse = getMainEnergyContainer().extract(energyUsage, Action.SIMULATE, AutomationType.INTERNAL);
            if (toUse > 0) {
                transferHeatTo(toUse / MekanismConfig.current().general.energyPerHeat.val());
                getMainEnergyContainer().extract(toUse, Action.EXECUTE, AutomationType.INTERNAL);
            }
        }

        setActive(toUse > 0);
        clientEnergyUsed = toUse;
        double[] loss = simulateHeat();
        applyTemperatureChange();
        lastTransferLoss = loss[0];
        lastEnvironmentLoss = loss[1];
        float newSoundScale = (float) Math.max(0, toUse / 1E5);
        if (Math.abs(newSoundScale - soundScale) > 0.01) {
            packet = true;
        }
        soundScale = newSoundScale;
        if (packet) {
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
    }

    @Override
    protected boolean hasCrossMachineAsyncOperations() {
        return true;
    }

    @Override
    public boolean sideIsConsumer(EnumFacing side) {
        return side == MekanismUtils.getLeft(facing) || side == MekanismUtils.getRight(facing);
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return facing != EnumFacing.DOWN && facing != EnumFacing.UP;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        energyUsage = nbtTags.getDouble("energyUsage");
        if (heatCapacitor != null && nbtTags.hasKey("heatStored")) {
            heatCapacitor.deserializeNBT(nbtTags.getCompoundTag("heatStored"));
            temperature = getTemp();
        } else {
            temperature = nbtTags.getDouble("temperature");
            syncHeatCapacitorFromTemperature();
        }
        clientActive = isActive = nbtTags.getBoolean("isActive");
        controlType = MekanismUtils.getByIndex(RedstoneControl.values(), nbtTags.getInteger("controlType"), controlType);
        maxEnergy = energyUsage * 400;
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setDouble("energyUsage", energyUsage);
        nbtTags.setDouble("temperature", getTemp());
        if (heatCapacitor != null) {
            nbtTags.setTag("heatStored", heatCapacitor.serializeNBT());
        }
        nbtTags.setBoolean("isActive", isActive);
        nbtTags.setInteger("controlType", controlType.ordinal());
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            energyUsage = MekanismUtils.convertToJoules(dataStream.readInt());
            maxEnergy = energyUsage * 400;
            return;
        }

        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            energyUsage = dataStream.readDouble();
            temperature = dataStream.readDouble();
            syncHeatCapacitorFromTemperature();
            clientActive = dataStream.readBoolean();
            maxEnergy = dataStream.readDouble();
            soundScale = dataStream.readFloat();
            controlType = MekanismUtils.getByIndex(RedstoneControl.values(), dataStream.readInt(), controlType);
            lastTransferLoss = dataStream.readDouble();
            lastEnvironmentLoss = dataStream.readDouble();
            clientEnergyUsed = dataStream.readDouble();
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

        data.add(energyUsage);
        data.add(temperature);
        data.add(isActive);
        data.add(maxEnergy);
        data.add(soundScale);
        data.add(controlType.ordinal());

        data.add(lastTransferLoss);
        data.add(lastEnvironmentLoss);
        data.add(clientEnergyUsed);
        return data;
    }

    @Override
    public double getTemp() {
        return heatCapacitor == null ? temperature : heatCapacitor.getTemperature() - IHeatTransfer.AMBIENT_TEMP;
    }

    @Override
    public double getInverseConductionCoefficient() {
        return 5;
    }

    @Override  //Try to fix the render lighting
    public boolean wasActiveRecently() {
        return getActive();
    }

    @Override
    public double getInsulationCoefficient(EnumFacing side) {
        return 1000;
    }

    @Override
    public void transferHeatTo(double heat) {
        if (heatCapacitor == null) {
            temperature += heat;
        } else {
            heatCapacitor.handleHeat(heat * heatCapacitor.getHeatCapacity());
        }
    }

    public MachineEnergyContainer getEnergyContainer() {
        return getMainEnergyContainer();
    }

    public double getEnergyUsed() {
        return clientEnergyUsed;
    }

    @Override
    public double[] simulateHeat() {
        return HeatUtils.simulate(this);
    }

    @Override
    public double applyTemperatureChange() {
        if (heatCapacitor != null) {
            heatCapacitor.update();
            temperature = getTemp();
        }
        return temperature;
    }

    @Override
    public boolean canConnectHeat(EnumFacing side) {
        return true;
    }

    @Override
    public IHeatTransfer getAdjacent(EnumFacing side) {
        TileEntity adj = Coord4D.get(this).offset(side).getTileEntity(world);
        if (CapabilityUtils.hasCapability(adj, Capabilities.HEAT_TRANSFER_CAPABILITY, side.getOpposite())) {
            return CapabilityUtils.getCapability(adj, Capabilities.HEAT_TRANSFER_CAPABILITY, side.getOpposite());
        }
        return null;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        return capability == Capabilities.HEAT_TRANSFER_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (capability == Capabilities.HEAT_TRANSFER_CAPABILITY) {
            return Capabilities.HEAT_TRANSFER_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, side);
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
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        switch (method) {
            case 0 -> {
                return new Object[]{getEnergy()};
            }
            case 1 -> {
                return new Object[]{getMaxEnergy()};
            }
            case 2 -> {
                return new Object[]{getTemp()};
            }
            case 3 -> {
                if (arguments.length == 1) {
                    if (arguments[0] instanceof Double) {
                        energyUsage = (Double) arguments[0];
                        return new Object[]{"Set energy usage."};
                    }
                }
                return new Object[]{"Invalid parameters."};
            }
            default -> throw new NoSuchMethodException();
        }
    }

    @Override
    public RedstoneControl getControlType() {
        return controlType;
    }

    @Override
    public void setControlType(RedstoneControl type) {
        controlType = type;
    }

    @Override
    public boolean canPulse() {
        return false;
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    public void validate() {
        super.validate();
        if (isRemote()) {
            if (Mekanism.hooks.Bloom && MekanismConfig.current().client.enableBloom.val()) {
                try {
                    new BloomRenderResistiveHeater(this);
                } catch (LinkageError e) {
                    mekanism.common.util.BloomDependencyHelper.disableBloom("BloomRenderResistiveHeater", e);
                }
            }
        }
    }
private void syncHeatCapacitorFromTemperature() {
        if (heatCapacitor != null) {
            heatCapacitor.setHeat((temperature + IHeatTransfer.AMBIENT_TEMP) * heatCapacitor.getHeatCapacity());
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Class<?> getSelectionWireframeModelClass() {
        return mekanism.client.model.ModelResistiveHeater.class;
    }
}
