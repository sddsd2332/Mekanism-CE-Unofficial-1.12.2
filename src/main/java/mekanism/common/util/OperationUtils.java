package mekanism.common.util;

import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.items.ItemHandlerHelper;

public final class OperationUtils {

    private OperationUtils() {
    }

    public static int getMaxOutputOperations(NonNullList<ItemStack> inventory, int outputIndex, ItemStack output) {
        if (output.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        ItemStack current = inventory.get(outputIndex);
        if (current.isEmpty()) {
            return output.getMaxStackSize() / output.getCount();
        }
        if (!ItemHandlerHelper.canItemStacksStack(current, output)) {
            return 0;
        }
        return Math.max(0, (current.getMaxStackSize() - current.getCount()) / output.getCount());
    }

    public static void shrinkStack(NonNullList<ItemStack> inventory, int index, ItemStack input, int operations) {
        inventory.set(index, StackUtils.size(inventory.get(index), inventory.get(index).getCount() - input.getCount() * operations));
    }

    public static void growOutput(NonNullList<ItemStack> inventory, int index, ItemStack output, int operations) {
        if (output.isEmpty() || operations <= 0) {
            return;
        }
        int amount = output.getCount() * operations;
        ItemStack current = inventory.get(index);
        if (current.isEmpty()) {
            inventory.set(index, StackUtils.size(output, amount));
        } else {
            current.grow(amount);
        }
    }
}
