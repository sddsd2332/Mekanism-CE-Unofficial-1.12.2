package mekanism.api.heat;

import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;

public interface ISidedHeatHandler extends IHeatHandler {

    default Object getHeatIdentity(@Nullable EnumFacing side) {
        return this;
    }

    @Override
    default Object getHeatIdentity() {
        return getHeatIdentity(getHeatSideFor());
    }

    @Nullable
    default EnumFacing getHeatSideFor() {
        return null;
    }

    int getHeatCapacitorCount(@Nullable EnumFacing side);

    @Override
    default int getHeatCapacitorCount() {
        return getHeatCapacitorCount(getHeatSideFor());
    }

    double getTemperature(int capacitor, @Nullable EnumFacing side);

    @Override
    default double getTemperature(int capacitor) {
        return getTemperature(capacitor, getHeatSideFor());
    }

    double getInverseConduction(int capacitor, @Nullable EnumFacing side);

    @Override
    default double getInverseConduction(int capacitor) {
        return getInverseConduction(capacitor, getHeatSideFor());
    }

    double getHeatCapacity(int capacitor, @Nullable EnumFacing side);

    @Override
    default double getHeatCapacity(int capacitor) {
        return getHeatCapacity(capacitor, getHeatSideFor());
    }

    void handleHeat(int capacitor, double transfer, @Nullable EnumFacing side);

    @Override
    default void handleHeat(int capacitor, double transfer) {
        handleHeat(capacitor, transfer, getHeatSideFor());
    }

    default double getTotalTemperature(@Nullable EnumFacing side) {
        int heatCapacitorCount = getHeatCapacitorCount(side);
        if (heatCapacitorCount <= 0) {
            return HeatAPI.AMBIENT_TEMP;
        } else if (heatCapacitorCount == 1) {
            return HeatAPI.sanitizeTemperature(getTemperature(0, side));
        }
        double sum = 0;
        double totalCapacity = getTotalHeatCapacity(side);
        if (!HeatAPI.isFinite(totalCapacity) || totalCapacity < 1) {
            return HeatAPI.AMBIENT_TEMP;
        }
        double maxCapacity = getMaxHeatCapacity(heatCapacitorCount, side);
        double totalWeight = getTotalCapacityWeight(heatCapacitorCount, maxCapacity, side);
        if (totalWeight <= 0) {
            return HeatAPI.AMBIENT_TEMP;
        }
        for (int capacitor = 0; capacitor < heatCapacitorCount; capacitor++) {
            double capacity = getHeatCapacity(capacitor, side);
            if (HeatAPI.isFinite(capacity) && capacity > 0) {
                double contribution = HeatAPI.sanitizeTemperature(getTemperature(capacitor, side)) *
                      (HeatAPI.getCapacityWeight(capacity, maxCapacity) / totalWeight);
                if (!HeatAPI.isFinite(contribution) || contribution >= HeatAPI.MAX_HEAT - sum) {
                    return HeatAPI.MAX_HEAT;
                }
                sum += contribution;
            }
        }
        return HeatAPI.sanitizeTemperature(sum);
    }

    @Override
    default double getTotalTemperature() {
        return getTotalTemperature(getHeatSideFor());
    }

    default double getTotalInverseConductionCoefficient(@Nullable EnumFacing side) {
        int heatCapacitorCount = getHeatCapacitorCount(side);
        if (heatCapacitorCount == 0) {
            return HeatAPI.DEFAULT_INVERSE_CONDUCTION;
        } else if (heatCapacitorCount == 1) {
            return HeatAPI.sanitizeInverseConduction(getInverseConduction(0, side));
        }
        double sum = 0;
        double totalCapacity = getTotalHeatCapacity(side);
        if (totalCapacity < 1) {
            return HeatAPI.DEFAULT_INVERSE_CONDUCTION;
        }
        double maxCapacity = getMaxHeatCapacity(heatCapacitorCount, side);
        double totalWeight = getTotalCapacityWeight(heatCapacitorCount, maxCapacity, side);
        if (totalWeight <= 0) {
            return HeatAPI.DEFAULT_INVERSE_CONDUCTION;
        }
        for (int capacitor = 0; capacitor < heatCapacitorCount; capacitor++) {
            double capacity = getHeatCapacity(capacitor, side);
            if (HeatAPI.isFinite(capacity) && capacity > 0) {
                double contribution = HeatAPI.sanitizeInverseConduction(getInverseConduction(capacitor, side)) *
                      (HeatAPI.getCapacityWeight(capacity, maxCapacity) / totalWeight);
                if (!HeatAPI.isFinite(contribution) || contribution >= HeatAPI.MAX_HEAT - sum) {
                    return HeatAPI.MAX_HEAT;
                }
                sum += contribution;
            }
        }
        return HeatAPI.sanitizeInverseConduction(sum);
    }

