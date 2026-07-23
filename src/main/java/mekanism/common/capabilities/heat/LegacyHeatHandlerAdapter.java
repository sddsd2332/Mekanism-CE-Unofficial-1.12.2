package mekanism.common.capabilities.heat;

import mekanism.api.IHeatTransfer;
import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.IHeatHandler;

import java.util.function.DoubleSupplier;

/**
 * Converts a handler exposed through the deprecated 1.12 heat capability to the new absolute-Kelvin API.
 * @deprecated Compatibility bridge for {@link mekanism.api.IHeatTransfer}.
 */
@Deprecated
@SuppressWarnings("removal")
public final class LegacyHeatHandlerAdapter implements IHeatHandler {

    private final IHeatTransfer legacy;
    private final DoubleSupplier ambientTemperature;

    public LegacyHeatHandlerAdapter(IHeatTransfer legacy) {
        this(legacy, () -> HeatAPI.AMBIENT_TEMP);
    }

    public LegacyHeatHandlerAdapter(IHeatTransfer legacy, DoubleSupplier ambientTemperature) {
        this.legacy = legacy;
        this.ambientTemperature = ambientTemperature;
    }

    @Override
    public Object getHeatIdentity() {
        return legacy.getHeatIdentity();
    }

    @Override
    public int getHeatCapacitorCount() {
        return 1;
    }

    @Override
    public double getTemperature(int capacitor) {
        if (capacitor != 0) {
            return HeatAPI.AMBIENT_TEMP;
        }
        double ambient = HeatAPI.sanitizeTemperature(ambientTemperature.getAsDouble());
        return HeatAPI.sanitizeTemperature(legacy.getTemp() + ambient);
    }

    @Override
    public double getInverseConduction(int capacitor) {
        if (capacitor != 0) {
            return HeatAPI.DEFAULT_INVERSE_CONDUCTION;
        }
        double inverseConduction = legacy.getInverseConductionCoefficient();
        return HeatAPI.sanitizeInverseConduction(inverseConduction);
    }

    @Override
    public double getHeatCapacity(int capacitor) {
        return capacitor == 0 ? HeatAPI.sanitizeHeatCapacity(legacy.getHeatCapacity()) : HeatAPI.DEFAULT_HEAT_CAPACITY;
    }

    @Override
    public void handleHeat(int capacitor, double transfer) {
        if (capacitor == 0 && HeatAPI.isFinite(transfer)) {
            double bounded = Math.max(-HeatAPI.MAX_HEAT, Math.min(HeatAPI.MAX_HEAT, transfer));
            legacy.transferHeatTo(bounded);
            //Legacy handlers commonly buffered transfers until this method was called at the
            //end of a tick. Modern heat simulation requires each transfer to be visible at once.
            legacy.applyTemperatureChange();
        }
    }
}
