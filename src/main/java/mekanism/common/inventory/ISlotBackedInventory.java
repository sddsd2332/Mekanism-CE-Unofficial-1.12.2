package mekanism.common.inventory;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.inventory.IMekanismInventory;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nonnull;

/**
 * 1.12 bridge for legacy vanilla inventories backed by Mekanism inventory slots.
 */
public interface ISlotBackedInventory extends IInventory, IMekanismInventory {

    @Override
    default int getSizeInventory() {
        return getInventorySlots(null).size();
    }

    @Override
    default boolean isEmpty() {
        return isInventoryEmpty(null);
    }

    @Nonnull
    @Override
    default ItemStack getStackInSlot(int index) {
        IInventorySlot slot = getInventorySlot(index, null);
        return slot == null ? ItemStack.EMPTY : slot.getStack();
    }

    @Nonnull
    @Override
    default ItemStack decrStackSize(int index, int count) {
        IInventorySlot slot = getInventorySlot(index, null);
        return slot == null ? ItemStack.EMPTY : slot.extractItem(count, Action.EXECUTE, AutomationType.MANUAL);
    }

    @Nonnull
    @Override
    default ItemStack removeStackFromSlot(int index) {
        IInventorySlot slot = getInventorySlot(index, null);
        if (slot == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        slot.setEmpty();
        return stack;
    }

    @Override
    default void setInventorySlotContents(int index, @Nonnull ItemStack stack) {
        IInventorySlot slot = getInventorySlot(index, null);
        if (slot == null) {
            return;
        }
        if (!stack.isEmpty() && stack.getCount() > getInventoryStackLimit()) {
            stack = stack.copy();
            stack.setCount(getInventoryStackLimit());
        }
        if (slot instanceof BasicInventorySlot basicSlot) {
            basicSlot.setStackUnchecked(stack);
        } else {
            slot.setStack(stack);
        }
    }

    @Override
    default int getInventoryStackLimit() {
        return 64;
    }

    @Override
    default void markDirty() {
        onContentsChanged();
    }

    @Override
    default boolean isUsableByPlayer(@Nonnull EntityPlayer player) {
        return true;
    }

    @Override
    default void openInventory(@Nonnull EntityPlayer player) {
    }

    @Override
    default void closeInventory(@Nonnull EntityPlayer player) {
        onContentsChanged();
    }

    @Override
    default boolean isItemValidForSlot(int index, @Nonnull ItemStack stack) {
        IInventorySlot slot = getInventorySlot(index, null);
        if (slot instanceof BasicInventorySlot basicSlot) {
            return basicSlot.isItemValidForInsertion(stack, AutomationType.MANUAL);
        }
        return slot != null && slot.isItemValid(stack);
    }

    @Override
    default int getField(int id) {
        return 0;
    }

    @Override
    default void setField(int id, int value) {
    }

    @Override
    default int getFieldCount() {
        return 0;
    }

    @Override
    default void clear() {
        for (IInventorySlot slot : getInventorySlots(null)) {
            slot.setEmpty();
        }
    }

    @Nonnull
    @Override
    default String getName() {
        return "MekanismInventory";
    }

    @Override
    default boolean hasCustomName() {
        return false;
    }

    @Nonnull
    @Override
    default ITextComponent getDisplayName() {
        return new TextComponentString(getName());
    }
}
