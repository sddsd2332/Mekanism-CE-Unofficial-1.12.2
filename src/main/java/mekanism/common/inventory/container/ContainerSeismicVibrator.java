package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntitySeismicVibrator;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerSeismicVibrator extends MekanismTileContainer<TileEntitySeismicVibrator> {

    public ContainerSeismicVibrator(InventoryPlayer inventory, TileEntitySeismicVibrator tile) {
        super(tile, inventory);
    }

}
