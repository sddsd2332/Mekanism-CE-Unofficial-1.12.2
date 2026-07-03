package mekanism.common.capabilities.gas;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.common.multiblock.SynchronizedData;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

public class VariableCapacityGasTank extends BasicGasTank {

    public static VariableCapacityGasTank create(SynchronizedData<?> multiblock, IntSupplier capacity, Predicate<Gas> validator,
          @Nullable IContentsListener listener) {
        Objects.requireNonNull(multiblock, "Multiblock cannot be null");
        return createModern(multiblock, capacity, wrapValidatorToModern(validator), listener);
    }

    public static VariableCapacityGasTank input(SynchronizedData<?> multiblock, IntSupplier capacity, Predicate<Gas> validator,
          @Nullable IContentsListener listener) {
        Objects.requireNonNull(multiblock, "Multiblock cannot be null");
        return inputModern(multiblock, capacity, wrapValidatorToModern(validator), listener);
    }

    public static VariableCapacityGasTank output(SynchronizedData<?> multiblock, IntSupplier capacity, Predicate<Gas> validator,
          @Nullable IContentsListener listener) {
        Objects.requireNonNull(multiblock, "Multiblock cannot be null");
        return outputModern(multiblock, capacity, wrapValidatorToModern(validator), listener);
    }

    public static VariableCapacityGasTank input(IntSupplier capacity, Predicate<Gas> validator, @Nullable IContentsListener listener) {
        return inputModern(capacity, wrapValidatorToModern(validator), listener);
    }

    public static VariableCapacityGasTank output(IntSupplier capacity, Predicate<Gas> validator, @Nullable IContentsListener listener) {
        return outputModern(capacity, wrapValidatorToModern(validator), listener);
    }

    public static VariableCapacityGasTank create(IntSupplier capacity, BiPredicate<Gas, AutomationType> canExtract,
          BiPredicate<Gas, AutomationType> canInsert, Predicate<Gas> validator, @Nullable IContentsListener listener) {
        Objects.requireNonNull(capacity, "Capacity supplier cannot be null");
        Objects.requireNonNull(canExtract, "Extraction validity check cannot be null");
        Objects.requireNonNull(canInsert, "Insertion validity check cannot be null");
        Objects.requireNonNull(validator, "Gas validity check cannot be null");
        return new VariableCapacityGasTank(capacity, canExtract, canInsert, validator, listener);
    }

    public static VariableCapacityGasTank createModern(SynchronizedData<?> multiblock, IntSupplier capacity, Predicate<GasStack> validator,
          @Nullable IContentsListener listener) {
        Objects.requireNonNull(multiblock, "Multiblock cannot be null");
        return createModern(capacity, multiblock.formedBiPred(), multiblock.formedBiPred(), validator, listener);
    }

    public static VariableCapacityGasTank inputModern(SynchronizedData<?> multiblock, IntSupplier capacity, Predicate<GasStack> validator,
          @Nullable IContentsListener listener) {
        Objects.requireNonNull(multiblock, "Multiblock cannot be null");
        return createModern(capacity, multiblock.notExternalFormedBiPred(), multiblock.formedBiPred(), validator, listener);
    }

    public static VariableCapacityGasTank outputModern(SynchronizedData<?> multiblock, IntSupplier capacity, Predicate<GasStack> validator,
          @Nullable IContentsListener listener) {
        Objects.requireNonNull(multiblock, "Multiblock cannot be null");
        return createModern(capacity, multiblock.formedBiPred(), multiblock.notExternalFormedBiPred(), validator, listener);
    }

    public static VariableCapacityGasTank inputModern(IntSupplier capacity, Predicate<GasStack> validator, @Nullable IContentsListener listener) {
        return createModern(capacity, ConstantPredicates.notExternal(), ConstantPredicates.alwaysTrueBi(), validator, listener);
    }

    public static VariableCapacityGasTank outputModern(IntSupplier capacity, Predicate<GasStack> validator, @Nullable IContentsListener listener) {
        return createModern(capacity, ConstantPredicates.alwaysTrueBi(), ConstantPredicates.internalOnly(), validator, listener);
    }

    public static VariableCapacityGasTank createModern(IntSupplier capacity, BiPredicate<GasStack, AutomationType> canExtract,
          BiPredicate<GasStack, AutomationType> canInsert, Predicate<GasStack> validator, @Nullable IContentsListener listener) {
        Objects.requireNonNull(capacity, "Capacity supplier cannot be null");
        Objects.requireNonNull(canExtract, "Extraction validity check cannot be null");
        Objects.requireNonNull(canInsert, "Insertion validity check cannot be null");
        Objects.requireNonNull(validator, "Gas validity check cannot be null");
        return new VariableCapacityGasTank(capacity, canExtract, canInsert, validator, listener, null);
    }

    private final IntSupplier capacity;

    protected VariableCapacityGasTank(IntSupplier capacity, BiPredicate<Gas, AutomationType> canExtract,
          BiPredicate<Gas, AutomationType> canInsert, Predicate<Gas> validator, @Nullable IContentsListener listener) {
        super(capacity.getAsInt(), canExtract, canInsert, validator, listener);
        this.capacity = capacity;
    }

    protected VariableCapacityGasTank(IntSupplier capacity, BiPredicate<GasStack, AutomationType> canExtract,
          BiPredicate<GasStack, AutomationType> canInsert, Predicate<GasStack> validator, @Nullable IContentsListener listener, @Nullable Void ignored) {
        super(capacity.getAsInt(), canExtract, canInsert, validator, listener, null);
        this.capacity = capacity;
    }

    @Override
    public int getCapacity() {
        return capacity.getAsInt();
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
        if (maxStackSize > 0 && amount > maxStackSize) {
            amount = maxStackSize;
        }
        if (getGasAmount() == amount || action.simulate()) {
            return amount;
        }
        stored.amount = amount;
        onContentsChanged();
        return amount;
    }
}
