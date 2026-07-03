package mekanism.common.inventory.container;

import mekanism.common.tile.TileEntitySecurityDesk;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerSecurityDesk extends MekanismTileContainer<TileEntitySecurityDesk> {

    public ContainerSecurityDesk(InventoryPlayer inventory, TileEntitySecurityDesk tile) {
        super(tile, inventory);
    }

    @Override
    protected int getInventoryYOffset() {
        return super.getInventoryYOffset() + 64;
    }
}
