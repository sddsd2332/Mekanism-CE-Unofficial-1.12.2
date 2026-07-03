package mekanism.api;

import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.IHeatCapacitor;
import net.minecraft.util.EnumFacing;

public interface IHeatTransfer extends IHeatCapacitor {

    /**
     * The value of the zero point of our temperature scale in kelvin
     */
    double AMBIENT_TEMP = HeatAPI.AMBIENT_TEMP;

    /**
     * The heat transfer coefficient for air
     */
    double AIR_INVERSE_COEFFICIENT = HeatAPI.AIR_INVERSE_COEFFICIENT;

    double getTemp();

    double getInverseConductionCoefficient();

    double getInsulationCoefficient(EnumFacing side);

    void transferHeatTo(double heat);

    double[] simulateHeat();

    double applyTemperatureChange();

    boolean canConnectHeat(EnumFacing side);

    IHeatTransfer getAdjacent(EnumFacing side);

    @Override
    default double getTemperature() {
        return getTemp() + HeatAPI.AMBIENT_TEMP;
    }

    @Override
    default double getInverseConduction() {
        return getInverseConductionCoefficient();
    }

    @Override
    default double getInverseInsulation() {
        return HeatAPI.DEFAULT_INVERSE_INSULATION;
    }

    @Override
    default double getHeatCapacity() {
        return HeatAPI.DEFAULT_HEAT_CAPACITY;
    }

    @Override
    default double getHeat() {
        return getTemperature() * getHeatCapacity();
    }

    @Override
    default void setHeat(double heat) {
        transferHeatTo(heat - getHeat());
    }

    @Override
    default void handleHeat(double transfer) {
        transferHeatTo(transfer);
    }
}
