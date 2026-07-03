package mekanism.api.energy;

import mekanism.api.Action;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;

/**
 * Sided variant of {@link IStrictEnergyHandler}.
 */
public interface ISidedStrictEnergyHandler extends IStrictEnergyHandler {

    @Nullable
    default EnumFacing getEnergySideFor() {
        return null;
    }

    int getEnergyContainerCount(@Nullable EnumFacing side);

    @Override
    default int getEnergyContainerCount() {
        return getEnergyContainerCount(getEnergySideFor());
    }

    double getEnergy(int container, @Nullable EnumFacing side);

    @Override
    default double getEnergy(int container) {
        return getEnergy(container, getEnergySideFor());
    }

    void setEnergy(int container, double energy, @Nullable EnumFacing side);

    @Override
    default void setEnergy(int container, double energy) {
        setEnergy(container, energy, getEnergySideFor());
    }

    double getMaxEnergy(int container, @Nullable EnumFacing side);

    @Override
    default double getMaxEnergy(int container) {
        return getMaxEnergy(container, getEnergySideFor());
    }

    double getNeededEnergy(int container, @Nullable EnumFacing side);

    @Override
    default double getNeededEnergy(int container) {
        return getNeededEnergy(container, getEnergySideFor());
    }

    double insertEnergy(int container, double amount, @Nullable EnumFacing side, Action action);

    @Override
    default double insertEnergy(int container, double amount, Action action) {
        return insertEnergy(container, amount, getEnergySideFor(), action);
    }

    double extractEnergy(int container, double amount, @Nullable EnumFacing side, Action action);

    @Override
    default double extractEnergy(int container, double amount, Action action) {
        return extractEnergy(container, amount, getEnergySideFor(), action);
    }

    default double insertEnergy(double amount, @Nullable EnumFacing side, Action action) {
        if (amount <= 0) {
            return 0;
        }
        int containers = getEnergyContainerCount(side);
        if (containers == 0) {
            return amount;
        } else if (containers == 1) {
            return insertEnergy(0, amount, side, action);
        }
        double toInsert = amount;
        boolean[] emptyContainers = new boolean[containers];
        for (int container = 0; container < containers; container++) {
            if (getEnergy(container, side) <= 0) {
                emptyContainers[container] = true;
            } else {
                double remainder = insertEnergy(container, toInsert, side, action);
                if (remainder <= 0) {
                    return 0;
                }
                toInsert = remainder;
            }
        }
        for (int container = 0; container < containers; container++) {
            if (emptyContainers[container]) {
                double remainder = insertEnergy(container, toInsert, side, action);
                if (remainder <= 0) {
                    return 0;
                }
                toInsert = remainder;
            }
        }
        return toInsert;
    }

    @Override
    default double insertEnergy(double amount, Action action) {
        return insertEnergy(amount, getEnergySideFor(), action);
    }

    default double extractEnergy(double amount, @Nullable EnumFacing side, Action action) {
        if (amount <= 0) {
            return 0;
        }
        int containers = getEnergyContainerCount(side);
        if (containers == 0) {
            return 0;
        } else if (containers == 1) {
            return extractEnergy(0, amount, side, action);
        }
        double extracted = 0;
        double toExtract = amount;
        for (int container = 0; container < containers; container++) {
            double drained = extractEnergy(container, toExtract, side, action);
            if (drained > 0) {
                extracted += drained;
                toExtract -= drained;
                if (toExtract <= 0) {
                    break;
                }
            }
        }
        return extracted;
    }

    @Override
    default double extractEnergy(double amount, Action action) {
        return extractEnergy(amount, getEnergySideFor(), action);
    }
}
