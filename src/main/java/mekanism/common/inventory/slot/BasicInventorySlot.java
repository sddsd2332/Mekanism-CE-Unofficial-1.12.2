package mekanism.common.inventory.slot;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.NBTConstants;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.warning.ISupportsWarning;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.*;

public class BasicInventorySlot implements IInventorySlot {

    public static final Predicate<ItemStack> alwaysTrue = ConstantPredicates.alwaysTrue();
    public static final Predicate<ItemStack> alwaysFalse = ConstantPredicates.alwaysFalse();
    public static final BiPredicate<ItemStack, AutomationType> alwaysTrueBi = ConstantPredicates.alwaysTrueBi();
    public static final BiPredicate<ItemStack, AutomationType> manualOnly = ConstantPredicates.manualOnly();
    public static final BiPredicate<ItemStack, AutomationType> internalOnly = ConstantPredicates.internalOnly();
    public static final BiPredicate<ItemStack, AutomationType> notExternal = ConstantPredicates.notExternal();
    public static final int DEFAULT_LIMIT = 64;

    public static BasicInventorySlot at(@Nullable IContentsListener listener, int x, int y) {
        return at(alwaysTrue, listener, x, y);
    }

    public static BasicInventorySlot at(Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        return at(validator, listener, x, y, DEFAULT_LIMIT);
    }

    public static BasicInventorySlot at(Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y, int limit) {
        Objects.requireNonNull(validator, "Item validity check cannot be null");
        validateSlotLimit(limit);
        return new BasicInventorySlot(limit, alwaysTrueBi, alwaysTrueBi, validator, listener, x, y);
    }

    public static BasicInventorySlot at(Predicate<ItemStack> canExtract, Predicate<ItemStack> canInsert, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(canExtract, "Extraction validity check cannot be null");
        Objects.requireNonNull(canInsert, "Insertion validity check cannot be null");
        return new BasicInventorySlot(canExtract, canInsert, alwaysTrue, listener, x, y);
    }

    public static BasicInventorySlot at(BiPredicate<ItemStack, AutomationType> canExtract, BiPredicate<ItemStack, AutomationType> canInsert,
          @Nullable IContentsListener listener, int x, int y) {
        return at(canExtract, canInsert, alwaysTrue, listener, x, y);
    }

    public static BasicInventorySlot at(BiPredicate<ItemStack, AutomationType> canExtract, BiPredicate<ItemStack, AutomationType> canInsert,
          Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(canExtract, "Extraction validity check cannot be null");
        Objects.requireNonNull(canInsert, "Insertion validity check cannot be null");
        Objects.requireNonNull(validator, "Item validity check cannot be null");
        return new BasicInventorySlot(canExtract, canInsert, validator, listener, x, y);
    }

    protected ItemStack current = ItemStack.EMPTY;
    private final BiPredicate<ItemStack, AutomationType> canExtract;
    private final BiPredicate<ItemStack, AutomationType> canInsert;
    private final Predicate<ItemStack> validator;
    private final int limit;
    @Nullable
    private final IContentsListener listener;
    private final int x;
    private final int y;
    private IntSupplier xSupplier;
    private IntSupplier ySupplier;
    protected boolean obeyStackLimit = true;
    private ContainerSlotType slotType = ContainerSlotType.NORMAL;
    @Nullable
    private SlotOverlay slotOverlay;
    @Nullable
    private Consumer<ISupportsWarning<?>> warningAdder;
    private BooleanSupplier enabledSupplier = () -> true;

    protected BasicInventorySlot(Predicate<ItemStack> canExtract, Predicate<ItemStack> canInsert, Predicate<ItemStack> validator,
          @Nullable IContentsListener listener, int x, int y) {
        this((stack, automationType) -> automationType == AutomationType.MANUAL || canExtract.test(stack),
              (stack, automationType) -> canInsert.test(stack), validator, listener, x, y);
    }

