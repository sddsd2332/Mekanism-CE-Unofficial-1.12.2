package mekanism.common.item.interfaces;

import mekanism.api.NBTConstants;
import mekanism.common.base.ISustainedInventory;
import mekanism.common.util.ItemDataUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;

public interface IItemSustainedInventory extends ISustainedInventory {

    @Override
    default void setInventory(NBTTagList nbtTags, Object... data) {
        if (data.length > 0 && data[0] instanceof ItemStack stack) {
            setSustainedInventory(nbtTags, stack);
        } else {
            throw new UnsupportedOperationException("IItemSustainedInventory needs a stack to work with");
        }
    }

    default void setSustainedInventory(NBTTagList nbtTags, ItemStack stack) {
        ItemDataUtils.setListOrRemove(stack, NBTConstants.ITEMS, nbtTags);
    }

    @Override
    default NBTTagList getInventory(Object... data) {
        if (data.length > 0 && data[0] instanceof ItemStack stack) {
            return getSustainedInventory(stack);
        }
        throw new UnsupportedOperationException("IItemSustainedInventory needs a stack to work with");
    }

    default NBTTagList getSustainedInventory(ItemStack stack) {
        return ItemDataUtils.getList(stack, NBTConstants.ITEMS);
    }

    default boolean hasSustainedInventory(ItemStack stack) {
        return !getSustainedInventory(stack).isEmpty();
    }
}
