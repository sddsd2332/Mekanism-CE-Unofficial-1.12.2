package mekanism.common.inventory.container;

import ic2.api.item.IElectricItem;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

public abstract class ContainerEnergyStorage<TILE extends TileEntityContainerBlock> extends MekanismTileContainer<TILE> {

    protected ContainerEnergyStorage(TILE tile, InventoryPlayer inventory) {
        super(tile, inventory);
    }

    private boolean canTransfer(ItemStack slotStack) {
        return MekanismUtils.useIC2() && slotStack.getItem() instanceof IElectricItem;
    }
}
