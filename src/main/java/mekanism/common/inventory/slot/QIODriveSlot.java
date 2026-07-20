package mekanism.common.inventory.slot;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.common.content.qio.IQIODriveHolder;
import mekanism.common.content.qio.IQIODriveItem;
import mekanism.common.content.qio.QIODriveData;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Inventory slot that accepts exactly one QIO drive and keeps its mount state current. */
public class QIODriveSlot extends BasicInventorySlot {

    private final IQIODriveHolder holder;
    private final int slot;

    public QIODriveSlot(IQIODriveHolder holder, int slot, @Nullable IContentsListener listener, int x, int y) {
        super(1, notExternal, notExternal, stack -> !stack.isEmpty() && stack.getItem() instanceof IQIODriveItem,
              listener, x, y);
        this.holder = holder;
        this.slot = slot;
    }

    public int getSlotIndex() {
        return slot;
    }

    @Override
    public void setStack(@Nonnull ItemStack stack) {
        super.setStack(stack);
        notifyDriveChanged();
    }

    @Override
    public void setStackUnchecked(@Nonnull ItemStack stack) {
        super.setStackUnchecked(stack);
        notifyDriveChanged();
    }

    @Override
    public void setStackUncheckedNoUpdate(@Nonnull ItemStack stack) {
        super.setStackUncheckedNoUpdate(stack);
        notifyDriveChanged();
    }

    /** Used by a holder callback to replace a defensive-copy stack without a callback loop. */
    public void setStackFromHolder(@Nonnull ItemStack stack) {
        super.setStackUncheckedNoUpdate(stack);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(@Nonnull ItemStack stack, @Nonnull Action action, @Nonnull AutomationType automationType) {
        ItemStack remainder = super.insertItem(stack, action, automationType);
        if (action.execute() && remainder.getCount() != stack.getCount()) {
            notifyDriveChanged();
        }
        return remainder;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int amount, @Nonnull Action action, @Nonnull AutomationType automationType) {
        ItemStack extracted = super.extractItem(amount, action, automationType);
        if (action.execute() && !extracted.isEmpty()) {
            notifyDriveChanged();
        }
        return extracted;
    }

    private void notifyDriveChanged() {
        if (holder instanceof TileEntity && (((TileEntity) holder).getWorld() == null || ((TileEntity) holder).getWorld().isRemote)) {
            return;
        }
        if (!getStack().isEmpty()) {
            QIODriveData.initialize(getStack());
            holder.updateQIODriveStack(slot, getStack().copy());
        }
        holder.onQIODriveSlotChanged(slot);
    }
}
