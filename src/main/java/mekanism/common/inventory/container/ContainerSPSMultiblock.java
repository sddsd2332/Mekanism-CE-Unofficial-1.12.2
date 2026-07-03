package mekanism.common.inventory.container;

import mekanism.common.tile.prefab.TileEntityContainerBlock;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerSPSMultiblock extends ContainerSPS {

    public ContainerSPSMultiblock(InventoryPlayer inventory, TileEntityContainerBlock tile) {
        super(inventory, tile);
    }

    @Override
    protected int getInventoryYOffset() {
        return 100;
    }
}
