package mekanism.common.inventory.container;

import mekanism.common.tile.prefab.TileEntityContainerBlock;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerFilter extends MekanismTileContainer<TileEntityContainerBlock> {

    public ContainerFilter(InventoryPlayer inventory, TileEntityContainerBlock tile) {
        super(tile, inventory);
    }

    @Override
    protected void addSlots() {
    }
}
