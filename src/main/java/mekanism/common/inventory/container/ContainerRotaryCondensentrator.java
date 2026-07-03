package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntityRotaryCondensentrator;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerRotaryCondensentrator extends MekanismTileContainer<TileEntityRotaryCondensentrator> {

    public ContainerRotaryCondensentrator(InventoryPlayer inventory, TileEntityRotaryCondensentrator tile) {
        super(tile, inventory);
    }

}
