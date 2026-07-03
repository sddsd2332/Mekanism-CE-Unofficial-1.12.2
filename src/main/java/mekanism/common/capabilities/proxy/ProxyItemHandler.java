package mekanism.common.capabilities.proxy;

import mekanism.api.Action;
import mekanism.api.inventory.ISidedItemHandler;
import mekanism.common.capabilities.holder.IHolder;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ProxyItemHandler extends ProxyHandler implements IItemHandlerModifiable {

    private final ISidedItemHandler inventory;

    public ProxyItemHandler(@Nonnull ISidedItemHandler inventory, @Nullable EnumFacing side, @Nullable IHolder holder) {
        super(side, holder);
        this.inventory = inventory;
    }

    @Override
    public int getSlots() {
        return inventory.getSlots(side);
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return inventory.getStackInSlot(slot, side);
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        if (!readOnly) {
            inventory.setStackInSlot(slot, stack, side);
        }
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        return readOnlyInsert() ? stack : inventory.insertItem(slot, stack, side, Action.get(!simulate));
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return readOnlyExtract() ? ItemStack.EMPTY : inventory.extractItem(slot, amount, side, Action.get(!simulate));
    }

    @Override
    public int getSlotLimit(int slot) {
        return inventory.getSlotLimit(slot, side);
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return !readOnly || inventory.isItemValid(slot, stack, side);
    }
}
