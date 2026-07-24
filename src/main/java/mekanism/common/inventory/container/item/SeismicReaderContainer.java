package mekanism.common.inventory.container.item;

import mekanism.common.inventory.container.IEmptyContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

public class SeismicReaderContainer extends MekanismItemContainer implements IEmptyContainer {

    public SeismicReaderContainer(InventoryPlayer inv, EnumHand hand, ItemStack stack) {
        super(inv, hand, stack);
    }

    public SeismicReaderContainer(InventoryPlayer inv, EnumHand hand, int itemSlot, ItemStack stack) {
        super(inv, hand, itemSlot, stack);
    }
}
