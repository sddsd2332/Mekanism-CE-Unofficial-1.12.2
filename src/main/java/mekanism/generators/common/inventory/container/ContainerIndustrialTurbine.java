package mekanism.generators.common.inventory.container;

import mekanism.common.inventory.container.ContainerFilter;
import mekanism.generators.common.tile.turbine.TileEntityTurbineCasing;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerIndustrialTurbine extends ContainerFilter {

    public ContainerIndustrialTurbine(InventoryPlayer inventory, TileEntityTurbineCasing tile) {
        super(inventory, tile);
    }

    @Override
    protected int getInventoryXOffset() {
        return super.getInventoryXOffset() + 7;
    }
}
