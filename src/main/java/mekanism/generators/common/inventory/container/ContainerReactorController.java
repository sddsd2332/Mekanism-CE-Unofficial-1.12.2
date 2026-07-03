package mekanism.generators.common.inventory.container;

import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.generators.common.tile.reactor.TileEntityReactorController;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerReactorController extends MekanismTileContainer<TileEntityReactorController> {

    public ContainerReactorController(InventoryPlayer inventory, TileEntityReactorController tile) {
        super(tile, inventory);
    }

    @Override
    protected int getInventoryXOffset() {
        return super.getInventoryXOffset() + 5;
    }
}
