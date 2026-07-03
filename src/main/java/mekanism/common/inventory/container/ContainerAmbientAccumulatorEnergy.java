package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntityAmbientAccumulatorEnergy;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerAmbientAccumulatorEnergy extends MekanismTileContainer<TileEntityAmbientAccumulatorEnergy> {

    public ContainerAmbientAccumulatorEnergy(InventoryPlayer inventory, TileEntityAmbientAccumulatorEnergy tile) {
        super(tile, inventory);
    }

    @Override
    protected int getInventoryYOffset() {
        return 89;
    }

}
