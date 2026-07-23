package mekanism.api.heat;

import mekanism.api.IContentsListener;
import mekanism.api.NBTConstants;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;

public interface IHeatCapacitor extends IHeatHandler, INBTSerializable<NBTTagCompound>, IContentsListener {

    double getTemperature();

    double getInverseConduction();

    double getInverseInsulation();

    double getHeatCapacity();

    double getHeat();

    void setHeat(double heat);

    void handleHeat(double transfer);

    @Override
    default Object getHeatIdentity() {
        return this;
    }

    @Override
    default int getHeatCapacitorCount() {
        return 1;
    }

    @Override
    default double getTemperature(int capacitor) {
        return capacitor == 0 ? getTemperature() : HeatAPI.AMBIENT_TEMP;
    }

    @Override
    default double getInverseConduction(int capacitor) {
        return capacitor == 0 ? getInverseConduction() : HeatAPI.DEFAULT_INVERSE_CONDUCTION;
    }

    @Override
    default double getHeatCapacity(int capacitor) {
        return capacitor == 0 ? getHeatCapacity() : HeatAPI.DEFAULT_HEAT_CAPACITY;
    }

    @Override
    default void handleHeat(int capacitor, double transfer) {
        if (capacitor == 0) {
            handleHeat(transfer);
        }
    }

    default boolean isAmbientTemperature() {
        return Math.abs(getTemperature() - HeatAPI.AMBIENT_TEMP) < HeatAPI.EPSILON;
    }

    @Override
    default NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setDouble(NBTConstants.STORED, getHeat());
        return nbt;
    }

    @Override
    default void deserializeNBT(NBTTagCompound nbt) {
        if (nbt.hasKey(NBTConstants.STORED)) {
            setHeat(HeatAPI.sanitizeHeat(nbt.getDouble(NBTConstants.STORED), HeatAPI.multiplyHeat(HeatAPI.AMBIENT_TEMP, getHeatCapacity())));
        }
    }

    @Override
    default void onContentsChanged() {
    }
}
