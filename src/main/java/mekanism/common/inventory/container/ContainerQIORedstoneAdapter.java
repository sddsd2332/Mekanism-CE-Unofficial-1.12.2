package mekanism.common.inventory.container;

import mekanism.common.tile.qio.TileEntityQIORedstoneAdapter;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerQIORedstoneAdapter extends MekanismTileContainer<TileEntityQIORedstoneAdapter> {

    public ContainerQIORedstoneAdapter(InventoryPlayer inventory, TileEntityQIORedstoneAdapter tile) {
        super(tile, inventory);
    }

    @Override
    protected int getInventoryYOffset() {
        return 110;
    }
}
