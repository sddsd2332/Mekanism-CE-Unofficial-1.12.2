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
    private double heatToHandle;

    public static BasicHeatCapacitor create(double heatCapacity, @Nullable DoubleSupplier ambientTempSupplier, @Nullable IContentsListener listener) {
        return create(heatCapacity, HeatAPI.DEFAULT_INVERSE_CONDUCTION, HeatAPI.DEFAULT_INVERSE_INSULATION, ambientTempSupplier, listener);
    }

    public static BasicHeatCapacitor create(double heatCapacity, double inverseConductionCoefficient, double inverseInsulationCoefficient,
          @Nullable DoubleSupplier ambientTempSupplier, @Nullable IContentsListener listener) {
        if (heatCapacity < 1) {
            throw new IllegalArgumentException("Heat capacity must be at least one");
        }
        if (inverseConductionCoefficient < 1) {
            throw new IllegalArgumentException("Inverse conduction coefficient must be at least one");
        }
        return new BasicHeatCapacitor(heatCapacity, inverseConductionCoefficient, inverseInsulationCoefficient, ambientTempSupplier, listener);
    }

    protected BasicHeatCapacitor(double heatCapacity, double inverseConductionCoefficient, double inverseInsulationCoefficient,
          @Nullable DoubleSupplier ambientTempSupplier, @Nullable IContentsListener listener) {
        this.heatCapacity = heatCapacity;
        this.inverseConductionCoefficient = inverseConductionCoefficient;
        this.inverseInsulationCoefficient = inverseInsulationCoefficient;
        this.ambientTempSupplier = ambientTempSupplier;
        this.listener = listener;
    }

    private void initStoredHeat() {
        if (storedHeat == -1) {
            storedHeat = heatCapacity * getAmbientTemperature();
        }
    }

    protected double getAmbientTemperature() {
        return ambientTempSupplier == null ? HeatAPI.AMBIENT_TEMP : ambientTempSupplier.getAsDouble();
    }

    @Override
    public double getTemperature() {
        return getHeat() / getHeatCapacity();
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
        if (getHeat() != heat) {
            storedHeat = heat;
            onContentsChanged();
        }
    }

    @Override
    public void handleHeat(double transfer) {
        heatToHandle += transfer;
    }

    @Override
    public boolean isAmbientTemperature() {
        return Math.abs(getTemperature() - getAmbientTemperature()) < HeatAPI.EPSILON;
    }

    public void update() {
        if (heatToHandle != 0 && Math.abs(heatToHandle) > HeatAPI.EPSILON) {
            initStoredHeat();
            storedHeat += heatToHandle;
            heatToHandle = 0;
            onContentsChanged();
        }
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = IHeatCapacitor.super.serializeNBT();
        nbt.setDouble(NBTConstants.HEAT_CAPACITY, getHeatCapacity());
        return nbt;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        if (nbt.hasKey(NBTConstants.STORED)) {
            storedHeat = nbt.getDouble(NBTConstants.STORED);
        }
        if (nbt.hasKey(NBTConstants.HEAT_CAPACITY)) {
            setHeatCapacity(nbt.getDouble(NBTConstants.HEAT_CAPACITY), false);
        }
    }

    public void setHeatCapacity(double newCapacity, boolean updateHeat) {
        if (newCapacity < 1) {
            throw new IllegalArgumentException("Heat capacity must be at least one");
        }
        if (updateHeat && storedHeat != -1) {
            setHeat(getHeat() + (newCapacity - getHeatCapacity()) * getAmbientTemperature());
        }
        heatCapacity = newCapacity;
    }

    public void setHeatCapacityFromPacket(double newCapacity) {
        heatCapacity = newCapacity;
    }

    @Override
    public void onContentsChanged() {
        if (listener != null) {
            listener.onContentsChanged();
        }
    }
}
