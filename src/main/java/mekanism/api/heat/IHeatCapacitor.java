package mekanism.api.heat;

import mekanism.api.IContentsListener;
import mekanism.api.NBTConstants;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;

public interface IHeatCapacitor extends INBTSerializable<NBTTagCompound>, IContentsListener {

    double getTemperature();

    double getInverseConduction();

    double getInverseInsulation();

    double getHeatCapacity();

    double getHeat();

    void setHeat(double heat);

    void handleHeat(double transfer);

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
            setHeat(nbt.getDouble(NBTConstants.STORED));
        }
    }

    @Override
    default void onContentsChanged() {
    }
}
