package mekanism.common.inventory.container;

import mekanism.common.tile.TileEntityResistiveHeater;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerResistiveHeater extends MekanismTileContainer<TileEntityResistiveHeater> {

    public ContainerResistiveHeater(InventoryPlayer inventory, TileEntityResistiveHeater tile) {
        super(tile, inventory);
    }

}
