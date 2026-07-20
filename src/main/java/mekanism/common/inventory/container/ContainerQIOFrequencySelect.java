package mekanism.common.inventory.container;

import mekanism.common.tile.qio.TileEntityQIOComponent;
import net.minecraft.entity.player.InventoryPlayer;

/** Tracker-only container used by the QIO frequency selection screen. */
public class ContainerQIOFrequencySelect extends MekanismTileContainer<TileEntityQIOComponent> implements IEmptyContainer {

    public ContainerQIOFrequencySelect(InventoryPlayer inventory, TileEntityQIOComponent tile) {
        super(tile, inventory);
    }
}
