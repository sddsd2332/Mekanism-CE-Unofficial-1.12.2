package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntityDimensionalStabilizer;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerDimensionalStabilizer extends MekanismTileContainer<TileEntityDimensionalStabilizer> {

    public ContainerDimensionalStabilizer(InventoryPlayer inventory, TileEntityDimensionalStabilizer tile) {
        super(tile, inventory);
    }
}
