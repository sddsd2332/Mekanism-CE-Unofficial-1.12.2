package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntityChemicalWasher;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerChemicalWasher extends MekanismTileContainer<TileEntityChemicalWasher> {

    public ContainerChemicalWasher(InventoryPlayer inventory, TileEntityChemicalWasher tile) {
        super(tile, inventory);
    }

}
