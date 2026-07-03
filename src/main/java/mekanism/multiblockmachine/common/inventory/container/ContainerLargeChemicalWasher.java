package mekanism.multiblockmachine.common.inventory.container;

import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeChemicalWasher;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerLargeChemicalWasher extends MekanismTileContainer<TileEntityLargeChemicalWasher> {

    public ContainerLargeChemicalWasher(InventoryPlayer inventory, TileEntityLargeChemicalWasher tile) {
        super(tile, inventory);
    }
}
