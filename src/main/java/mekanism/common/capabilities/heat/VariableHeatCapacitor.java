package mekanism.common.capabilities.heat;

import mekanism.api.IContentsListener;
import mekanism.api.heat.HeatAPI;

import javax.annotation.Nullable;
import java.util.function.DoubleSupplier;

public class VariableHeatCapacitor extends BasicHeatCapacitor {

    private final DoubleSupplier conductionCoefficientSupplier;
    private final DoubleSupplier insulationCoefficientSupplier;

    public static VariableHeatCapacitor create(double heatCapacity, @Nullable DoubleSupplier ambientTempSupplier, @Nullable IContentsListener listener) {
        return create(heatCapacity, () -> HeatAPI.DEFAULT_INVERSE_CONDUCTION, () -> HeatAPI.DEFAULT_INVERSE_INSULATION, ambientTempSupplier, listener);
    }

    public static VariableHeatCapacitor create(double heatCapacity, DoubleSupplier conductionCoefficient, DoubleSupplier insulationCoefficient,
          @Nullable DoubleSupplier ambientTempSupplier, @Nullable IContentsListener listener) {
        return new VariableHeatCapacitor(heatCapacity, conductionCoefficient, insulationCoefficient, ambientTempSupplier, listener);
    }

    protected VariableHeatCapacitor(double heatCapacity, DoubleSupplier conductionCoefficient, DoubleSupplier insulationCoefficient,
          @Nullable DoubleSupplier ambientTempSupplier, @Nullable IContentsListener listener) {
        // Suppliers may be backed by live config values. Sanitize their initial values as well as
        // subsequent reads so a malformed value cannot prevent the capacitor from being created.
        super(heatCapacity, HeatAPI.sanitizeInverseConduction(conductionCoefficient.getAsDouble()),
              HeatAPI.sanitizeInverseInsulation(insulationCoefficient.getAsDouble()), ambientTempSupplier, listener);
        this.conductionCoefficientSupplier = conductionCoefficient;
        this.insulationCoefficientSupplier = insulationCoefficient;
    }

    @Override
    public double getInverseConduction() {
        double inverseConduction = conductionCoefficientSupplier.getAsDouble();
        return HeatAPI.sanitizeInverseConduction(inverseConduction);
    }

    @Override
    public double getInverseInsulation() {
        double inverseInsulation = insulationCoefficientSupplier.getAsDouble();
        return HeatAPI.sanitizeInverseInsulation(inverseInsulation);
    }
}
