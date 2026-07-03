package mekanism.api.energy;

import mekanism.api.Action;

/**
 * High-version style strict energy handler, adapted to 1.12's double based energy values.
 */
public interface IStrictEnergyHandler {

    int getEnergyContainerCount();

    double getEnergy(int container);

    void setEnergy(int container, double energy);

    double getMaxEnergy(int container);

    double getNeededEnergy(int container);

    /**
     * Inserts energy into a given container and returns the remainder.
     */
    double insertEnergy(int container, double amount, Action action);

    /**
     * Extracts energy from a given container.
     */
    double extractEnergy(int container, double amount, Action action);

    /**
     * Inserts energy into this handler, leaving distribution to the handler.
     */
    default double insertEnergy(double amount, Action action) {
        if (amount <= 0) {
            return 0;
        }
        int containers = getEnergyContainerCount();
        if (containers == 0) {
            return amount;
        } else if (containers == 1) {
            return insertEnergy(0, amount, action);
        }
        double toInsert = amount;
        boolean[] emptyContainers = new boolean[containers];
        for (int container = 0; container < containers; container++) {
            if (getEnergy(container) <= 0) {
                emptyContainers[container] = true;
            } else {
                double remainder = insertEnergy(container, toInsert, action);
                if (remainder <= 0) {
                    return 0;
                }
                toInsert = remainder;
            }
        }
        for (int container = 0; container < containers; container++) {
            if (emptyContainers[container]) {
                double remainder = insertEnergy(container, toInsert, action);
                if (remainder <= 0) {
                    return 0;
                }
                toInsert = remainder;
            }
        }
        return toInsert;
    }

    /**
     * Extracts energy from this handler, leaving distribution to the handler.
     */
    default double extractEnergy(double amount, Action action) {
        if (amount <= 0) {
            return 0;
        }
        int containers = getEnergyContainerCount();
        if (containers == 0) {
            return 0;
        } else if (containers == 1) {
            return extractEnergy(0, amount, action);
        }
        double extracted = 0;
        double toExtract = amount;
        for (int container = 0; container < containers; container++) {
            double drained = extractEnergy(container, toExtract, action);
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
}