    protected BasicInventorySlot(BiPredicate<ItemStack, AutomationType> canExtract, BiPredicate<ItemStack, AutomationType> canInsert,
          Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        this(DEFAULT_LIMIT, canExtract, canInsert, validator, listener, x, y);
    }

    protected BasicInventorySlot(int limit, BiPredicate<ItemStack, AutomationType> canExtract, BiPredicate<ItemStack, AutomationType> canInsert,
          Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        validateSlotLimit(limit);
        this.limit = limit;
        this.canExtract = Objects.requireNonNull(canExtract, "Extraction validity check cannot be null");
        this.canInsert = Objects.requireNonNull(canInsert, "Insertion validity check cannot be null");
        this.validator = Objects.requireNonNull(validator, "Item validity check cannot be null");
        this.listener = listener;
        this.x = x;
        this.y = y;
        xSupplier = () -> this.x;
        ySupplier = () -> this.y;
    }

    public int getGuiX() {
        return xSupplier.getAsInt();
    }

    public int getGuiY() {
        return ySupplier.getAsInt();
    }

    @Nonnull
    @Override
    public ItemStack getStack() {
        return current;
    }

    @Override
    public void setStack(@Nonnull ItemStack stack) {
        setStack(stack, true);
    }

    public void setStackUnchecked(@Nonnull ItemStack stack) {
        setStack(stack, false);
    }

    private void setStack(@Nonnull ItemStack stack, boolean validateStack) {
        setStack(stack, validateStack, true);
    }

    public void setStackUncheckedNoUpdate(@Nonnull ItemStack stack) {
        setStack(stack, false, false);
    }

    private void setStack(@Nonnull ItemStack stack, boolean validateStack, boolean notifyChange) {
        if (stack.isEmpty()) {
            if (current.isEmpty()) {
                return;
            }
            current = ItemStack.EMPTY;
        } else if (!validateStack || isItemValid(stack)) {
            current = stack.copy();
        } else {
            throw new RuntimeException("Invalid stack for slot: " + stack);
        }
        if (notifyChange) {
            onContentsChanged();
        }
    }

