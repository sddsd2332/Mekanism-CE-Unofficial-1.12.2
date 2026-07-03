package mekanism.common.inventory.container;

import mekanism.common.tile.prefab.TileEntityContainerBlock;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerSPS extends MekanismTileContainer<TileEntityContainerBlock>{


    public ContainerSPS(InventoryPlayer inventory, TileEntityContainerBlock tile) {
        super(tile, inventory);
    }

    @Override
    protected int getInventoryYOffset() {
        return 89;
    }

    @Override
    protected void addSlots() {

    }
}
