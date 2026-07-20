package mekanism.common.inventory.container;

import mekanism.common.tile.qio.TileEntityQIOExporter;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerQIOExporter extends MekanismTileContainer<TileEntityQIOExporter> {

    public ContainerQIOExporter(InventoryPlayer inventory, TileEntityQIOExporter tile) {
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