    @Nonnull
    @Override
    public ItemStack insertItem(@Nonnull ItemStack stack, @Nonnull Action action, @Nonnull AutomationType automationType) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int needed = getLimit(stack) - current.getCount();
        if (needed <= 0 || !isItemValidForInsertion(stack, automationType)) {
            return stack;
        }
        boolean sameType = false;
        if (current.isEmpty() || (sameType = ItemHandlerHelper.canItemStacksStack(current, stack))) {
            int toAdd = Math.min(stack.getCount(), needed);
            if (action.execute()) {
                if (sameType) {
                    current.grow(toAdd);
                    onContentsChanged();
                } else {
                    ItemStack toSet = stack.copy();
                    toSet.setCount(toAdd);
                    setStackUnchecked(toSet);
                }
            }
            ItemStack remainder = stack.copy();
            remainder.setCount(stack.getCount() - toAdd);
            return remainder;
        }
        return stack;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int amount, @Nonnull Action action, @Nonnull AutomationType automationType) {
        if (isEmpty() || amount < 1 || !canExtract.test(current, automationType)) {
            return ItemStack.EMPTY;
        }
        amount = Math.min(amount, Math.min(getCount(), current.getMaxStackSize()));
        ItemStack toReturn = current.copy();
        toReturn.setCount(amount);
        if (action.execute()) {
            current.shrink(amount);
            if (current.getCount() <= 0) {
                current = ItemStack.EMPTY;
            }
            onContentsChanged();
        }
        return toReturn;
    }

    @Override
    public int getLimit(@Nonnull ItemStack stack) {
        return obeyStackLimit && !stack.isEmpty() ? Math.min(limit, stack.getMaxStackSize()) : limit;
    }

    @Override
    public boolean isItemValid(@Nonnull ItemStack stack) {
        return validator.test(stack);
    }

    public boolean isItemValidForInsertion(@Nonnull ItemStack stack, @Nonnull AutomationType automationType) {
        return validator.test(stack) && canInsert.test(stack, automationType);
    }

    @Override
    public void onContentsChanged() {
        if (listener != null) {
            listener.onContentsChanged();
        }
    }

    @Nullable
    @Override
    public InventoryContainerSlot createContainerSlot() {
        return new InventoryContainerSlot(this, xSupplier.getAsInt(), ySupplier.getAsInt(), slotType, slotOverlay, warningAdder, this::setStackUnchecked) {
            @Override
            public boolean isEnabled() {
                return enabledSupplier.getAsBoolean();
            }
        };
    }

    public void setSlotType(@Nonnull ContainerSlotType slotType) {
        this.slotType = slotType;
    }

    public void tracksWarnings(@Nullable Consumer<ISupportsWarning<?>> warningAdder) {
        this.warningAdder = warningAdder;
    }

    public void setSlotOverlay(@Nullable SlotOverlay slotOverlay) {
        this.slotOverlay = slotOverlay;
    }

    public void setEnabledSupplier(@Nonnull BooleanSupplier enabledSupplier) {
        this.enabledSupplier = Objects.requireNonNull(enabledSupplier, "Enabled supplier cannot be null");
    }

    public void setPositionSuppliers(@Nonnull IntSupplier xSupplier, @Nonnull IntSupplier ySupplier) {
        this.xSupplier = Objects.requireNonNull(xSupplier, "X supplier cannot be null");
        this.ySupplier = Objects.requireNonNull(ySupplier, "Y supplier cannot be null");
    }

    @Nullable
    protected final SlotOverlay getSlotOverlay() {
        return slotOverlay;
    }

    protected final ContainerSlotType getSlotType() {
        return slotType;
    }

    @Override
    public int setStackSize(int amount, @Nonnull Action action) {
        if (isEmpty()) {
            return 0;
        } else if (amount <= 0) {
            if (action.execute()) {
                setEmpty();
            }
            return 0;
        }
        int maxStackSize = getLimit(current);
        if (amount > maxStackSize) {
            amount = maxStackSize;
        }
        if (getCount() == amount || action.simulate()) {
            return amount;
        }
        current.setCount(amount);
        onContentsChanged();
        return amount;
    }

    @Override
    public int growStack(int amount, @Nonnull Action action) {
        int current = this.current.getCount();
        if (current == 0) {
            return 0;
        } else if (amount > 0) {
            amount = Math.min(amount, getLimit(this.current));
        }
        int newSize = setStackSize(current + amount, action);
        return newSize - current;
    }

    @Override
    public boolean isEmpty() {
        return current.isEmpty();
    }

    @Override
    public int getCount() {
        return current.getCount();
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        if (!isEmpty()) {
            NBTTagCompound itemTag = new NBTTagCompound();
            current.writeToNBT(itemTag);
            nbt.setTag(NBTConstants.ITEM, itemTag);
            if (getCount() > current.getMaxStackSize()) {
                nbt.setInteger(NBTConstants.SIZE_OVERRIDE, getCount());
            }
        }
        return nbt;
    }

    @Override
    public void deserializeNBT(@Nonnull NBTTagCompound nbt) {
        ItemStack stack = ItemStack.EMPTY;
        if (nbt.hasKey(NBTConstants.ITEM, NBT.TAG_COMPOUND)) {
            stack = new ItemStack(nbt.getCompoundTag(NBTConstants.ITEM));
            if (nbt.hasKey(NBTConstants.SIZE_OVERRIDE, NBT.TAG_INT)) {
                stack.setCount(nbt.getInteger(NBTConstants.SIZE_OVERRIDE));
            }
        }
        setStackUnchecked(stack);
    }

    private static void validateSlotLimit(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Slots with a custom limit must allow at least one item");
        }
    }
}
