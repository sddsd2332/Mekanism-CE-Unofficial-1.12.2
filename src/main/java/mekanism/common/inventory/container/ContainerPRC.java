package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntityPRC;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerPRC extends MekanismTileContainer<TileEntityPRC> {

    public ContainerPRC(InventoryPlayer inventory, TileEntityPRC tile) {
        super(tile, inventory);
    }

    @Override
    protected int getInventoryYOffset() {
        return super.getInventoryYOffset() + 5;
    }
}
