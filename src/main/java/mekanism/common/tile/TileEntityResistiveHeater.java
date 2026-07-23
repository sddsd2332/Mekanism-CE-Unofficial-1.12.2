package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.HeatAPI.HeatTransfer;
import mekanism.client.render.bloom.BloomRenderResistiveHeater;
import mekanism.common.Mekanism;
import mekanism.common.base.IRedstoneControl;
import mekanism.common.base.ISpecialSelectionWireframeTile;
import mekanism.common.block.states.BlockStateMachine.MachineType;
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
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

public class TileEntityResistiveHeater extends TileEntityEffectsBlock implements IComputerIntegration, IRedstoneControl, ISecurityTile, ISpecialSelectionWireframeTile {

    private static final String[] methods = new String[]{"getEnergy", "getMaxEnergy", "getTemperature", "setEnergyUsage"};
    public double energyUsage = 100;
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
        heatCapacitor = builder.addCapacitor(BasicHeatCapacitor.create(100, 5, 10, () -> getAmbientTemperature(null), listener));
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
    protected void onUpdateServer() {
        super.onUpdateServer();
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
                double efficiency = MekanismConfig.current().general.resistiveHeaterEfficiency.val();
                efficiency = HeatAPI.isFinite(efficiency) ? Math.max(0, Math.min(1, efficiency)) : 0;
                double heat = HeatAPI.multiplyHeat(toUse, efficiency);
                if (HeatAPI.isFinite(heat) && heat > 0) {
                    heatCapacitor.handleHeat(heat);
                }
                getMainEnergyContainer().extract(toUse, Action.EXECUTE, AutomationType.INTERNAL);
            }
        }

        setActive(toUse > 0);
        clientEnergyUsed = toUse;
        HeatTransfer loss = simulate();
        lastTransferLoss = sanitizeLoss(loss.adjacentTransfer());
        lastEnvironmentLoss = sanitizeLoss(loss.environmentTransfer());
        float newSoundScale = HeatAPI.isFinite(toUse) ? (float) Math.min(Float.MAX_VALUE, Math.max(0, toUse / 1E5)) : 0;
        if (Math.abs(newSoundScale - soundScale) > 0.01) {
            packet = true;
        }
        soundScale = newSoundScale;
        if (packet) {
            Mekanism.packetHandler.sendUpdatePacket(this);
        }
    }

    @Override
    public boolean supportsAsync() {
        return false;
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
        if (nbtTags.hasKey("energyUsage")) {
            energyUsage = sanitizeEnergyUsage(nbtTags.getDouble("energyUsage"));
        }
        clientActive = isActive = nbtTags.getBoolean("isActive");
        controlType = MekanismUtils.getByIndex(RedstoneControl.values(), nbtTags.getInteger("controlType"), controlType);
        updateMaxEnergy();
    }


    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setDouble("energyUsage", energyUsage);
        nbtTags.setBoolean("isActive", isActive);
        nbtTags.setInteger("controlType", controlType.ordinal());
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
            energyUsage = sanitizeEnergyUsage(MekanismUtils.convertToJoules(dataStream.readInt()));
            updateMaxEnergy();
            return;
        }

        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            energyUsage = sanitizeEnergyUsage(dataStream.readDouble());
            heatCapacitor.setHeatCapacityFromPacket(dataStream.readDouble());
            heatCapacitor.setHeat(dataStream.readDouble());
            clientActive = dataStream.readBoolean();
            double syncedMaxEnergy = dataStream.readDouble();
            maxEnergy = HeatAPI.isFinite(syncedMaxEnergy) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, syncedMaxEnergy)) : 0;
            float syncedSoundScale = dataStream.readFloat();
            soundScale = Float.isFinite(syncedSoundScale) && syncedSoundScale >= 0 ? syncedSoundScale : 0;
            controlType = MekanismUtils.getByIndex(RedstoneControl.values(), dataStream.readInt(), controlType);
            lastTransferLoss = sanitizeLoss(dataStream.readDouble());
            lastEnvironmentLoss = sanitizeLoss(dataStream.readDouble());
            clientEnergyUsed = sanitizeEnergyUsage(dataStream.readDouble());
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
        data.add(heatCapacitor.getHeatCapacity());
        data.add(heatCapacitor.getHeat());
        data.add(isActive);
        data.add(maxEnergy);
        data.add(Float.isFinite(soundScale) && soundScale >= 0 ? soundScale : 0);
        data.add(controlType.ordinal());

        data.add(sanitizeLoss(lastTransferLoss));
        data.add(sanitizeLoss(lastEnvironmentLoss));
        data.add(sanitizeEnergyUsage(clientEnergyUsed));
        return data;
    }

    public double getTemp() {
        return heatCapacitor.getTemperature();
    }

    @Override  //Try to fix the render lighting
    public boolean wasActiveRecently() {
        return getActive();
    }

    public MachineEnergyContainer getEnergyContainer() {
        return getMainEnergyContainer();
    }

    public double getEnergyUsed() {
        return clientEnergyUsed;
    }

    private static double sanitizeEnergyUsage(double usage) {
        return HeatAPI.isFinite(usage) && usage >= 0 ? Math.min(HeatAPI.MAX_HEAT / 400D, usage) : 0;
    }

    private static double sanitizeLoss(double loss) {
        return HeatAPI.isFinite(loss) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, loss)) : 0;
    }

    private void updateMaxEnergy() {
        maxEnergy = HeatAPI.multiplyHeat(energyUsage, 400D);
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
                        energyUsage = sanitizeEnergyUsage((Double) arguments[0]);
                        updateMaxEnergy();
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
    @Override
    @SideOnly(Side.CLIENT)
    public Class<?> getSelectionWireframeModelClass() {
        return mekanism.client.model.ModelResistiveHeater.class;
    }
}
