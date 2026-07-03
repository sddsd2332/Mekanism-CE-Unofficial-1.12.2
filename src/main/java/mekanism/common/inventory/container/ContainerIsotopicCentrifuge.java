package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntityIsotopicCentrifuge;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerIsotopicCentrifuge extends MekanismTileContainer<TileEntityIsotopicCentrifuge> {

    public ContainerIsotopicCentrifuge(InventoryPlayer inventory, TileEntityIsotopicCentrifuge tile) {
        super(tile, inventory);
    }

}
