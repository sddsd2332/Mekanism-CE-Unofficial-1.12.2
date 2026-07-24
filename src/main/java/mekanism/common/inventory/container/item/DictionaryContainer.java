package mekanism.common.inventory.container.item;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

public class DictionaryContainer extends MekanismItemContainer {

    public DictionaryContainer(InventoryPlayer inv, EnumHand hand, ItemStack stack) {
        super(inv, hand, stack);
    }

    public DictionaryContainer(InventoryPlayer inv, EnumHand hand, int itemSlot, ItemStack stack) {
        super(inv, hand, itemSlot, stack);
    }

    @Override
    protected int getInventoryYOffset() {
        return super.getInventoryYOffset() + 5;
    }
}
