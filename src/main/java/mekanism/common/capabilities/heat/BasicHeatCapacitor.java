package mekanism.common.capabilities.heat;

import mekanism.api.IContentsListener;
import mekanism.api.NBTConstants;
import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.IHeatCapacitor;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.function.DoubleSupplier;

public class BasicHeatCapacitor implements IHeatCapacitor {

    @Nullable
    private final IContentsListener listener;
    @Nullable
    private final DoubleSupplier ambientTempSupplier;
    private final double inverseConductionCoefficient;
    private final double inverseInsulationCoefficient;
    private double heatCapacity;
    private double storedHeat = -1;

    public static BasicHeatCapacitor create(double heatCapacity, @Nullable DoubleSupplier ambientTempSupplier, @Nullable IContentsListener listener) {
        return create(heatCapacity, HeatAPI.DEFAULT_INVERSE_CONDUCTION, HeatAPI.DEFAULT_INVERSE_INSULATION, ambientTempSupplier, listener);
    }

    public static BasicHeatCapacitor create(double heatCapacity, double inverseConductionCoefficient, double inverseInsulationCoefficient,
          @Nullable DoubleSupplier ambientTempSupplier, @Nullable IContentsListener listener) {
        if (!HeatAPI.isFinite(heatCapacity) || heatCapacity < 1) {
            throw new IllegalArgumentException("Heat capacity must be at least one");
        }
        if (!HeatAPI.isFinite(inverseConductionCoefficient) || inverseConductionCoefficient < 1) {
            throw new IllegalArgumentException("Inverse conduction coefficient must be at least one");
        }
        if (!HeatAPI.isFinite(inverseInsulationCoefficient) || inverseInsulationCoefficient < 0) {
            throw new IllegalArgumentException("Inverse insulation coefficient cannot be negative");
        }
        return new BasicHeatCapacitor(heatCapacity, inverseConductionCoefficient, inverseInsulationCoefficient, ambientTempSupplier, listener);
    }

    protected BasicHeatCapacitor(double heatCapacity, double inverseConductionCoefficient, double inverseInsulationCoefficient,
          @Nullable DoubleSupplier ambientTempSupplier, @Nullable IContentsListener listener) {
        this.heatCapacity = Math.min(HeatAPI.MAX_HEAT, heatCapacity);
        this.inverseConductionCoefficient = HeatAPI.sanitizeInverseConduction(inverseConductionCoefficient);
        this.inverseInsulationCoefficient = HeatAPI.sanitizeInverseInsulation(inverseInsulationCoefficient);
        this.ambientTempSupplier = ambientTempSupplier;
        this.listener = listener;
    }

    private void initStoredHeat() {
        if (storedHeat == -1) {
            storedHeat = HeatAPI.multiplyHeat(getAmbientTemperature(), heatCapacity);
        }
    }

    protected double getAmbientTemperature() {
        double ambient = ambientTempSupplier == null ? HeatAPI.AMBIENT_TEMP : ambientTempSupplier.getAsDouble();
        return HeatAPI.isFinite(ambient) && ambient >= 0 ? ambient : HeatAPI.AMBIENT_TEMP;
    }

    @Override
    public double getTemperature() {
        return HeatAPI.sanitizeTemperature(getHeat() / getHeatCapacity());
    }

    @Override
    public double getInverseConduction() {
        return inverseConductionCoefficient;
    }

    @Override
    public double getInverseInsulation() {
        return inverseInsulationCoefficient;
    }

    @Override
    public double getHeatCapacity() {
        return heatCapacity;
    }

    @Override
    public double getHeat() {
        initStoredHeat();
        return storedHeat;
    }

    @Override
    public void setHeat(double heat) {
        double sanitized = HeatAPI.sanitizeHeat(heat, HeatAPI.multiplyHeat(getAmbientTemperature(), getHeatCapacity()));
        if (getHeat() != sanitized) {
            storedHeat = sanitized;
            onContentsChanged();
        }
    }

    @Override
    public void handleHeat(double transfer) {
        if (!HeatAPI.isFinite(transfer) || Math.abs(transfer) <= HeatAPI.EPSILON) {
            return;
        }
        double heat = getHeat();
        storedHeat = HeatAPI.addHeatClamped(heat, transfer);
        if (storedHeat != heat) {
            onContentsChanged();
        }
    }

    @Override
    public boolean isAmbientTemperature() {
        return Math.abs(getTemperature() - getAmbientTemperature()) < HeatAPI.EPSILON;
    }

    public void update() {
        // Heat is applied immediately so all connections in a tick observe the latest state.
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = IHeatCapacitor.super.serializeNBT();
        nbt.setDouble(NBTConstants.HEAT_CAPACITY, getHeatCapacity());
        return nbt;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        double newHeatCapacity = heatCapacity;
        double newStoredHeat = storedHeat;
        if (nbt.hasKey(NBTConstants.HEAT_CAPACITY)) {
            newHeatCapacity = HeatAPI.sanitizeHeatCapacity(nbt.getDouble(NBTConstants.HEAT_CAPACITY));
        }
        if (nbt.hasKey(NBTConstants.STORED)) {
            newStoredHeat = HeatAPI.sanitizeHeat(nbt.getDouble(NBTConstants.STORED), HeatAPI.multiplyHeat(getAmbientTemperature(), newHeatCapacity));
        }
        if (heatCapacity != newHeatCapacity || storedHeat != newStoredHeat) {
            heatCapacity = newHeatCapacity;
            storedHeat = newStoredHeat;
            onContentsChanged();
        }
    }

    public void updateHeatAndCapacity(double newCapacity) {
        setHeatCapacity(newCapacity, true);
    }

    public void setHeatCapacity(double newCapacity, boolean updateHeat) {
        newCapacity = HeatAPI.sanitizeHeatCapacity(newCapacity);
        double oldCapacity = heatCapacity;
        double oldHeat = storedHeat;
        if (updateHeat && storedHeat != -1) {
            double capacityChange = newCapacity - oldCapacity;
            double heatChange = HeatAPI.multiplyHeat(getAmbientTemperature(), Math.abs(capacityChange));
            storedHeat = HeatAPI.addHeatClamped(storedHeat, capacityChange < 0 ? -heatChange : heatChange);
        }
        heatCapacity = newCapacity;
        if (oldCapacity != heatCapacity || oldHeat != storedHeat) {
            onContentsChanged();
        }
    }

    public void setHeatCapacityFromPacket(double newCapacity) {
        heatCapacity = HeatAPI.sanitizeHeatCapacity(newCapacity);
    }

    @Override
    public void onContentsChanged() {
        if (listener != null) {
            listener.onContentsChanged();
        }
    }
}
