package mekanism.common.inventory.container;

import mekanism.common.tile.multiblock.TileEntityThermalEvaporationController;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerThermalEvaporationController extends MekanismTileContainer<TileEntityThermalEvaporationController> {

    public ContainerThermalEvaporationController(InventoryPlayer inventory,
                                                 TileEntityThermalEvaporationController tile) {
        super(tile, inventory);
    }

    @Override
    protected int getInventoryXOffset() {
        return super.getInventoryXOffset() + 10;
    }
}
