package mekanism.common.inventory.container;

import mekanism.common.tile.TileEntityFuelwoodHeater;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerFuelwoodHeater extends MekanismTileContainer<TileEntityFuelwoodHeater> {

    public ContainerFuelwoodHeater(InventoryPlayer inventory, TileEntityFuelwoodHeater tile) {
        super(tile, inventory);
    }

}
