package mekanism.common.inventory.container;

import mekanism.common.tile.multiblock.TileEntityDynamicTank;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerDynamicTank extends ContainerFluidStorage<TileEntityDynamicTank> {

    public ContainerDynamicTank(InventoryPlayer inventory, TileEntityDynamicTank tile) {
        super(tile, inventory);
    }


}
