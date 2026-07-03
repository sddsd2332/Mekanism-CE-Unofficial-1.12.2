package mekanism.common.inventory.container;

import mekanism.common.tile.machine.TileEntityElectricPump;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerElectricPump extends MekanismTileContainer<TileEntityElectricPump> {

    public ContainerElectricPump(InventoryPlayer inventory, TileEntityElectricPump tile) {
        super(tile, inventory);
    }

}
