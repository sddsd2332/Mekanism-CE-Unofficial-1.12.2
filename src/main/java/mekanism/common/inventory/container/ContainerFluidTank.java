package mekanism.common.inventory.container;

import mekanism.common.tile.TileEntityFluidTank;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerFluidTank extends ContainerFluidStorage<TileEntityFluidTank> {

    public ContainerFluidTank(InventoryPlayer inventory, TileEntityFluidTank tile) {
        super(tile, inventory);
    }

}
