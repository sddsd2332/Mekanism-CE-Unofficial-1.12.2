package mekanism.common.inventory.container;

import mekanism.common.tile.TileEntityModificationStation;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerModificationStation extends MekanismTileContainer<TileEntityModificationStation> {


    public ContainerModificationStation(InventoryPlayer inventory, TileEntityModificationStation tile) {
        super(tile, inventory);
    }

    @Override
    protected void addSlots() {
        super.addSlots();
        addArmorSlots(inv, 8, 8, 8);
    }

    @Override
    protected int getInventoryYOffset() {
        return super.getInventoryYOffset() + 64;
    }
}
