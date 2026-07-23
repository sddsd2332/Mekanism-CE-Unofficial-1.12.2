package mekanism.api;

import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.IHeatCapacitor;
import net.minecraft.util.EnumFacing;

/**
 * @deprecated Use {@link mekanism.api.heat.IHeatHandler} and {@link IHeatCapacitor}. This compatibility API will be removed.
 */
@Deprecated
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
        return HeatAPI.sanitizeTemperature(getTemp() + HeatAPI.AMBIENT_TEMP);
    }

    @Override
    default double getInverseConduction() {
        return HeatAPI.sanitizeInverseConduction(getInverseConductionCoefficient());
    }

    @Override
    default double getInverseInsulation() {
        return HeatAPI.sanitizeInverseInsulation(getInsulationCoefficient(null));
    }

    @Override
    default double getHeatCapacity() {
        return HeatAPI.DEFAULT_HEAT_CAPACITY;
    }

    @Override
    default double getHeat() {
        return HeatAPI.multiplyHeat(getTemperature(), getHeatCapacity());
    }

    @Override
    default void setHeat(double heat) {
        double target = HeatAPI.sanitizeHeat(heat, HeatAPI.multiplyHeat(HeatAPI.AMBIENT_TEMP, getHeatCapacity()));
        transferHeatTo(target - getHeat());
        applyTemperatureChange();
    }

    @Override
    default void handleHeat(double transfer) {
        if (HeatAPI.isFinite(transfer)) {
            transferHeatTo(Math.max(-HeatAPI.MAX_HEAT, Math.min(HeatAPI.MAX_HEAT, transfer)));
            applyTemperatureChange();
        }
    }
}
