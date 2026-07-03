package mekanism.common.inventory;

import mekanism.api.DataHandlerUtils;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.inventory.IMekanismInventory;
import mekanism.common.item.interfaces.IItemSustainedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Helper class for implementing Mekanism inventories backed by an ItemStack.
 */
public abstract class ItemStackMekanismInventory implements IMekanismInventory {

    @Nonnull
    protected final ItemStack stack;
    private final List<IInventorySlot> slots;

    protected ItemStackMekanismInventory(@Nonnull ItemStack stack) {
        this.stack = stack;
        slots = getInitialInventory();
        if (!stack.isEmpty() && stack.getItem() instanceof IItemSustainedInventory sustainedInventory) {
            DataHandlerUtils.readContainers(getInventorySlots(null), sustainedInventory.getSustainedInventory(stack));
        }
    }

    protected abstract List<IInventorySlot> getInitialInventory();

    @Nonnull
    @Override
    public List<IInventorySlot> getInventorySlots(@Nullable EnumFacing side) {
        return slots;
    }

    @Override
    public void onContentsChanged() {
        if (!stack.isEmpty() && stack.getItem() instanceof IItemSustainedInventory sustainedInventory) {
            sustainedInventory.setSustainedInventory(DataHandlerUtils.writeContainers(getInventorySlots(null)), stack);
        }
    }
}
