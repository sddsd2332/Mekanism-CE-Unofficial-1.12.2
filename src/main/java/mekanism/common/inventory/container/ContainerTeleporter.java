package mekanism.common.inventory.container;

import mekanism.common.tile.TileEntityTeleporter;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerTeleporter extends MekanismTileContainer<TileEntityTeleporter> {

    public ContainerTeleporter(InventoryPlayer inventory, TileEntityTeleporter tile) {
        super(tile, inventory);
    }

    @Override
    protected int getInventoryYOffset() {
        return 158;
    }
}
