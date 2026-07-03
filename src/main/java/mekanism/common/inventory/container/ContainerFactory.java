package mekanism.common.inventory.container;

import mekanism.common.tile.factory.TileEntityFactory;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerFactory extends MekanismTileContainer<TileEntityFactory> {

    public ContainerFactory(InventoryPlayer inventory, TileEntityFactory tile) {
        super(tile, inventory);
    }


    @Override
    protected int getInventoryYOffset() {
        return tile.getPlayerInventoryYOffset();
    }

    @Override
    protected int getInventoryXOffset() {
        return tile.getPlayerInventoryXOffset();
    }
}
