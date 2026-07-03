package mekanism.common.capabilities.heat;

import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.HeatAPI.HeatTransfer;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.heat.IHeatHandler;
import mekanism.api.heat.IMekanismHeatHandler;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;

public interface ITileHeatHandler extends IMekanismHeatHandler {

    default void updateHeatCapacitors(@Nullable EnumFacing side) {
        for (IHeatCapacitor capacitor : getHeatCapacitors(side)) {
            if (capacitor instanceof BasicHeatCapacitor) {
                ((BasicHeatCapacitor) capacitor).update();
            }
        }
    }

    @Nullable
    default IHeatHandler getAdjacent(EnumFacing side) {
        return null;
    }

    default HeatTransfer simulate() {
        return new HeatTransfer(simulateAdjacent(), simulateEnvironment());
    }

    default double getAmbientTemperature(EnumFacing side) {
        return HeatAPI.AMBIENT_TEMP;
    }

    default double simulateEnvironment() {
        double environmentTransfer = 0;
        for (EnumFacing side : EnumFacing.VALUES) {
            double heatCapacity = getTotalHeatCapacity(side);
            double invConduction = HeatAPI.AIR_INVERSE_COEFFICIENT + getTotalInverseInsulation(side) + getTotalInverseConductionCoefficient(side);
            double tempToTransfer = (getTotalTemperature(side) - getAmbientTemperature(side)) / invConduction;
            handleHeat(-tempToTransfer * heatCapacity, side);
            if (tempToTransfer > 0) {
                environmentTransfer += tempToTransfer;
            }
        }
        return environmentTransfer;
    }

    default double simulateAdjacent() {
        double adjacentTransfer = 0;
        for (EnumFacing side : EnumFacing.VALUES) {
            IHeatHandler sink = getAdjacent(side);
            if (sink != null) {
                double heatCapacity = getTotalHeatCapacity(side);
                double invConduction = sink.getTotalInverseConduction() + getTotalInverseConductionCoefficient(side);
                double tempToTransfer = (getTotalTemperature(side) - getAmbientTemperature(side)) / invConduction;
                double heatToTransfer = tempToTransfer * heatCapacity;
                handleHeat(-heatToTransfer, side);
                sink.handleHeat(heatToTransfer);
                adjacentTransfer = incrementAdjacentTransfer(adjacentTransfer, tempToTransfer, side);
            }
        }
        return adjacentTransfer;
    }

    default double incrementAdjacentTransfer(double currentAdjacentTransfer, double tempToTransfer, EnumFacing side) {
        return currentAdjacentTransfer + tempToTransfer;
    }
}
