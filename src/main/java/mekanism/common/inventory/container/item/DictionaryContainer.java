package mekanism.common.inventory.container.item;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

public class DictionaryContainer extends MekanismItemContainer {

    public DictionaryContainer(InventoryPlayer inv, EnumHand hand, ItemStack stack) {
        super(inv, hand, stack);
    }

    @Override
    protected int getInventoryYOffset() {
        return super.getInventoryYOffset() + 5;
    }
}
