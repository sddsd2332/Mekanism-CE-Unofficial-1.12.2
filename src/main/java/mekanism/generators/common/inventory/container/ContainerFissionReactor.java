package mekanism.generators.common.inventory.container;

import mekanism.common.inventory.container.MekanismTileContainer;
import mekanism.generators.common.tile.fission.TileEntityFissionReactorCasing;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerFissionReactor extends MekanismTileContainer<TileEntityFissionReactorCasing> {

    public ContainerFissionReactor(InventoryPlayer inventory, TileEntityFissionReactorCasing tile) {
        super(tile, inventory);
    }

    @Override
    protected int getInventoryXOffset() {
        return super.getInventoryXOffset() + 10;
    }

    @Override
    protected int getInventoryYOffset() {
        return super.getInventoryYOffset() + 91;
    }
}
