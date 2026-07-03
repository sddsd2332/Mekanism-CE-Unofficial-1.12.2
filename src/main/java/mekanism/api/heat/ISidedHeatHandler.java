package mekanism.api.heat;

import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;

public interface ISidedHeatHandler extends IHeatHandler {

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
        if (heatCapacitorCount == 1) {
            return getTemperature(0, side);
        }
        double sum = 0;
        double totalCapacity = getTotalHeatCapacity(side);
        for (int capacitor = 0; capacitor < heatCapacitorCount; capacitor++) {
            sum += getTemperature(capacitor, side) * (getHeatCapacity(capacitor, side) / totalCapacity);
        }
        return sum;
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
            return getInverseConduction(0, side);
        }
        double sum = 0;
        double totalCapacity = getTotalHeatCapacity(side);
        for (int capacitor = 0; capacitor < heatCapacitorCount; capacitor++) {
            sum += getInverseConduction(capacitor, side) * (getHeatCapacity(capacitor, side) / totalCapacity);
        }
        return sum;
    }

    @Override
    default double getTotalInverseConduction() {
        return getTotalInverseConductionCoefficient(getHeatSideFor());
    }

    default double getTotalHeatCapacity(@Nullable EnumFacing side) {
        int heatCapacitorCount = getHeatCapacitorCount(side);
        if (heatCapacitorCount == 1) {
            return getHeatCapacity(0, side);
        }
        double sum = 0;
        for (int capacitor = 0; capacitor < heatCapacitorCount; capacitor++) {
            sum += getHeatCapacity(capacitor, side);
        }
        return sum;
    }

    @Override
    default double getTotalHeatCapacity() {
        return getTotalHeatCapacity(getHeatSideFor());
    }

    default void handleHeat(double transfer, @Nullable EnumFacing side) {
        int heatCapacitorCount = getHeatCapacitorCount(side);
        if (heatCapacitorCount == 1) {
            handleHeat(0, transfer, side);
        } else {
            double totalHeatCapacity = getTotalHeatCapacity(side);
            for (int capacitor = 0; capacitor < heatCapacitorCount; capacitor++) {
                handleHeat(capacitor, transfer * (getHeatCapacity(capacitor, side) / totalHeatCapacity), side);
            }
        }
    }

    @Override
    default void handleHeat(double transfer) {
        handleHeat(transfer, getHeatSideFor());
    }
}
