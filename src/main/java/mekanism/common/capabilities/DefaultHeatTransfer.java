package mekanism.common.capabilities;

import mekanism.api.IHeatTransfer;
import mekanism.common.capabilities.DefaultStorageHelper.NullStorage;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.CapabilityManager;

/** @deprecated Compatibility registration for the pre-capacitor heat API. */
@Deprecated
@SuppressWarnings("removal")
public class DefaultHeatTransfer implements IHeatTransfer {

    public static void register() {
        CapabilityManager.INSTANCE.register(IHeatTransfer.class, new NullStorage<>(), DefaultHeatTransfer::new);
    }

    @Override
    public double getTemp() {
        return 0;
    }

    @Override
    public double getInverseConductionCoefficient() {
        return 0;
    }

    @Override
    public double getInsulationCoefficient(EnumFacing side) {
        return 0;
    }

    @Override
    public void transferHeatTo(double heat) {
    }

    @Override
    public double[] simulateHeat() {
        return new double[]{0, 0};
    }

    @Override
    public double applyTemperatureChange() {
        return 0;
    }

    @Override
    public boolean canConnectHeat(EnumFacing side) {
        return false;
    }

    @Override
    public IHeatTransfer getAdjacent(EnumFacing side) {
        return null;
    }
}
