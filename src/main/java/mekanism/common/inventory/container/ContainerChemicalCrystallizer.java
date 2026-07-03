package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntityChemicalCrystallizer;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerChemicalCrystallizer extends MekanismTileContainer<TileEntityChemicalCrystallizer> {

    public ContainerChemicalCrystallizer(InventoryPlayer inventory, TileEntityChemicalCrystallizer tile) {
        super(tile, inventory);
    }

}
