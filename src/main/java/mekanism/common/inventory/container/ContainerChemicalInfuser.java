package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntityChemicalInfuser;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerChemicalInfuser extends MekanismTileContainer<TileEntityChemicalInfuser> {

    public ContainerChemicalInfuser(InventoryPlayer inventory, TileEntityChemicalInfuser tile) {
        super(tile, inventory);
    }

}
