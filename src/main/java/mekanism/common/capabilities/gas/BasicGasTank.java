package mekanism.common.capabilities.gas;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.IContentsListenerRegistry;
import mekanism.api.IContentsSnapshot;
import mekanism.api.NBTConstants;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTank;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.config.MekanismConfig;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class BasicGasTank extends GasTank implements IExtendedGasTank, IContentsListenerRegistry, IContentsSnapshot {

    public static final Predicate<Gas> alwaysTrue = ConstantPredicates.alwaysTrue();
    public static final Predicate<Gas> alwaysFalse = ConstantPredicates.alwaysFalse();
    public static final BiPredicate<Gas, AutomationType> alwaysTrueBi = ConstantPredicates.alwaysTrueBi();
    public static final BiPredicate<Gas, AutomationType> internalOnly = ConstantPredicates.internalOnly();
    public static final BiPredicate<Gas, AutomationType> notExternal = ConstantPredicates.notExternal();
    public static final BiPredicate<Gas, AutomationType> manualOnly = ConstantPredicates.manualOnly();

    public static BasicGasTank create(int capacity, @Nullable IContentsListener listener) {
        return createModern(capacity, listener);
    }

    public static BasicGasTank create(int capacity, Predicate<Gas> validator, @Nullable IContentsListener listener) {
        return createModern(capacity, wrapValidatorToModern(validator), listener);
    }

    public static BasicGasTank create(int capacity, Predicate<Gas> canExtract, Predicate<Gas> canInsert, @Nullable IContentsListener listener) {
        return create(capacity, canExtract, canInsert, alwaysTrue, listener);
    }

    public static BasicGasTank input(int capacity, Predicate<Gas> validator, @Nullable IContentsListener listener) {
        return inputModern(capacity, wrapValidatorToModern(validator), listener);
    }

    public static BasicGasTank input(int capacity, Predicate<Gas> canInsert, Predicate<Gas> validator, @Nullable IContentsListener listener) {
        return inputModern(capacity, wrapValidatorToModern(canInsert), wrapValidatorToModern(validator), listener);
    }

    public static BasicGasTank output(int capacity, @Nullable IContentsListener listener) {
        return createModern(capacity, ConstantPredicates.alwaysTrueBi(), ConstantPredicates.internalOnly(), ConstantPredicates.alwaysTrue(), listener);
    }

    public static BasicGasTank output(int capacity, Predicate<Gas> validator, @Nullable IContentsListener listener) {
        return outputModern(capacity, wrapValidatorToModern(validator), listener);
    }

    public static BasicGasTank create(int capacity, Predicate<Gas> canExtract, Predicate<Gas> canInsert, Predicate<Gas> validator,
          @Nullable IContentsListener listener) {
        return createModern(capacity, wrapValidatorToModern(canExtract), wrapValidatorToModern(canInsert), wrapValidatorToModern(validator), listener);
    }

    public static BasicGasTank create(int capacity, BiPredicate<Gas, AutomationType> canExtract, BiPredicate<Gas, AutomationType> canInsert,
          Predicate<Gas> validator, @Nullable IContentsListener listener) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity must be at least zero");
        }
        Objects.requireNonNull(canExtract, "Extraction validity check cannot be null");
        Objects.requireNonNull(canInsert, "Insertion validity check cannot be null");
        Objects.requireNonNull(validator, "Gas validity check cannot be null");
        return new BasicGasTank(capacity, canExtract, canInsert, validator, listener);
    }

    public static BasicGasTank createModern(int capacity, @Nullable IContentsListener listener) {
        return createModern(capacity, ConstantPredicates.alwaysTrueBi(), ConstantPredicates.alwaysTrueBi(), ConstantPredicates.alwaysTrue(), listener);
    }

    public static BasicGasTank createModern(int capacity, Predicate<GasStack> validator, @Nullable IContentsListener listener) {
        return createModern(capacity, ConstantPredicates.alwaysTrueBi(), ConstantPredicates.alwaysTrueBi(), validator, listener);
    }

    public static BasicGasTank createModern(int capacity, Predicate<GasStack> canExtract, Predicate<GasStack> canInsert,
          @Nullable IContentsListener listener) {
        return createModern(capacity, canExtract, canInsert, ConstantPredicates.alwaysTrue(), listener);
    }

    public static BasicGasTank inputModern(int capacity, Predicate<GasStack> validator, @Nullable IContentsListener listener) {
        return createModern(capacity, ConstantPredicates.notExternal(), ConstantPredicates.alwaysTrueBi(), validator, listener);
    }

    public static BasicGasTank inputModern(int capacity, Predicate<GasStack> canInsert, Predicate<GasStack> validator,
          @Nullable IContentsListener listener) {
        return createModern(capacity, ConstantPredicates.notExternal(), (stack, automationType) -> canInsert.test(stack), validator, listener);
    }

    public static BasicGasTank outputModern(int capacity, Predicate<GasStack> validator, @Nullable IContentsListener listener) {
        return createModern(capacity, ConstantPredicates.alwaysTrueBi(), ConstantPredicates.internalOnly(), validator, listener);
    }

    public static BasicGasTank createModern(int capacity, Predicate<GasStack> canExtract, Predicate<GasStack> canInsert,
          Predicate<GasStack> validator, @Nullable IContentsListener listener) {
        return createModern(capacity, (stack, automationType) -> automationType == AutomationType.MANUAL || canExtract.test(stack),
              (stack, automationType) -> canInsert.test(stack), validator, listener);
    }

    public static BasicGasTank createModern(int capacity, BiPredicate<GasStack, AutomationType> canExtract,
          BiPredicate<GasStack, AutomationType> canInsert, Predicate<GasStack> validator, @Nullable IContentsListener listener) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity must be at least zero");
        }
        Objects.requireNonNull(canExtract, "Extraction validity check cannot be null");
        Objects.requireNonNull(canInsert, "Insertion validity check cannot be null");
        Objects.requireNonNull(validator, "Gas validity check cannot be null");
        return new BasicGasTank(capacity, canExtract, canInsert, validator, listener, null);
    }

    @SuppressWarnings("unchecked")
    protected static BiPredicate<Gas, AutomationType> wrapAutomationPredicate(BiPredicate<GasStack, AutomationType> predicate) {
        if (predicate == ConstantPredicates.<GasStack, AutomationType>alwaysTrueBi() ||
            predicate == ConstantPredicates.<GasStack>internalOnly() ||
            predicate == ConstantPredicates.<GasStack>notExternal() ||
            predicate == ConstantPredicates.<GasStack>manualOnly()) {
            return (BiPredicate<Gas, AutomationType>) (BiPredicate<?, AutomationType>) predicate;
        }
        return (gas, automationType) -> gas != null && predicate.test(new GasStack(gas, 1), automationType);
    }

    @SuppressWarnings("unchecked")
    protected static BiPredicate<GasStack, AutomationType> wrapAutomationPredicateToModern(BiPredicate<Gas, AutomationType> predicate) {
        if (predicate == alwaysTrueBi || predicate == internalOnly || predicate == notExternal || predicate == manualOnly) {
            return (BiPredicate<GasStack, AutomationType>) (BiPredicate<?, AutomationType>) predicate;
        }
        return (stack, automationType) -> stack != null && stack.getGas() != null && predicate.test(stack.getGas(), automationType);
    }

    @SuppressWarnings("unchecked")
    protected static Predicate<GasStack> wrapValidatorToModern(Predicate<Gas> validator) {
        if (validator == alwaysTrue || validator == alwaysFalse) {
            return (Predicate<GasStack>) (Predicate<?>) validator;
        }
        return stack -> stack != null && stack.getGas() != null && validator.test(stack.getGas());
    }

    @SuppressWarnings("unchecked")
    protected static Predicate<Gas> wrapValidator(Predicate<GasStack> validator) {
        if (validator == ConstantPredicates.<GasStack>alwaysTrue() || validator == ConstantPredicates.<GasStack>alwaysFalse()) {
            return (Predicate<Gas>) (Predicate<?>) validator;
        }
        return gas -> gas != null && validator.test(new GasStack(gas, 1));
    }

    private final Predicate<GasStack> validator;
    protected final BiPredicate<GasStack, AutomationType> canExtractModern;
    protected final BiPredicate<GasStack, AutomationType> canInsertModern;
    protected final BiPredicate<Gas, AutomationType> canExtract;
    protected final BiPredicate<Gas, AutomationType> canInsert;
    private final int capacity;
    @Nullable
    private final IContentsListener listener;
    @Nullable
    private volatile IContentsListener[] additionalListeners;

    protected BasicGasTank(int capacity, BiPredicate<Gas, AutomationType> canExtract, BiPredicate<Gas, AutomationType> canInsert,
          Predicate<Gas> validator, @Nullable IContentsListener listener) {
        super(capacity);
        this.capacity = capacity;
        this.canExtract = canExtract;
        this.canInsert = canInsert;
        this.canExtractModern = wrapAutomationPredicateToModern(canExtract);
        this.canInsertModern = wrapAutomationPredicateToModern(canInsert);
        this.validator = wrapValidatorToModern(validator);
        this.listener = listener;
    }

    protected BasicGasTank(int capacity, BiPredicate<GasStack, AutomationType> canExtract, BiPredicate<GasStack, AutomationType> canInsert,
          Predicate<GasStack> validator, @Nullable IContentsListener listener, @Nullable Void ignored) {
        super(capacity);
        this.capacity = capacity;
        this.canExtractModern = canExtract;
        this.canInsertModern = canInsert;
        this.validator = validator;
        this.canExtract = wrapAutomationPredicate(canExtractModern);
        this.canInsert = wrapAutomationPredicate(canInsertModern);
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
    public void setStack(@Nullable GasStack stack) {
        setStack(stack, true, true);
    }

    @Override
    public void setStackUnchecked(@Nullable GasStack stack) {
        setStack(stack, false, true);
    }

    public void setStackUncheckedNoUpdate(@Nullable GasStack stack) {
        setStack(stack, false, false);
    }

    @Override
    public void setGas(@Nullable GasStack stack) {
        setStackUnchecked(stack);
    }

    protected int getInsertRate(@Nullable AutomationType automationType) {
        return Integer.MAX_VALUE;
    }

    protected int getExtractRate(@Nullable AutomationType automationType) {
        return Integer.MAX_VALUE;
    }

    private void setStack(@Nullable GasStack stack, boolean validateStack, boolean notifyChange) {
        if (stack == null || stack.amount <= 0) {
            if (stored == null) {
                return;
            }
            stored = null;
        } else if (!validateStack || isValid(stack)) {
            stored = stack.copy();
        } else {
            throw new RuntimeException("Invalid gas for tank: " + stack.getGas().getName() + " " + stack.amount);
        }
        if (notifyChange) {
            onContentsChanged();
        }
    }

    @Override
    public NBTTagCompound createContentsSnapshot() {
        return serializeNBT();
    }

    @Override
    public void restoreContentsSnapshot(NBTTagCompound snapshot) {
        setStackUncheckedNoUpdate(snapshot.hasKey(NBTConstants.STORED) ?
              GasStack.readFromNBT(snapshot.getCompoundTag(NBTConstants.STORED)) : null);
    }

    @Override
    @Nullable
    public synchronized GasStack draw(int amount, boolean doDraw) {
        return output(amount, doDraw);
    }

    @Override
    public synchronized int receive(@Nullable GasStack stack, boolean doReceive) {
        return input(stack, doReceive);
    }

    @Override
    public int input(@Nullable GasStack resource, boolean input) {
        return IExtendedGasTank.super.input(resource, input);
    }

    @Override
    @Nullable
    public GasStack output(int maxOutput, boolean output) {
        return IExtendedGasTank.super.output(maxOutput, output);
    }

    @Override
    @Nullable
    public GasStack insert(@Nullable GasStack stack, Action action, AutomationType automationType) {
        boolean sameType = false;
        if (stack == null || stack.amount <= 0 || !(isEmpty() || (sameType = isTypeEqual(stack)))) {
            return stack;
        }
        int needed = Math.min(getInsertRate(automationType), getNeeded());
        if (needed <= 0) {
            return stack;
        }
        if (!isValid(stack) || !canInsertModern.test(stack, automationType)) {
            return stack;
        }
        int toAdd = Math.min(stack.amount, needed);
        if (action.execute()) {
            if (sameType) {
                stored.amount += toAdd;
                onContentsChanged();
            } else {
                setStackUnchecked(stack.copy().withAmount(toAdd));
            }
        }
        return stack.amount == toAdd ? null : stack.copy().withAmount(stack.amount - toAdd);
    }

    @Override
    @Nullable
    public GasStack extract(int amount, Action action, AutomationType automationType) {
        if (stored == null || amount < 1 || !canExtractModern.test(stored, automationType)) {
            return null;
        }
        int size = Math.min(Math.min(getExtractRate(automationType), stored.amount), amount);
        if (size <= 0) {
            return null;
        }
        GasStack ret = stored.copy().withAmount(size);
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
    public boolean isValid(@Nullable GasStack stack) {
        return stack != null && stack.getGas() != null && validator.test(stack);
    }

    public boolean isValid(Gas gas) {
        return gas != null && validator.test(new GasStack(gas, 1));
    }

    public void clearIfInvalid() {
        clearIfInvalid(this::isValid);
    }

    public void clearIfInvalid(Predicate<Gas> validator) {
        if (MekanismConfig.current().general.voidInvalidGases.val()) {
            Gas gas = getGasType();
            if (gas != null && !validator.test(gas)) {
                setEmpty();
            }
        }
    }

    @Override
    public int setStackSize(int amount, Action action) {
        if (stored == null) {
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
        if (stored.amount == amount || action.simulate()) {
            return amount;
        }
        stored.amount = amount;
        onContentsChanged();
        return amount;
    }

    @Override
    public int growStack(int amount, Action action) {
        int current = getGasAmount();
        if (current == 0) {
            return 0;
        } else if (amount > 0) {
            amount = Math.min(Math.min(amount, getNeeded()), getInsertRate(null));
        } else if (amount < 0) {
            amount = Math.max(amount, -getExtractRate(null));
        }
        int newSize = setStackSize(current + amount, action);
        return newSize - current;
    }

    @Override
    public boolean isEmpty() {
        return stored == null || stored.amount <= 0;
    }

    @Override
    public int getGasAmount() {
        return stored == null ? 0 : stored.amount;
    }

    @Override
    public int getStored() {
        return getGasAmount();
    }

    @Override
    public int getMaxGas() {
        return getCapacity();
    }

    @Override
    public int getCapacity() {
        return capacity;
    }

    @Override
    public int getNeeded() {
        return Math.max(0, getCapacity() - getGasAmount());
    }

    @Override
    public boolean canReceive(Gas gas) {
        return isValid(gas) && getNeeded() > 0 && (stored == null || gas == null || gas == stored.getGas());
    }

    @Override
    public boolean canReceiveType(Gas gas) {
        return isValid(gas) && (stored == null || gas == null || gas == stored.getGas());
    }

    @Override
    public boolean canDraw(Gas gas) {
        return stored != null && (gas == null || gas == stored.getGas());
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return IExtendedGasTank.super.serializeNBT();
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        IExtendedGasTank.super.deserializeNBT(nbt);
    }

    @Override
    public void read(NBTTagCompound nbt) {
        if (nbt.hasKey(NBTConstants.STORED)) {
            setStackUnchecked(GasStack.readFromNBT(nbt.getCompoundTag(NBTConstants.STORED)));
        } else {
            setEmpty();
        }
    }

    @Override
    public NBTTagCompound write(NBTTagCompound nbt) {
        if (stored != null && stored.getGas() != null && stored.amount > 0) {
            nbt.setTag(NBTConstants.STORED, stored.write(new NBTTagCompound()));
        }
        return nbt;
    }
}
