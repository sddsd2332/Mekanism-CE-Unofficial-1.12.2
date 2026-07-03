package mekanism.common.inventory.container;

import mekanism.common.tile.prefab.TileEntityContainerBlock;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerThermoelectricBoiler extends ContainerFilter {

    public ContainerThermoelectricBoiler(InventoryPlayer inventory, TileEntityContainerBlock tile) {
        super(inventory, tile);
    }

    @Override
    protected int getInventoryXOffset() {
        return 29;
    }
}
