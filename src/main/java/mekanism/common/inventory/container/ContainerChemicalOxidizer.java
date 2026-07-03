package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntityChemicalOxidizer;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerChemicalOxidizer extends MekanismTileContainer<TileEntityChemicalOxidizer> {

    public ContainerChemicalOxidizer(InventoryPlayer inventory, TileEntityChemicalOxidizer tile) {
        super(tile, inventory);
    }

}
