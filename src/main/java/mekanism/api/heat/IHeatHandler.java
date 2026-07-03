package mekanism.api.heat;

public interface IHeatHandler {

    int getHeatCapacitorCount();

    double getTemperature(int capacitor);

    double getInverseConduction(int capacitor);

    double getHeatCapacity(int capacitor);

    void handleHeat(int capacitor, double transfer);

    default double getTotalTemperature() {
        int heatCapacitorCount = getHeatCapacitorCount();
        if (heatCapacitorCount == 1) {
            return getTemperature(0);
        }
        double sum = 0;
        double totalCapacity = getTotalHeatCapacity();
        for (int capacitor = 0; capacitor < heatCapacitorCount; capacitor++) {
            sum += getTemperature(capacitor) * (getHeatCapacity(capacitor) / totalCapacity);
        }
        return sum;
    }

    default double getTotalInverseConduction() {
        int heatCapacitorCount = getHeatCapacitorCount();
        if (heatCapacitorCount == 0) {
            return HeatAPI.DEFAULT_INVERSE_CONDUCTION;
        } else if (heatCapacitorCount == 1) {
            return getInverseConduction(0);
        }
        double sum = 0;
        double totalCapacity = getTotalHeatCapacity();
        for (int capacitor = 0; capacitor < heatCapacitorCount; capacitor++) {
            sum += getInverseConduction(capacitor) * (getHeatCapacity(capacitor) / totalCapacity);
        }
        return sum;
    }

    default double getTotalHeatCapacity() {
        int heatCapacitorCount = getHeatCapacitorCount();
        if (heatCapacitorCount == 1) {
            return getHeatCapacity(0);
        }
        double sum = 0;
        for (int capacitor = 0; capacitor < heatCapacitorCount; capacitor++) {
            sum += getHeatCapacity(capacitor);
        }
        return sum;
    }

    default void handleHeat(double transfer) {
        int heatCapacitorCount = getHeatCapacitorCount();
        if (heatCapacitorCount == 1) {
            handleHeat(0, transfer);
        } else {
            double totalHeatCapacity = getTotalHeatCapacity();
            for (int capacitor = 0; capacitor < heatCapacitorCount; capacitor++) {
                handleHeat(capacitor, transfer * (getHeatCapacity(capacitor) / totalHeatCapacity));
            }
        }
    }
}
