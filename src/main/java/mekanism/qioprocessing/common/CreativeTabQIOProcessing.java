package mekanism.qioprocessing.common;

import mekanism.qioprocessing.common.registries.QIOProcessingItems;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

public final class CreativeTabQIOProcessing extends CreativeTabs {

    public CreativeTabQIOProcessing() {
        super("tabMekanismQIOProcessing");
    }

    @Override
    public ItemStack createIcon() {
        return new ItemStack(QIOProcessingItems.QIOAutoCraftingUpgrade);
    }
}
