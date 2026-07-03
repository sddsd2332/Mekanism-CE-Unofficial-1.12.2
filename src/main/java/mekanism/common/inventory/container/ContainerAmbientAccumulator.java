package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntityAmbientAccumulator;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerAmbientAccumulator extends MekanismTileContainer<TileEntityAmbientAccumulator> {
    public ContainerAmbientAccumulator(InventoryPlayer inventory, TileEntityAmbientAccumulator tile) {
        super(tile, inventory);
    }

    //TODO
    @Override
    protected int getInventoryYOffset() {
        return 89;
    }
}
