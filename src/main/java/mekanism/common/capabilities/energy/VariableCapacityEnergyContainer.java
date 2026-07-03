package mekanism.common.capabilities.energy;

import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

public class VariableCapacityEnergyContainer extends BasicEnergyContainer {

    public static VariableCapacityEnergyContainer input(DoubleSupplier maxEnergy, @Nullable IContentsListener listener) {
        Objects.requireNonNull(maxEnergy, "Max energy supplier cannot be null");
        return new VariableCapacityEnergyContainer(maxEnergy, notExternal, automationType -> true, listener);
    }

    public static VariableCapacityEnergyContainer output(DoubleSupplier maxEnergy, @Nullable IContentsListener listener) {
        Objects.requireNonNull(maxEnergy, "Max energy supplier cannot be null");
        return new VariableCapacityEnergyContainer(maxEnergy, automationType -> true, internalOnly, listener);
    }

    public static VariableCapacityEnergyContainer create(DoubleSupplier maxEnergy, Predicate<AutomationType> canExtract, Predicate<AutomationType> canInsert,
          @Nullable IContentsListener listener) {
        Objects.requireNonNull(maxEnergy, "Max energy supplier cannot be null");
        Objects.requireNonNull(canExtract, "Extraction validity check cannot be null");
        Objects.requireNonNull(canInsert, "Insertion validity check cannot be null");
        return new VariableCapacityEnergyContainer(maxEnergy, canExtract, canInsert, listener);
    }

    private final DoubleSupplier maxEnergy;

    protected VariableCapacityEnergyContainer(DoubleSupplier maxEnergy, Predicate<AutomationType> canExtract, Predicate<AutomationType> canInsert,
          @Nullable IContentsListener listener) {
        super(maxEnergy.getAsDouble(), canExtract, canInsert, listener);
        this.maxEnergy = maxEnergy;
    }

    @Override
    public double getMaxEnergy() {
        return maxEnergy.getAsDouble();
    }
}
