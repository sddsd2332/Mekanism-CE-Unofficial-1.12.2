package mekanism.common.util;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

public final class OperationUtils {

    private OperationUtils() {
    }

    public static int getMaxOutputOperations(IInventorySlot outputSlot, ItemStack output) {
        if (output.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        ItemStack current = outputSlot.getStack();
        if (current.isEmpty()) {
            return outputSlot.getLimit(output) / output.getCount();
        }
        if (!ItemHandlerHelper.canItemStacksStack(current, output)) {
            return 0;
        }
        return Math.max(0, (outputSlot.getLimit(output) - current.getCount()) / output.getCount());
    }

    public static void shrinkStack(IInventorySlot inputSlot, ItemStack input, int operations) {
        if (!input.isEmpty() && operations > 0) {
            inputSlot.shrinkStack(input.getCount() * operations, Action.EXECUTE);
        }
    }

    public static void growOutput(IInventorySlot outputSlot, ItemStack output, int operations) {
        if (!output.isEmpty() && operations > 0) {
            outputSlot.insertItem(StackUtils.size(output, output.getCount() * operations), Action.EXECUTE, AutomationType.INTERNAL);
        }
    }
}
