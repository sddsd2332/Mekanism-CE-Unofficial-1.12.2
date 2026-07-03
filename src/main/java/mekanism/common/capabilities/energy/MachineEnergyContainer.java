package mekanism.common.capabilities.energy;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.energy.IEnergyContainer;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

/**
 * 1.12 bridge for high-version machine-scoped energy containers.
 *
 * <p>
 * This container intentionally does not own storage. It wraps legacy machine energy fields so
 * explicit container holders can be migrated before the old energy tile base is removed.
 * </p>
 */
public class MachineEnergyContainer implements IEnergyContainer {

    public static MachineEnergyContainer input(DoubleSupplier stored, DoubleConsumer setter, DoubleSupplier maxEnergy, DoubleSupplier energyPerTick,
          @Nullable IContentsListener listener) {
        Objects.requireNonNull(stored, "Stored energy supplier cannot be null");
        Objects.requireNonNull(setter, "Stored energy setter cannot be null");
        Objects.requireNonNull(maxEnergy, "Max energy supplier cannot be null");
        Objects.requireNonNull(energyPerTick, "Energy per tick supplier cannot be null");
        return new MachineEnergyContainer(stored, setter, maxEnergy, energyPerTick, BasicEnergyContainer.notExternal, automationType -> true, listener);
    }

    public static MachineEnergyContainer internal(DoubleSupplier stored, DoubleConsumer setter, DoubleSupplier maxEnergy, DoubleSupplier energyPerTick,
          @Nullable IContentsListener listener) {
        Objects.requireNonNull(stored, "Stored energy supplier cannot be null");
        Objects.requireNonNull(setter, "Stored energy setter cannot be null");
        Objects.requireNonNull(maxEnergy, "Max energy supplier cannot be null");
        Objects.requireNonNull(energyPerTick, "Energy per tick supplier cannot be null");
        return new MachineEnergyContainer(stored, setter, maxEnergy, energyPerTick, BasicEnergyContainer.internalOnly, BasicEnergyContainer.internalOnly, listener);
    }

    public static MachineEnergyContainer create(DoubleSupplier stored, DoubleConsumer setter, DoubleSupplier maxEnergy, DoubleSupplier energyPerTick,
          Predicate<AutomationType> canExtract, Predicate<AutomationType> canInsert, @Nullable IContentsListener listener) {
        Objects.requireNonNull(stored, "Stored energy supplier cannot be null");
        Objects.requireNonNull(setter, "Stored energy setter cannot be null");
        Objects.requireNonNull(maxEnergy, "Max energy supplier cannot be null");
        Objects.requireNonNull(energyPerTick, "Energy per tick supplier cannot be null");
        Objects.requireNonNull(canExtract, "Extraction validity check cannot be null");
        Objects.requireNonNull(canInsert, "Insertion validity check cannot be null");
        return new MachineEnergyContainer(stored, setter, maxEnergy, energyPerTick, canExtract, canInsert, listener);
    }

    private final DoubleSupplier stored;
    private final DoubleConsumer setter;
    private final DoubleSupplier maxEnergy;
    private final DoubleSupplier energyPerTick;
    private final Predicate<AutomationType> canExtract;
    private final Predicate<AutomationType> canInsert;
    @Nullable
    private final IContentsListener listener;

    protected MachineEnergyContainer(DoubleSupplier stored, DoubleConsumer setter, DoubleSupplier maxEnergy, DoubleSupplier energyPerTick,
          Predicate<AutomationType> canExtract, Predicate<AutomationType> canInsert, @Nullable IContentsListener listener) {
        this.stored = stored;
        this.setter = setter;
        this.maxEnergy = maxEnergy;
        this.energyPerTick = energyPerTick;
        this.canExtract = canExtract;
        this.canInsert = canInsert;
        this.listener = listener;
    }

    public void onContentsChanged() {
        if (listener != null) {
            listener.onContentsChanged();
        }
    }

    @Override
    public double getEnergy() {
        return stored.getAsDouble();
    }

    @Override
    public void setEnergy(double energy) {
        double clamped = Math.max(0, Math.min(energy, getMaxEnergy()));
        if (Double.compare(getEnergy(), clamped) != 0) {
            setter.accept(clamped);
            onContentsChanged();
        }
    }

    @Override
    public double getMaxEnergy() {
        return Math.max(0, maxEnergy.getAsDouble());
    }

    public double getEnergyPerTick() {
        return Math.max(0, energyPerTick.getAsDouble());
    }

    protected double getInsertRate(@Nullable AutomationType automationType) {
        return Double.MAX_VALUE;
    }

    protected double getExtractRate(@Nullable AutomationType automationType) {
        return Double.MAX_VALUE;
    }

    @Override
    public double insert(double amount, Action action, AutomationType automationType) {
        if (amount <= 0 || !canInsert.test(automationType)) {
            return amount;
        }
        double needed = Math.min(getInsertRate(automationType), getNeeded());
        if (needed <= 0) {
            return amount;
        }
        double toAdd = Math.min(amount, needed);
        if (action.execute()) {
            setEnergy(getEnergy() + toAdd);
        }
        return amount - toAdd;
    }

    @Override
    public double extract(double amount, Action action, AutomationType automationType) {
        if (isEmpty() || amount <= 0 || !canExtract.test(automationType)) {
            return 0;
        }
        double ret = Math.min(Math.min(getExtractRate(automationType), getEnergy()), amount);
        if (ret > 0 && action.execute()) {
            setEnergy(getEnergy() - ret);
        }
        return ret;
    }

    @Override
    public boolean canReceiveEnergy(EnumFacing side) {
        return canInsert.test(AutomationType.handler(side)) && getNeeded() > 0;
    }

    @Override
    public boolean canOutputEnergy(EnumFacing side) {
        return canExtract.test(AutomationType.handler(side)) && !isEmpty();
    }
}
