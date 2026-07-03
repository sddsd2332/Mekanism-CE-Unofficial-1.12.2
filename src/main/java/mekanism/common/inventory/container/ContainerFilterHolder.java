package mekanism.common.inventory.container;

import mekanism.common.tile.prefab.TileEntityContainerBlock;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerFilterHolder extends MekanismTileContainer<TileEntityContainerBlock> {

    public ContainerFilterHolder(InventoryPlayer inv, TileEntityContainerBlock tile) {
        super(tile, inv);
    }

    @Override
    protected void addSlots() {
        addUpgradeSlots();
    }

    @Override
    protected int getInventoryXOffset() {
        return super.getInventoryXOffset() + 50;
    }

    @Override
    protected int getInventoryYOffset() {
        return BASE_Y_OFFSET + 88;
    }
}
