package mekanism.common.inventory.container;

import mekanism.common.tile.qio.TileEntityQIOImporter;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerQIOImporter extends MekanismTileContainer<TileEntityQIOImporter> {

    public ContainerQIOImporter(InventoryPlayer inventory, TileEntityQIOImporter tile) {
        super(tile, inventory);
    }

    @Override
    protected int getInventoryYOffset() {
        return 158;
    }

    @Override
    protected int getInventoryXOffset() {
        return 38;
    }
}
