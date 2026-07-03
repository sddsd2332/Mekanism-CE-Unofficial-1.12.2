package mekanism.common.inventory.container;

import mekanism.common.tile.TileEntityGasTank;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerGasTank extends MekanismTileContainer<TileEntityGasTank> {

    public ContainerGasTank(InventoryPlayer inventory, TileEntityGasTank tile) {
        super(tile, inventory);
    }

    @Override
    protected void addSlots() {
        super.addSlots();
        addArmorSlots(inv, -20, 67, 0);
    }
}
