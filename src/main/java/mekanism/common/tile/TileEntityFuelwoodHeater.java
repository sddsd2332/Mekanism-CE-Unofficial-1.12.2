package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.HeatAPI.HeatTransfer;
import mekanism.common.Mekanism;
import mekanism.common.base.IActiveState;
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
import mekanism.common.util.MekanismUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;

public class TileEntityFuelwoodHeater extends TileEntityContainerBlock implements ISecurityTile, IActiveState {

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
        fuelSlot = builder.addSlot(FuelInventorySlot.forFuel(TileEntityFurnace::getItemBurnTime, listener, 15, 29), RelativeSide.values());
        return builder.build();
    }

    @Override
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener) {
        HeatCapacitorHelper builder = createHeatCapacitorHelper();
        heatCapacitor = builder.addCapacitor(BasicHeatCapacitor.create(100, 5, 10, () -> getAmbientTemperature(null), listener));
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
    protected void onUpdateServer() {
        super.onUpdateServer();
        if (updateDelay > 0) {
            updateDelay--;
            if (updateDelay == 0 && clientActive != isActive) {
                Mekanism.packetHandler.sendUpdatePacket(this);
            }
        }

        if (burnTime <= 0) {
            maxBurnTime = burnTime = Math.max(0, fuelSlot.burn());
        }
        int ticks = Math.min(burnTime, Math.max(1, MekanismConfig.current().general.fuelwoodTickMultiplier.val()));
        boolean burning = ticks > 0;
        if (burning) {
            burnTime -= ticks;
            double heat = HeatAPI.multiplyHeat(MekanismConfig.current().general.heatPerFuelTick.val(), ticks);
            if (HeatAPI.isFinite(heat) && heat > 0) {
                heatCapacitor.handleHeat(heat);
            }
        }
        HeatTransfer loss = simulate();
        lastTransferLoss = sanitizeLoss(loss.adjacentTransfer());
        lastEnvironmentLoss = sanitizeLoss(loss.environmentTransfer());
        setActive(burning);
    }

    @Override
    public boolean supportsAsync() {
        return false;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        clientActive = isActive = nbtTags.getBoolean("isActive");
        burnTime = Math.max(0, nbtTags.getInteger("burnTime"));
        maxBurnTime = Math.max(0, nbtTags.getInteger("maxBurnTime"));
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setBoolean("isActive", isActive);
        nbtTags.setInteger("burnTime", burnTime);
        nbtTags.setInteger("maxBurnTime", maxBurnTime);

    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            heatCapacitor.setHeatCapacityFromPacket(dataStream.readDouble());
            heatCapacitor.setHeat(dataStream.readDouble());
            clientActive = dataStream.readBoolean();
            burnTime = Math.max(0, dataStream.readInt());
            maxBurnTime = Math.max(0, dataStream.readInt());
            lastTransferLoss = sanitizeLoss(dataStream.readDouble());
            lastEnvironmentLoss = sanitizeLoss(dataStream.readDouble());
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
        data.add(heatCapacitor.getHeatCapacity());
        data.add(heatCapacitor.getHeat());
        data.add(isActive);
        data.add(burnTime);
        data.add(maxBurnTime);
        data.add(sanitizeLoss(lastTransferLoss));
        data.add(sanitizeLoss(lastEnvironmentLoss));
        return data;
    }

    private static double sanitizeLoss(double loss) {
        return HeatAPI.isFinite(loss) ? Math.max(0, Math.min(HeatAPI.MAX_HEAT, loss)) : 0;
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

    public double getTemp() {
        return heatCapacitor.getTemperature();
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

}
