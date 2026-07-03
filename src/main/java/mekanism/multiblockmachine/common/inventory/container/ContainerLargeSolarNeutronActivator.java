package mekanism.multiblockmachine.common.inventory.container;

import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeSolarNeutronActivator;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerLargeSolarNeutronActivator extends MekanismTileContainer<TileEntityLargeSolarNeutronActivator> {

    public ContainerLargeSolarNeutronActivator(InventoryPlayer inventory, TileEntityLargeSolarNeutronActivator tile) {
        super(tile, inventory);
    }
}
