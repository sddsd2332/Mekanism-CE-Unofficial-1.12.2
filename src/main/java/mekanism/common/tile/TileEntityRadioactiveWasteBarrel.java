package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.*;
import mekanism.api.gas.GasStack;
import mekanism.common.Mekanism;
import mekanism.common.base.IActiveState;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.base.ISustainedData;
import mekanism.common.base.ITankManager;
import mekanism.common.capabilities.gas.BasicGasTank;
import mekanism.common.capabilities.holder.gas.GasTankHelper;
import mekanism.common.capabilities.holder.gas.IGasTankHolder;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.GasUtils;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TileUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.Collections;

public class TileEntityRadioactiveWasteBarrel extends TileEntityContainerBlock implements ISustainedData, ITankManager, IComparatorSupport, IActiveState, IConfigurable {

    private long lastProcessTick;
    public BasicGasTank gasTank;
    private int processTicks;
    public boolean isActive;
    public boolean clientActive;

    public TileEntityRadioactiveWasteBarrel() {
        super("radioactive_waste_barrel");
        initializeInventorySlots();
    }

    @Override
    protected IGasTankHolder getInitialGasTanks(IContentsListener listener) {
        GasTankHelper builder = createGasTankHelper();
        gasTank = BasicGasTank.input(512000, gas -> gas != null && gas.isRadiation(), listener);
        builder.addTank(gasTank, RelativeSide.TOP, RelativeSide.BOTTOM);
        return builder.build();
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        if (getWorld().getTotalWorldTime() > lastProcessTick) {
            lastProcessTick = getWorld().getTotalWorldTime();
            if (gasTank.getGas() != null && gasTank.getGas().getGas().isRadiation() && ++processTicks >= 20) {
                processTicks = 0;
                gasTank.extract(1, Action.EXECUTE, AutomationType.INTERNAL);
            }
            if (getActive()) {
                GasUtils.emit(Collections.singleton(EnumFacing.DOWN), gasTank, this, getDownOutputLimit());
            }
        }
    }

    private int getDownOutputLimit() {
        TileEntity below = MekanismUtils.getTileEntity(world, pos.down());
        if (below instanceof TileEntityRadioactiveWasteBarrel) {
            TileEntityRadioactiveWasteBarrel barrel = (TileEntityRadioactiveWasteBarrel) below;
            return Math.min(barrel.gasTank.getNeeded(), gasTank.getCapacity());
        }
        return gasTank.getCapacity();
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        writeSustainedGasTanks(itemStack);
        ItemDataUtils.setLegacyGas(itemStack, "gasStored", gasTank.getGas());
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        if (!readSustainedGasTanks(itemStack)) {
            gasTank.setStackUnchecked(ItemDataUtils.getLegacyGas(itemStack, "gasStored"));
        }
        sanitizeAndClampGasTank();
    }

    private void sanitizeAndClampGasTank() {
        GasStack stored = gasTank.getGas();
        if (stored != null && (stored.amount <= 0 || stored.getGas() == null || !stored.getGas().isRadiation())) {
            gasTank.setEmpty();
        } else if (stored != null) {
            gasTank.setStackSize(stored.amount, Action.EXECUTE);
        }
    }

    @Override
    public Object[] getManagedTanks() {
        return new Object[]{gasTank};
    }

    @Override
    public EnumActionResult onSneakRightClick(EntityPlayer player, EnumFacing side) {
        if (!isRemote()) {
            setActive(!getActive());
            world.playSound(null, getPos().getX(), getPos().getY(), getPos().getZ(), SoundEvents.UI_BUTTON_CLICK, SoundCategory.BLOCKS, 0.3F, 1);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public EnumActionResult onRightClick(EntityPlayer player, EnumFacing side) {
        return EnumActionResult.PASS;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            clientActive = isActive = dataStream.readBoolean();
            TileUtils.readTankData(dataStream, gasTank);
        }

    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(isActive);
        TileUtils.addTankData(data, gasTank);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        clientActive = isActive = nbtTags.getBoolean("isActive");
        if (!hasStoredGasTanks(nbtTags) && nbtTags.hasKey("gasTank")) {
            gasTank.read(nbtTags.getCompoundTag("gasTank"));
        }
        sanitizeAndClampGasTank();
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setBoolean("isActive", isActive);
    }

    @Override
    public boolean supportsAsync() {
        return false;
    }

    @Override
    public boolean getActive() {
        return isActive;
    }

    @Override
    public void setActive(boolean active) {
        isActive = active;
        if (clientActive != active) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            clientActive = active;
        }
    }


    @Override
    public boolean renderUpdate() {
        return false;
    }

    @Override
    public boolean lightUpdate() {
        return false;
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(gasTank.getStored(), gasTank.getMaxGas());
    }

    @Override
    protected boolean shouldDumpRadiation() {
        return true;
    }
}
