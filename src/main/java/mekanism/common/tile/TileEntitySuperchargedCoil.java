package mekanism.common.tile;

import mekanism.common.tile.prefab.TileEntityEffectsBlock;
import mekanism.common.util.InventoryUtils;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;

public class TileEntitySuperchargedCoil extends TileEntityEffectsBlock {

    public TileEntitySuperchargedCoil() {
        super("machine.laser", "SuperchargedCoil", 1_000_000_000D);
        initializeInventorySlots();
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        if (getActive()) {
            setActive(false);
        }
    }

    @Override
    public boolean sideIsConsumer(EnumFacing side) {
        return false;
    }

    @Override
    public boolean renderUpdate() {
        return false;
    }

    @Override
    public boolean lightUpdate() {
        return false;
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return InventoryUtils.EMPTY;
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true;
        }
        return super.isCapabilityDisabled(capability, side);
    }


}