    @Override
    default double getTotalInverseConduction() {
        return getTotalInverseConductionCoefficient(getHeatSideFor());
    }

    default double getTotalHeatCapacity(@Nullable EnumFacing side) {
        int heatCapacitorCount = getHeatCapacitorCount(side);
        if (heatCapacitorCount <= 0) {
            return 0;
        } else if (heatCapacitorCount == 1) {
            return HeatAPI.sanitizeHeatCapacity(getHeatCapacity(0, side));
        }
        double sum = 0;
        for (int capacitor = 0; capacitor < heatCapacitorCount; capacitor++) {
            double capacity = getHeatCapacity(capacitor, side);
            if (HeatAPI.isFinite(capacity) && capacity > 0) {
                if (capacity >= HeatAPI.MAX_HEAT - sum) {
                    return HeatAPI.MAX_HEAT;
                }
                sum += capacity;
            }
        }
        return sum;
    }

    default double getMaxHeatCapacity(int heatCapacitorCount, @Nullable EnumFacing side) {
        double maxCapacity = 0;
        for (int capacitor = 0; capacitor < heatCapacitorCount; capacitor++) {
            double capacity = getHeatCapacity(capacitor, side);
            if (HeatAPI.isFinite(capacity) && capacity > maxCapacity) {
                maxCapacity = capacity;
            }
        }
        return maxCapacity;
    }

    default double getTotalCapacityWeight(int heatCapacitorCount, double maxCapacity, @Nullable EnumFacing side) {
        double totalWeight = 0;
        for (int capacitor = 0; capacitor < heatCapacitorCount; capacitor++) {
            double weight = HeatAPI.getCapacityWeight(getHeatCapacity(capacitor, side), maxCapacity);
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

    @Override
    default double getTotalHeatCapacity() {
        return getTotalHeatCapacity(getHeatSideFor());
    }

    default void handleHeat(double transfer, @Nullable EnumFacing side) {
        if (!HeatAPI.isFinite(transfer) || Math.abs(transfer) <= HeatAPI.EPSILON) {
            return;
        }
        int heatCapacitorCount = getHeatCapacitorCount(side);
        if (heatCapacitorCount == 1) {
            handleHeat(0, transfer, side);
        } else if (heatCapacitorCount > 1) {
            double totalHeatCapacity = getTotalHeatCapacity(side);
            if (totalHeatCapacity < 1) {
                return;
            }
            double maxCapacity = getMaxHeatCapacity(heatCapacitorCount, side);
            double totalWeight = getTotalCapacityWeight(heatCapacitorCount, maxCapacity, side);
            if (totalWeight <= 0) {
                return;
            }
            for (int capacitor = 0; capacitor < heatCapacitorCount; capacitor++) {
                double capacity = getHeatCapacity(capacitor, side);
                if (HeatAPI.isFinite(capacity) && capacity > 0) {
                    double share = transfer * (HeatAPI.getCapacityWeight(capacity, maxCapacity) / totalWeight);
                    if (HeatAPI.isFinite(share)) {
                        handleHeat(capacitor, share, side);
                    }
                }
            }
        }
    }

    @Override
    default void handleHeat(double transfer) {
        handleHeat(transfer, getHeatSideFor());
    }
}
