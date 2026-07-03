package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.common.Mekanism;
import mekanism.common.base.IActiveState;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import mekanism.common.capabilities.holder.heat.HeatCapacitorHelper;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.slot.FuelInventorySlot;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.HeatUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;

public class TileEntityFuelwoodHeater extends TileEntityContainerBlock implements IHeatTransfer, ISecurityTile, IActiveState {

    public double temperature;

    public int burnTime;
    public int maxBurnTime;

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

    public double lastEnvironmentLoss;
    public double lastTransferLoss;

    public TileComponentSecurity securityComponent = new TileComponentSecurity(this);
    private FuelInventorySlot fuelSlot;
    private BasicHeatCapacitor heatCapacitor;

    public TileEntityFuelwoodHeater() {
        super("FuelwoodHeater");
        initializeInventorySlots();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = createInventorySlotHelper();
        fuelSlot = builder.addSlot(FuelInventorySlot.forFuel(stack -> TileEntityFurnace.getItemBurnTime(stack) / 2, listener, 15, 29), RelativeSide.values());
        return builder.build();
    }

    @Override
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener) {
        HeatCapacitorHelper builder = createHeatCapacitorHelper();
        heatCapacitor = builder.addCapacitor(BasicHeatCapacitor.create(100, 5, 1_000, () -> IHeatTransfer.AMBIENT_TEMP, listener));
        return builder.build();
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
        if (updateDelay > 0) {
            updateDelay--;
            if (updateDelay == 0 && clientActive != isActive) {
                Mekanism.packetHandler.sendUpdatePacket(this);
            }
        }

        boolean burning = false;
        if (burnTime > 0) {
            burnTime--;
            burning = true;
        } else {
            maxBurnTime = burnTime = fuelSlot.burn();
            burning = burnTime > 0;
        }
        if (burning) {
            transferHeatTo(MekanismConfig.current().general.heatPerFuelTick.val());
        }
        double[] loss = simulateHeat();
        applyTemperatureChange();
        lastTransferLoss = loss[0];
        lastEnvironmentLoss = loss[1];
        setActive(burning);
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        if (heatCapacitor != null && nbtTags.hasKey("heatStored")) {
            heatCapacitor.deserializeNBT(nbtTags.getCompoundTag("heatStored"));
            temperature = getTemp();
        } else {
            temperature = nbtTags.getDouble("temperature");
            syncHeatCapacitorFromTemperature();
        }
        clientActive = isActive = nbtTags.getBoolean("isActive");
        burnTime = nbtTags.getInteger("burnTime");
        maxBurnTime = nbtTags.getInteger("maxBurnTime");
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setDouble("temperature", getTemp());
        if (heatCapacitor != null) {
            nbtTags.setTag("heatStored", heatCapacitor.serializeNBT());
        }
        nbtTags.setBoolean("isActive", isActive);
        nbtTags.setInteger("burnTime", burnTime);
        nbtTags.setInteger("maxBurnTime", maxBurnTime);

    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            temperature = dataStream.readDouble();
            syncHeatCapacitorFromTemperature();
            clientActive = dataStream.readBoolean();
            burnTime = dataStream.readInt();
            maxBurnTime = dataStream.readInt();
            lastTransferLoss = dataStream.readDouble();
            lastEnvironmentLoss = dataStream.readDouble();
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
        data.add(temperature);
        data.add(isActive);
        data.add(burnTime);
        data.add(maxBurnTime);
        data.add(lastTransferLoss);
        data.add(lastEnvironmentLoss);
        return data;
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return facing != EnumFacing.DOWN && facing != EnumFacing.UP;
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
    public double getTemp() {
        return heatCapacitor == null ? temperature : heatCapacitor.getTemperature() - IHeatTransfer.AMBIENT_TEMP;
    }

    @Override
    public double getInverseConductionCoefficient() {
        return 5;
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
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    private void syncHeatCapacitorFromTemperature() {
        if (heatCapacitor != null) {
            heatCapacitor.setHeat((temperature + IHeatTransfer.AMBIENT_TEMP) * heatCapacitor.getHeatCapacity());
        }
    }
}
