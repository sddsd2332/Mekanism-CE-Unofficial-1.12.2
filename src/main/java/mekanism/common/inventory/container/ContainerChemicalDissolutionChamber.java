package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntityChemicalDissolutionChamber;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerChemicalDissolutionChamber extends MekanismTileContainer<TileEntityChemicalDissolutionChamber> {

    public ContainerChemicalDissolutionChamber(InventoryPlayer inventory, TileEntityChemicalDissolutionChamber tile) {
        super(tile, inventory);
    }

}
