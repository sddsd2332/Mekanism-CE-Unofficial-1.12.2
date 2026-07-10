package mekanism.common.capabilities.fluid;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.IContentsListenerRegistry;
import mekanism.api.fluid.ExtendedFluidHandlerUtils;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.functions.ConstantPredicates;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class BasicFluidTank implements IExtendedFluidTank, IContentsListenerRegistry {

    public static final Predicate<FluidStack> alwaysTrue = ConstantPredicates.alwaysTrue();
    public static final Predicate<FluidStack> alwaysFalse = ConstantPredicates.alwaysFalse();
    public static final BiPredicate<FluidStack, AutomationType> alwaysTrueBi = ConstantPredicates.alwaysTrueBi();
    public static final BiPredicate<FluidStack, AutomationType> internalOnly = ConstantPredicates.internalOnly();
    public static final BiPredicate<FluidStack, AutomationType> notExternal = ConstantPredicates.notExternal();

    public static BasicFluidTank create(int capacity, @Nullable IContentsListener listener) {
        return create(capacity, alwaysTrueBi, alwaysTrueBi, alwaysTrue, listener);
    }

    public static BasicFluidTank create(int capacity, Predicate<FluidStack> validator, @Nullable IContentsListener listener) {
        return create(capacity, alwaysTrueBi, alwaysTrueBi, validator, listener);
    }

    public static BasicFluidTank create(int capacity, Predicate<FluidStack> canExtract, Predicate<FluidStack> canInsert, @Nullable IContentsListener listener) {
        return create(capacity, canExtract, canInsert, alwaysTrue, listener);
    }

    public static BasicFluidTank input(int capacity, Predicate<FluidStack> validator, @Nullable IContentsListener listener) {
        return create(capacity, notExternal, alwaysTrueBi, validator, listener);
    }

    public static BasicFluidTank input(int capacity, Predicate<FluidStack> canInsert, Predicate<FluidStack> validator, @Nullable IContentsListener listener) {
        return create(capacity, notExternal, (stack, automationType) -> canInsert.test(stack), validator, listener);
    }

    public static BasicFluidTank output(int capacity, @Nullable IContentsListener listener) {
        return create(capacity, alwaysTrueBi, internalOnly, alwaysTrue, listener);
    }

    public static BasicFluidTank create(int capacity, Predicate<FluidStack> canExtract, Predicate<FluidStack> canInsert, Predicate<FluidStack> validator,
          @Nullable IContentsListener listener) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity must be at least zero");
        }
        Objects.requireNonNull(canExtract, "Extraction validity check cannot be null");
        Objects.requireNonNull(canInsert, "Insertion validity check cannot be null");
        Objects.requireNonNull(validator, "Fluid validity check cannot be null");
        return new BasicFluidTank(capacity, canExtract, canInsert, validator, listener);
    }

    public static BasicFluidTank create(int capacity, BiPredicate<FluidStack, AutomationType> canExtract, BiPredicate<FluidStack, AutomationType> canInsert,
          Predicate<FluidStack> validator, @Nullable IContentsListener listener) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity must be at least zero");
        }
        Objects.requireNonNull(canExtract, "Extraction validity check cannot be null");
        Objects.requireNonNull(canInsert, "Insertion validity check cannot be null");
        Objects.requireNonNull(validator, "Fluid validity check cannot be null");
        return new BasicFluidTank(capacity, canExtract, canInsert, validator, listener);
    }

    @Nullable
    protected FluidStack stored;
    private final Predicate<FluidStack> validator;
    protected final BiPredicate<FluidStack, AutomationType> canExtract;
    protected final BiPredicate<FluidStack, AutomationType> canInsert;
    private final int capacity;
    @Nullable
    private final IContentsListener listener;
    @Nullable
    private volatile IContentsListener[] additionalListeners;

    protected BasicFluidTank(int capacity, Predicate<FluidStack> canExtract, Predicate<FluidStack> canInsert, Predicate<FluidStack> validator,
          @Nullable IContentsListener listener) {
        this(capacity, (stack, automationType) -> automationType == AutomationType.MANUAL || canExtract.test(stack), (stack, automationType) -> canInsert.test(stack),
              validator, listener);
    }

    protected BasicFluidTank(int capacity, BiPredicate<FluidStack, AutomationType> canExtract, BiPredicate<FluidStack, AutomationType> canInsert,
          Predicate<FluidStack> validator, @Nullable IContentsListener listener) {
        this.capacity = capacity;
        this.canExtract = canExtract;
        this.canInsert = canInsert;
        this.validator = validator;
        this.listener = listener;
    }

    @Override
    public void onContentsChanged() {
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

    @Override
    @Nullable
    public FluidStack getFluid() {
        return stored;
    }

    @Override
    public void setStack(@Nullable FluidStack stack) {
        setStack(stack, true);
    }

    @Override
    public void setStackUnchecked(@Nullable FluidStack stack) {
        setStack(stack, false);
    }

    public void setFluid(@Nullable FluidStack stack) {
        setStackUnchecked(stack);
    }

    protected int getRate(@Nullable AutomationType automationType) {
        return Integer.MAX_VALUE;
    }

    private void setStack(@Nullable FluidStack stack, boolean validateStack) {
        if (ExtendedFluidHandlerUtils.isEmpty(stack)) {
            if (stored == null) {
                return;
            }
            stored = null;
        } else if (!validateStack || isFluidValid(stack)) {
            stored = stack.copy();
        } else {
            throw new RuntimeException("Invalid fluid for tank: " + stack.getFluid().getName() + " " + stack.amount);
        }
        onContentsChanged();
    }

    @Override
    @Nullable
    public FluidStack insert(@Nullable FluidStack stack, Action action, AutomationType automationType) {
        if (ExtendedFluidHandlerUtils.isEmpty(stack) || !isFluidValid(stack) || !canInsert.test(stack, automationType)) {
            return stack;
        }
        int needed = Math.min(getRate(automationType), getNeeded());
        if (needed <= 0) {
            return stack;
        }
        boolean sameType = false;
        if (isEmpty() || (sameType = isFluidEqual(stack))) {
            int toAdd = Math.min(stack.amount, needed);
            if (action.execute()) {
                if (sameType) {
                    stored.amount += toAdd;
                    onContentsChanged();
                } else {
                    setStackUnchecked(new FluidStack(stack, toAdd));
                }
            }
            return stack.amount == toAdd ? null : new FluidStack(stack, stack.amount - toAdd);
        }
        return stack;
    }

    @Override
    @Nullable
    public FluidStack extract(int amount, Action action, AutomationType automationType) {
        if (isEmpty() || amount < 1 || !canExtract.test(stored, automationType)) {
            return null;
        }
        int size = Math.min(Math.min(getRate(automationType), getFluidAmount()), amount);
        if (size <= 0) {
            return null;
        }
        FluidStack ret = new FluidStack(stored, size);
        if (action.execute()) {
            stored.amount -= ret.amount;
            if (stored.amount <= 0) {
                stored = null;
            }
            onContentsChanged();
        }
        return ret;
    }

    @Override
    public boolean isFluidValid(@Nullable FluidStack stack) {
        return stack != null && validator.test(stack);
    }

    @Override
    public int setStackSize(int amount, Action action) {
        if (isEmpty()) {
            return 0;
        } else if (amount <= 0) {
            if (action.execute()) {
                setEmpty();
            }
            return 0;
        }
        int maxStackSize = getCapacity();
        if (amount > maxStackSize) {
            amount = maxStackSize;
        }
        if (getFluidAmount() == amount || action.simulate()) {
            return amount;
        }
        stored.amount = amount;
        onContentsChanged();
        return amount;
    }

    @Override
    public int growStack(int amount, Action action) {
        int current = getFluidAmount();
        if (current == 0) {
            return 0;
        } else if (amount > 0) {
            amount = Math.min(Math.min(amount, getNeeded()), getRate(null));
        } else if (amount < 0) {
            amount = Math.max(amount, -getRate(null));
        }
        int newSize = setStackSize(current + amount, action);
        return newSize - current;
    }

    @Override
    public boolean isEmpty() {
        return stored == null || stored.amount <= 0;
    }

    @Override
    public boolean isFluidEqual(@Nullable FluidStack other) {
        return stored != null && stored.isFluidEqual(other);
    }

    @Override
    public int getFluidAmount() {
        return stored == null ? 0 : stored.amount;
    }

    @Override
    public int getCapacity() {
        return capacity;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return IExtendedFluidTank.super.serializeNBT();
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        IExtendedFluidTank.super.deserializeNBT(nbt);
    }

    public BasicFluidTank readFromNBT(NBTTagCompound nbt) {
        if (!nbt.hasKey("Empty")) {
            setStackUnchecked(FluidStack.loadFluidStackFromNBT(nbt));
        } else {
            setEmpty();
        }
        return this;
    }

    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        if (stored != null) {
            stored.writeToNBT(nbt);
        } else {
            nbt.setString("Empty", "");
        }
        return nbt;
    }
}
