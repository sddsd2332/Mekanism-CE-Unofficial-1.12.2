package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntitySolarNeutronActivator;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerSolarNeutronActivator extends MekanismTileContainer<TileEntitySolarNeutronActivator> {

    public ContainerSolarNeutronActivator(InventoryPlayer inventory, TileEntitySolarNeutronActivator tile) {
        super(tile, inventory);
    }

}
