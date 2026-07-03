package mekanism.api.inventory;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Represents an item type for comparing {@link ItemStack ItemStacks} without size.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public interface IHashedItem {

    /**
     * Gets the internal {@link ItemStack} that backs this item type.
     *
     * @apiNote Do not modify the returned value.
     */
    ItemStack getInternalStack();

    /**
     * Creates a mutable {@link ItemStack} of this type with the given size.
     */
    ItemStack createStack(int size);

    default Item getItem() {
        return getInternalStack().getItem();
    }

    default int getMaxStackSize() {
        return getInternalStack().getMaxStackSize();
    }
}
