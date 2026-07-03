package mekanism.common.inventory.container;

import mekanism.common.tile.prefab.TileEntityContainerBlock;
import net.minecraft.entity.player.InventoryPlayer;

public abstract class ContainerFluidStorage<TILE extends TileEntityContainerBlock> extends MekanismTileContainer<TILE> {

    protected ContainerFluidStorage(TILE tank, InventoryPlayer inventory) {
        super(tank, inventory);

    }

    @Override
    protected void addSlots() {
        super.addSlots();
        addArmorSlots(inv, -20, 67, 0);
    }
}
