package mekanism.api.energy;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;

/**
 * 1.12 bridge for high-version energy containers.
 */
public interface IEnergyContainer extends IStrictEnergyStorage, IStrictEnergyAcceptor, IStrictEnergyOutputter {

    default double getNeeded() {
        return Math.max(0, getNeedEnergy());
    }

    default boolean isEmpty() {
        return getEnergy() <= 0;
    }

    default void setEmpty() {
        setEnergy(0);
    }

    /**
     * Inserts energy and returns the unaccepted remainder.
     */
    default double insert(double amount, Action action, AutomationType automationType) {
        if (amount <= 0) {
            return amount;
        }
        double needed = getNeeded();
        if (needed <= 0) {
            return amount;
        }
        double toAdd = Math.min(amount, needed);
        if (action.execute()) {
            setEnergy(getEnergy() + toAdd);
        }
        return Math.max(0, amount - toAdd);
    }

    /**
     * Inserts energy and returns the unaccepted remainder.
     */
    default double insert(double amount, @Nullable EnumFacing side, Action action, AutomationType automationType) {
        return insert(amount, action, automationType);
    }

    /**
     * Extracts energy from this container.
     */
    default double extract(double amount, Action action, AutomationType automationType) {
        if (isEmpty() || amount <= 0) {
            return 0;
        }
        double ret = Math.min(getEnergy(), amount);
        if (ret > 0 && action.execute()) {
            setEnergy(getEnergy() - ret);
        }
        return ret;
    }

    /**
     * Extracts energy from this container.
     */
    default double extract(double amount, @Nullable EnumFacing side, Action action, AutomationType automationType) {
        return extract(amount, action, automationType);
    }

    @Override
    default double acceptEnergy(EnumFacing side, double amount, boolean simulate) {
        return amount - insert(amount, side, Action.get(!simulate), AutomationType.handler(side));
    }

    @Override
    default boolean canReceiveEnergy(EnumFacing side) {
        return getNeeded() > 0;
    }

    @Override
    default double pullEnergy(EnumFacing side, double amount, boolean simulate) {
        return extract(amount, side, Action.get(!simulate), AutomationType.handler(side));
    }

    @Override
    default boolean canOutputEnergy(EnumFacing side) {
        return !isEmpty();
    }
}
