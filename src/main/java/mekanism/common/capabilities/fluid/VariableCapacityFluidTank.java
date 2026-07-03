package mekanism.common.capabilities.fluid;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.common.multiblock.SynchronizedData;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

public class VariableCapacityFluidTank extends BasicFluidTank {

    public static VariableCapacityFluidTank create(SynchronizedData<?> multiblock, IntSupplier capacity, Predicate<FluidStack> validator,
          @Nullable IContentsListener listener) {
        Objects.requireNonNull(multiblock, "Multiblock cannot be null");
        Objects.requireNonNull(capacity, "Capacity supplier cannot be null");
        Objects.requireNonNull(validator, "Fluid validity check cannot be null");
        return new VariableCapacityFluidTank(capacity, multiblock.formedBiPred(), multiblock.formedBiPred(), validator, listener);
    }

    public static VariableCapacityFluidTank input(SynchronizedData<?> multiblock, IntSupplier capacity, Predicate<FluidStack> validator,
          @Nullable IContentsListener listener) {
        Objects.requireNonNull(multiblock, "Multiblock cannot be null");
        Objects.requireNonNull(capacity, "Capacity supplier cannot be null");
        Objects.requireNonNull(validator, "Fluid validity check cannot be null");
        return new VariableCapacityFluidTank(capacity, multiblock.notExternalFormedBiPred(), multiblock.formedBiPred(), validator, listener);
    }

    public static VariableCapacityFluidTank output(SynchronizedData<?> multiblock, IntSupplier capacity, Predicate<FluidStack> validator,
          @Nullable IContentsListener listener) {
        Objects.requireNonNull(multiblock, "Multiblock cannot be null");
        Objects.requireNonNull(capacity, "Capacity supplier cannot be null");
        Objects.requireNonNull(validator, "Fluid validity check cannot be null");
        return new VariableCapacityFluidTank(capacity, multiblock.formedBiPred(), multiblock.notExternalFormedBiPred(), validator, listener);
    }

    public static VariableCapacityFluidTank input(IntSupplier capacity, Predicate<FluidStack> validator, @Nullable IContentsListener listener) {
        Objects.requireNonNull(capacity, "Capacity supplier cannot be null");
        Objects.requireNonNull(validator, "Fluid validity check cannot be null");
        return new VariableCapacityFluidTank(capacity, notExternal, alwaysTrueBi, validator, listener);
    }

    public static VariableCapacityFluidTank output(IntSupplier capacity, Predicate<FluidStack> validator, @Nullable IContentsListener listener) {
        Objects.requireNonNull(capacity, "Capacity supplier cannot be null");
        Objects.requireNonNull(validator, "Fluid validity check cannot be null");
        return new VariableCapacityFluidTank(capacity, alwaysTrueBi, internalOnly, validator, listener);
    }

    public static VariableCapacityFluidTank create(IntSupplier capacity, BiPredicate<FluidStack, AutomationType> canExtract,
          BiPredicate<FluidStack, AutomationType> canInsert, Predicate<FluidStack> validator, @Nullable IContentsListener listener) {
        Objects.requireNonNull(capacity, "Capacity supplier cannot be null");
        Objects.requireNonNull(canExtract, "Extraction validity check cannot be null");
        Objects.requireNonNull(canInsert, "Insertion validity check cannot be null");
        Objects.requireNonNull(validator, "Fluid validity check cannot be null");
        return new VariableCapacityFluidTank(capacity, canExtract, canInsert, validator, listener);
    }

    private final IntSupplier capacity;

    protected VariableCapacityFluidTank(IntSupplier capacity, BiPredicate<FluidStack, AutomationType> canExtract,
          BiPredicate<FluidStack, AutomationType> canInsert, Predicate<FluidStack> validator, @Nullable IContentsListener listener) {
        super(capacity.getAsInt(), canExtract, canInsert, validator, listener);
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
        if (getFluidAmount() == amount || action.simulate()) {
            return amount;
        }
        stored.amount = amount;
        onContentsChanged();
        return amount;
    }
}
