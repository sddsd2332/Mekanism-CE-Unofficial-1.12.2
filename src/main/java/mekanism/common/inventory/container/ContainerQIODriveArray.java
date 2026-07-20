package mekanism.common.inventory.container;

import mekanism.common.tile.qio.TileEntityQIODriveArray;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerQIODriveArray extends MekanismTileContainer<TileEntityQIODriveArray> {

    public ContainerQIODriveArray(InventoryPlayer inventory, TileEntityQIODriveArray tile) {
        super(tile, inventory);
    }

    @Override
    protected int getInventoryYOffset() {
        return BASE_Y_OFFSET + 40;
    }
}
