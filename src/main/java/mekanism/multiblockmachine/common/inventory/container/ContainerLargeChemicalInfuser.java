package mekanism.multiblockmachine.common.inventory.container;

import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeChemicalInfuser;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerLargeChemicalInfuser extends MekanismTileContainer<TileEntityLargeChemicalInfuser> {

    public ContainerLargeChemicalInfuser(InventoryPlayer inventory, TileEntityLargeChemicalInfuser tile) {
        super(tile, inventory);
    }
}
