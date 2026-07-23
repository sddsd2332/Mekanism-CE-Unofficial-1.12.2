package mekanism.api.heat;

public interface IHeatHandler {

    /**
     * Returns an identity shared by proxies that point at the same thermal state.
     */
    default Object getHeatIdentity() {
        return this;
    }

    int getHeatCapacitorCount();

    double getTemperature(int capacitor);

    double getInverseConduction(int capacitor);

    double getHeatCapacity(int capacitor);

    void handleHeat(int capacitor, double transfer);

    default double getTotalTemperature() {
        int heatCapacitorCount = getHeatCapacitorCount();
        if (heatCapacitorCount <= 0) {
            return HeatAPI.AMBIENT_TEMP;
        } else if (heatCapacitorCount == 1) {
            return HeatAPI.sanitizeTemperature(getTemperature(0));
        }
        double sum = 0;
        double totalCapacity = getTotalHeatCapacity();
        if (!HeatAPI.isFinite(totalCapacity) || totalCapacity < 1) {
            return HeatAPI.AMBIENT_TEMP;
        }
        double maxCapacity = getMaxHeatCapacity(heatCapacitorCount);
        double totalWeight = getTotalCapacityWeight(heatCapacitorCount, maxCapacity);
        if (totalWeight <= 0) {
            return HeatAPI.AMBIENT_TEMP;
        }
        for (int capacitor = 0; capacitor < heatCapacitorCount; capacitor++) {
            double capacity = getHeatCapacity(capacitor);
            if (HeatAPI.isFinite(capacity) && capacity > 0) {
                double contribution = HeatAPI.sanitizeTemperature(getTemperature(capacitor)) *
                      (HeatAPI.getCapacityWeight(capacity, maxCapacity) / totalWeight);
                if (!HeatAPI.isFinite(contribution) || contribution >= HeatAPI.MAX_HEAT - sum) {
                    return HeatAPI.MAX_HEAT;
                }
                sum += contribution;
            }
        }
        return HeatAPI.sanitizeTemperature(sum);
    }

    default double getTotalInverseConduction() {
        int heatCapacitorCount = getHeatCapacitorCount();
        if (heatCapacitorCount == 0) {
            return HeatAPI.DEFAULT_INVERSE_CONDUCTION;
        } else if (heatCapacitorCount == 1) {
            return HeatAPI.sanitizeInverseConduction(getInverseConduction(0));
        }
        double sum = 0;
        double totalCapacity = getTotalHeatCapacity();
        if (totalCapacity < 1) {
            return HeatAPI.DEFAULT_INVERSE_CONDUCTION;
        }
        double maxCapacity = getMaxHeatCapacity(heatCapacitorCount);
        double totalWeight = getTotalCapacityWeight(heatCapacitorCount, maxCapacity);
        if (totalWeight <= 0) {
            return HeatAPI.DEFAULT_INVERSE_CONDUCTION;
        }
        for (int capacitor = 0; capacitor < heatCapacitorCount; capacitor++) {
            double capacity = getHeatCapacity(capacitor);
            if (HeatAPI.isFinite(capacity) && capacity > 0) {
                double contribution = HeatAPI.sanitizeInverseConduction(getInverseConduction(capacitor)) *
                      (HeatAPI.getCapacityWeight(capacity, maxCapacity) / totalWeight);
                if (!HeatAPI.isFinite(contribution) || contribution >= HeatAPI.MAX_HEAT - sum) {
                    return HeatAPI.MAX_HEAT;
                }
                sum += contribution;
            }
        }
        return HeatAPI.sanitizeInverseConduction(sum);
    }

    default double getTotalHeatCapacity() {
        int heatCapacitorCount = getHeatCapacitorCount();
        if (heatCapacitorCount <= 0) {
            return 0;
        } else if (heatCapacitorCount == 1) {
            return HeatAPI.sanitizeHeatCapacity(getHeatCapacity(0));
        }
        double sum = 0;
        for (int capacitor = 0; capacitor < heatCapacitorCount; capacitor++) {
            double capacity = getHeatCapacity(capacitor);
            if (HeatAPI.isFinite(capacity) && capacity > 0) {
                if (capacity >= HeatAPI.MAX_HEAT - sum) {
                    return HeatAPI.MAX_HEAT;
                }
                sum += capacity;
            }
        }
        return sum;
    }

    default double getMaxHeatCapacity(int heatCapacitorCount) {
        double maxCapacity = 0;
        for (int capacitor = 0; capacitor < heatCapacitorCount; capacitor++) {
            double capacity = getHeatCapacity(capacitor);
            if (HeatAPI.isFinite(capacity) && capacity > maxCapacity) {
                maxCapacity = capacity;
            }
        }
        return maxCapacity;
    }

    default double getTotalCapacityWeight(int heatCapacitorCount, double maxCapacity) {
        double totalWeight = 0;
        for (int capacitor = 0; capacitor < heatCapacitorCount; capacitor++) {
            double weight = HeatAPI.getCapacityWeight(getHeatCapacity(capacitor), maxCapacity);
            if (!HeatAPI.isFinite(weight) || weight <= 0) {
                continue;
            }
            if (weight >= HeatAPI.MAX_HEAT - totalWeight) {
                return HeatAPI.MAX_HEAT;
            }
            totalWeight += weight;
        }
        return totalWeight;
    }

    default void handleHeat(double transfer) {
        if (!HeatAPI.isFinite(transfer) || Math.abs(transfer) <= HeatAPI.EPSILON) {
            return;
        }
        int heatCapacitorCount = getHeatCapacitorCount();
        if (heatCapacitorCount == 1) {
            handleHeat(0, transfer);
        } else if (heatCapacitorCount > 1) {
            double totalHeatCapacity = getTotalHeatCapacity();
            if (totalHeatCapacity < 1) {
                return;
            }
            double maxCapacity = getMaxHeatCapacity(heatCapacitorCount);
            double totalWeight = getTotalCapacityWeight(heatCapacitorCount, maxCapacity);
            if (totalWeight <= 0) {
                return;
            }
            for (int capacitor = 0; capacitor < heatCapacitorCount; capacitor++) {
                double capacity = getHeatCapacity(capacitor);
                if (HeatAPI.isFinite(capacity) && capacity > 0) {
                    double share = transfer * (HeatAPI.getCapacityWeight(capacity, maxCapacity) / totalWeight);
                    if (HeatAPI.isFinite(share)) {
                        handleHeat(capacitor, share);
                    }
                }
            }
        }
    }
}
