package mekanism.common.inventory.slot;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.IContentsListenerRegistry;
import mekanism.api.IContentsSnapshot;
import mekanism.api.NBTConstants;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.qio.resource.QIOResourceDescriptor;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.warning.ISupportsWarning;
import mekanism.common.util.MachineStressDiagnostics;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.*;

public class BasicInventorySlot implements IInventorySlot, IContentsListenerRegistry, IContentsSnapshot {

    private static final ClassValue<Boolean> CUSTOM_INSERT = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            Class<?> current = type;
            while (current != null && BasicInventorySlot.class.isAssignableFrom(current)) {
                try {
                    current.getDeclaredMethod("insertItem", ItemStack.class, Action.class, AutomationType.class);
                    return current != BasicInventorySlot.class;
                } catch (NoSuchMethodException ignored) {
                    current = current.getSuperclass();
                }
            }
            return false;
        }
    };

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
    private BooleanSupplier transferAllowed = () -> true;
    private final BiPredicate<ItemStack, AutomationType> canExtract;

    public void setTransferAllowed(BooleanSupplier transferAllowed) {
        this.transferAllowed = Objects.requireNonNull(transferAllowed);
    }

    public boolean isTransferAllowed() {
        return transferAllowed.getAsBoolean();
    }
    private final BiPredicate<ItemStack, AutomationType> canInsert;
    private final Predicate<ItemStack> validator;
    private final int limit;
    @Nullable
    private final IContentsListener listener;
    @Nullable
    private volatile IContentsListener[] additionalListeners;
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
        if (MachineStressDiagnostics.ENABLED) MachineStressDiagnostics.record(this, notifyChange ? "slot_set_write" : "slot_quiet_write");
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
                    if (MachineStressDiagnostics.ENABLED) MachineStressDiagnostics.record(this, "slot_grow_write");
                } else {
                    ItemStack toSet = stack.copy();
                    toSet.setCount(toAdd);
                    setStackUnchecked(toSet);
                }
            }
            if (toAdd == stack.getCount()) {
                return ItemStack.EMPTY;
            }
            ItemStack remainder = stack.copy();
            remainder.setCount(stack.getCount() - toAdd);
            return remainder;
        }
        return stack;
    }

    @Override
    public int insertItemCount(@Nonnull ItemStack stack, @Nonnull Action action, @Nonnull AutomationType automationType) {
        // Preserve special slot subclasses which redefine the remainder-based contract.
        if (CUSTOM_INSERT.get(getClass())) {
            return IInventorySlot.super.insertItemCount(stack, action, automationType);
        }
        if (stack.isEmpty()) {
            return 0;
        }
        int needed = getLimit(stack) - current.getCount();
        if (needed <= 0 || !isItemValidForInsertion(stack, automationType)) {
            return 0;
        }
        boolean sameType = false;
        if (current.isEmpty() || (sameType = ItemHandlerHelper.canItemStacksStack(current, stack))) {
            int toAdd = Math.min(stack.getCount(), needed);
            if (action.execute()) {
                if (sameType) {
                    current.grow(toAdd);
                    if (MachineStressDiagnostics.ENABLED) MachineStressDiagnostics.record(this, "slot_grow_write");
                    onContentsChanged();
                } else {
                    ItemStack toSet = stack.copy();
                    toSet.setCount(toAdd);
                    setStackUnchecked(toSet);
                }
            }
            return toAdd;
        }
        return 0;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int amount, @Nonnull Action action, @Nonnull AutomationType automationType) {
        if (!isTransferAllowed() || isEmpty() || amount < 1 || !canExtract.test(current, automationType)) {
            return ItemStack.EMPTY;
        }
        amount = Math.min(amount, Math.min(getCount(), current.getMaxStackSize()));
        ItemStack toReturn = current.copy();
        toReturn.setCount(amount);
        if (action.execute()) {
            current.shrink(amount);
            if (MachineStressDiagnostics.ENABLED) MachineStressDiagnostics.record(this, "slot_extract_write");
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
    public boolean mayHaveSpaceForInsertion() {
        // These exact classes use our hard upper bound for every insertion. Subclasses may replace that contract.
        Class<?> type = getClass();
        if (type != BasicInventorySlot.class && type != InputInventorySlot.class &&
            type != FactoryInputInventorySlot.class && type != OutputInventorySlot.class) {
            return true;
        }
        // Do not call item limits or validators: they can depend on the offered item, count, or capabilities.
        return current.isEmpty() || current.getCount() < limit;
    }

    @Override
    public int getResourceCapacity(QIOResourceDescriptor resource, int templateCount, boolean checkInsertion,
          AutomationType automationType) {
        if (templateCount <= 0 || checkInsertion && !isTransferAllowed()) return 0;
        // Exact classes only: subclasses may override limits, insertion, or stack access.
        boolean plainSlot = getClass() == BasicInventorySlot.class || getClass() == OutputInventorySlot.class;
        boolean plainInsertion = validator == alwaysTrue && (canInsert == alwaysTrueBi ||
              canInsert == internalOnly && automationType == AutomationType.INTERNAL);
        if (plainSlot && (!checkInsertion || plainInsertion)) {
            int space = resource.getPlainItemSpace(current, limit, obeyStackLimit);
            if (space >= 0) return space;
        }
        return IInventorySlot.super.getResourceCapacity(resource, templateCount, checkInsertion, automationType);
    }

    @Override
    public boolean isItemValid(@Nonnull ItemStack stack) {
        return validator.test(stack);
    }

    public boolean isItemValidForInsertion(@Nonnull ItemStack stack, @Nonnull AutomationType automationType) {
        return isTransferAllowed() && validator.test(stack) && canInsert.test(stack, automationType);
    }

    @Override
    public void onContentsChanged() {
        MachineStressDiagnostics.Source previous = MachineStressDiagnostics.ENABLED ? MachineStressDiagnostics.beginNotification(this) : null;
        try {
            notifyContentsListeners();
        } finally {
            if (MachineStressDiagnostics.ENABLED) MachineStressDiagnostics.endNotification(previous);
        }
    }

    private void notifyContentsListeners() {
        if (listener != null) {
            listener.onContentsChanged();
        }
        IContentsListener[] listeners = additionalListeners;
        if (listeners != null) {
            for (IContentsListener additionalListener : listeners) {
                additionalListener.onContentsChanged();
            }
        }
    }

    @Override
    public synchronized boolean addContentsListener(IContentsListener listener) {
        if (listener == null || listener == this || listener == this.listener) {
            return false;
        }
        IContentsListener[] listeners = additionalListeners;
        if (listeners == null) {
            additionalListeners = new IContentsListener[]{listener};
            return true;
        }
        for (IContentsListener existing : listeners) {
            if (existing == listener) {
                return false;
            }
        }
        IContentsListener[] updated = new IContentsListener[listeners.length + 1];
        System.arraycopy(listeners, 0, updated, 0, listeners.length);
        updated[listeners.length] = listener;
        additionalListeners = updated;
        return true;
    }

    @Override
    public synchronized boolean removeContentsListener(IContentsListener listener) {
        if (listener == null) {
            return false;
        }
        IContentsListener[] listeners = additionalListeners;
        if (listeners == null) {
            return false;
        }
        for (int i = 0; i < listeners.length; i++) {
            if (listeners[i] == listener) {
                if (listeners.length == 1) {
                    additionalListeners = null;
                } else {
                    IContentsListener[] updated = new IContentsListener[listeners.length - 1];
                    System.arraycopy(listeners, 0, updated, 0, i);
                    System.arraycopy(listeners, i + 1, updated, i, listeners.length - i - 1);
                    additionalListeners = updated;
                }
                return true;
            }
        }
        return false;
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
        if (MachineStressDiagnostics.ENABLED) MachineStressDiagnostics.record(this, "slot_count_write");
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

    @Override
    public NBTTagCompound createContentsSnapshot() {
        return serializeNBT();
    }

    @Override
    public void restoreContentsSnapshot(NBTTagCompound snapshot) {
        ItemStack stack = ItemStack.EMPTY;
        if (snapshot.hasKey(NBTConstants.ITEM, NBT.TAG_COMPOUND)) {
            stack = new ItemStack(snapshot.getCompoundTag(NBTConstants.ITEM));
            if (snapshot.hasKey(NBTConstants.SIZE_OVERRIDE, NBT.TAG_INT)) {
                stack.setCount(snapshot.getInteger(NBTConstants.SIZE_OVERRIDE));
            }
        }
        setStackUncheckedNoUpdate(stack);
    }

    private static void validateSlotLimit(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Slots with a custom limit must allow at least one item");
        }
    }
}
