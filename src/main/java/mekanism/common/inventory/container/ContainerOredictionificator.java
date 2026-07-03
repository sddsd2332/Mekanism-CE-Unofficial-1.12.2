package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntityOredictionificator;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerOredictionificator extends MekanismTileContainer<TileEntityOredictionificator> {

    public ContainerOredictionificator(InventoryPlayer inventory, TileEntityOredictionificator tile) {
        super(tile, inventory);
    }

    @Override
    protected int getInventoryXOffset() {
        return super.getInventoryXOffset() + 30;
    }

    @Override
    protected int getInventoryYOffset() {
        return super.getInventoryYOffset() + 64;
    }
}
