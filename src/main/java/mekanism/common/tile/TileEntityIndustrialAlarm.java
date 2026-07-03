package mekanism.common.tile;

import mekanism.common.tile.prefab.TileEntityEffectsBlock;
import mekanism.common.util.InventoryUtils;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import org.jetbrains.annotations.NotNull;

public class TileEntityIndustrialAlarm extends TileEntityEffectsBlock {


    public TileEntityIndustrialAlarm() {
        super("machine.IndustrialAlarm", "IndustrialAlarm", 0);
        initializeInventorySlots();
    }


    @Override
    public void onUpdateClient() {
        super.onUpdateClient();
    }

    @Override
    public void onUpdateServer() {
        super.onUpdateServer();
        setActive(isPowered());
    }

    @Override
    public boolean renderUpdate() {
        return false;
    }

    @Override
    public boolean lightUpdate() {
        return true;
    }

    @NotNull
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        if (getActive()) {
            return new AxisAlignedBB(this.pos, this.pos.add(1, 1, 1));
        }
        return super.getRenderBoundingBox();
    }

    @NotNull
    @Override
    public int[] getSlotsForFace(@NotNull EnumFacing side) {
        return InventoryUtils.EMPTY;
    }
}
