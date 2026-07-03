package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntityFluidicPlenisher;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerFluidicPlenisher extends MekanismTileContainer<TileEntityFluidicPlenisher> {

    public ContainerFluidicPlenisher(InventoryPlayer inventory, TileEntityFluidicPlenisher tile) {
        super(tile, inventory);
    }


}
