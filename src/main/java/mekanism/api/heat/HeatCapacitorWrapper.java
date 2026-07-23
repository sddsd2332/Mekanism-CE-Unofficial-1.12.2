package mekanism.api.heat;

import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;

public class HeatCapacitorWrapper implements IHeatCapacitor {

    protected final IHeatCapacitor internal;

    public HeatCapacitorWrapper(@Nonnull IHeatCapacitor internal) {
        this.internal = internal;
    }

    @Override
    public Object getHeatIdentity() {
        return internal.getHeatIdentity();
    }

    @Override
    public double getTemperature() {
        return internal.getTemperature();
    }

    @Override
    public double getInverseConduction() {
        return internal.getInverseConduction();
    }

    @Override
    public double getInverseInsulation() {
        return internal.getInverseInsulation();
    }

    @Override
    public double getHeatCapacity() {
        return internal.getHeatCapacity();
    }

    @Override
    public double getHeat() {
        return internal.getHeat();
    }

    @Override
    public void setHeat(double heat) {
        internal.setHeat(heat);
    }

    @Override
    public void handleHeat(double transfer) {
        internal.handleHeat(transfer);
    }

    @Override
    public boolean isAmbientTemperature() {
        return internal.isAmbientTemperature();
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return internal.serializeNBT();
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        internal.deserializeNBT(nbt);
    }

    @Override
    public void onContentsChanged() {
        internal.onContentsChanged();
    }
}
