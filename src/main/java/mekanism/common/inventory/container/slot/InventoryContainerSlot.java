package mekanism.common.inventory.container.slot;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.inventory.IgnoredIInventory;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.warning.ISupportsWarning;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

public class InventoryContainerSlot extends Slot implements IInsertableSlot {

    private final BasicInventorySlot slot;
    private final Consumer<ItemStack> uncheckedStackSetter;
    private final ContainerSlotType slotType;
    @Nullable
    private final SlotOverlay slotOverlay;
    @Nullable
    private final Consumer<ISupportsWarning<?>> warningAdder;

    public InventoryContainerSlot(BasicInventorySlot slot, int xPosition, int yPosition, ContainerSlotType slotType, @Nullable SlotOverlay slotOverlay,
          @Nullable Consumer<ISupportsWarning<?>> warningAdder, Consumer<ItemStack> uncheckedStackSetter) {
        super(IgnoredIInventory.INSTANCE, 0, xPosition, yPosition);
        this.slot = slot;
        this.uncheckedStackSetter = uncheckedStackSetter;
        this.slotType = slotType;
        this.slotOverlay = slotOverlay;
        this.warningAdder = warningAdder;
    }

    public IInventorySlot getInventorySlot() {
        return slot;
    }

    public void addWarnings(ISupportsWarning<?> slot) {
        if (warningAdder != null) {
            warningAdder.accept(slot);
        }
    }

    public ContainerSlotType getSlotType() {
        return slotType;
    }

    @Nullable
    public SlotOverlay getSlotOverlay() {
        return slotOverlay;
    }

    @NotNull
    @Override
    public ItemStack insertItem(@NotNull ItemStack stack, @Nonnull Action action) {
        ItemStack remainder = slot.insertItem(stack, action, AutomationType.MANUAL);
        if (action.execute() && stack.getCount() != remainder.getCount()) {
            onSlotChanged();
        }
        return remainder;
    }

    @Override
    public boolean isItemValid(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (slot.isEmpty()) {
            return insertItem(stack, Action.SIMULATE).getCount() < stack.getCount();
        }
        if (slot.extractItem(1, Action.SIMULATE, AutomationType.MANUAL).isEmpty()) {
            return false;
        }
        return slot.isItemValidForInsertion(stack, AutomationType.MANUAL);
    }

    @Nonnull
    @Override
    public ItemStack getStack() {
        return getItem();
    }

    @Nonnull
    public ItemStack getItem() {
        return slot.getStack();
    }

    @Override
    public boolean getHasStack() {
        return hasItem();
    }

    public boolean hasItem() {
        return !slot.isEmpty();
    }

    @Override
    public boolean canTakeStack(@Nonnull EntityPlayer playerIn) {
        return mayPickup(playerIn);
    }

    public boolean mayPickup(@Nonnull EntityPlayer player) {
        return !slot.extractItem(1, Action.SIMULATE, AutomationType.MANUAL).isEmpty();
    }

    @Override
    public void putStack(@Nonnull ItemStack stack) {
        set(stack);
    }

    public void set(@Nonnull ItemStack stack) {
        uncheckedStackSetter.accept(stack);
        setChanged();
    }

    @Nonnull
    @Override
    public ItemStack decrStackSize(int amount) {
        return remove(amount);
    }

    @Nonnull
    public ItemStack remove(int amount) {
        return slot.extractItem(amount, Action.EXECUTE, AutomationType.MANUAL);
    }

    @Override
    public int getSlotStackLimit() {
        return getMaxStackSize();
    }

    public int getMaxStackSize() {
        return slot.getLimit(ItemStack.EMPTY);
    }

    @Override
    public int getItemStackLimit(@Nonnull ItemStack stack) {
        return getMaxStackSize(stack);
    }

    public int getMaxStackSize(@Nonnull ItemStack stack) {
        return slot.getLimit(stack);
    }

    @Override
    public void onSlotChanged() {
        setChanged();
    }

    public void setChanged() {
        super.onSlotChanged();
        slot.onContentsChanged();
    }

    public boolean mayPlace(@Nonnull ItemStack stack) {
        return isItemValid(stack);
    }
}
