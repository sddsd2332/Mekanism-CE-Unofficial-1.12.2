package mekanism.common.capabilities.heat;

import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.HeatAPI.HeatTransfer;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.heat.IHeatHandler;
import mekanism.api.heat.IMekanismHeatHandler;
import mekanism.api.heat.ISidedHeatHandler;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.Objects;

public interface ITileHeatHandler extends IMekanismHeatHandler {

    default void updateHeatCapacitors(@Nullable EnumFacing side) {
        // Kept for source compatibility. Heat changes are immediately visible.
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
            double sideTransfer = simulateEnvironment(side);
            if (sideTransfer > 0) {
                environmentTransfer = sideTransfer >= HeatAPI.MAX_HEAT - environmentTransfer ? HeatAPI.MAX_HEAT : environmentTransfer + sideTransfer;
            }
        }
        return environmentTransfer;
    }

    /** Simulates environmental exchange for one exposed side. */
    default double simulateEnvironment(EnumFacing side) {
        double heatCapacity = getTotalHeatCapacity(side);
        if (!HeatAPI.isFinite(heatCapacity) || heatCapacity < 1) {
            return 0;
        }
        double invConduction = HeatAPI.AIR_INVERSE_COEFFICIENT + getTotalInverseInsulation(side) + getTotalInverseConductionCoefficient(side);
        if (!HeatAPI.isFinite(invConduction) || invConduction <= 0) {
            invConduction = HeatAPI.MAX_HEAT;
        }
        double temperature = HeatAPI.sanitizeTemperature(getTotalTemperature(side));
        double ambient = HeatAPI.sanitizeTemperature(getAmbientTemperature(side));
        double tempToTransfer = (temperature - ambient) / invConduction;
        if (!HeatAPI.isFinite(tempToTransfer)) {
            return 0;
        }
        double heatToTransfer = HeatAPI.multiplyHeatSigned(tempToTransfer, heatCapacity);
        if (HeatAPI.isFinite(heatToTransfer) && Math.abs(heatToTransfer) > HeatAPI.EPSILON) {
            double[] before = snapshotHandlerHeat(this, side);
            handleHeat(-heatToTransfer, side);
            double[] after = snapshotHandlerHeat(this, side);
            double actualHeat = heatToTransfer > 0 ? getHeatDecrease(before, after) : -getHeatIncrease(before, after);
            if (HeatAPI.isFinite(actualHeat) && heatCapacity > 0) {
                tempToTransfer = actualHeat / heatCapacity;
            }
        }
        return tempToTransfer > 0 ? tempToTransfer : 0;
    }

    default double simulateAdjacent() {
        double adjacentTransfer = 0;
        for (EnumFacing side : EnumFacing.VALUES) {
            double sideTransfer = simulateAdjacent(side);
            if (sideTransfer > 0) {
                adjacentTransfer = incrementAdjacentTransfer(adjacentTransfer, sideTransfer, side);
            }
        }
        return adjacentTransfer;
    }

    /** Simulates adjacent exchange for one exposed side. Only the hotter side initiates a transfer. */
    default double simulateAdjacent(EnumFacing side) {
        IHeatHandler sink = getAdjacent(side);
        Object sourceIdentity = getHeatIdentity(side);
        Object sinkIdentity = sink == null ? null : sink.getHeatIdentity();
        if (sink == null || sourceIdentity != null && Objects.equals(sourceIdentity, sinkIdentity)) {
            return 0;
        }
        double temp = HeatAPI.sanitizeTemperature(getTotalTemperature(side));
        double sinkTemp = HeatAPI.sanitizeTemperature(sink.getTotalTemperature());
        if (temp <= sinkTemp) {
            return 0;
        }
        double heatCapacity = getTotalHeatCapacity(side);
        double sinkHeatCapacity = sink.getTotalHeatCapacity();
        if (!HeatAPI.isFinite(heatCapacity) || heatCapacity < 1 || !HeatAPI.isFinite(sinkHeatCapacity) || sinkHeatCapacity < 1) {
            return 0;
        }
        double finalTemp = HeatAPI.getFinalTemperature(temp, heatCapacity, sinkTemp, sinkHeatCapacity);
        double invConduction = Math.max(1, sink.getTotalInverseConduction()) + Math.max(1, getTotalInverseConductionCoefficient(side));
        if (!HeatAPI.isFinite(invConduction) || invConduction <= 0) {
            invConduction = HeatAPI.MAX_HEAT;
        }
        double tempToTransfer = (temp - finalTemp) / invConduction;
        double heatToTransfer = HeatAPI.multiplyHeat(tempToTransfer, heatCapacity);
        if (!HeatAPI.isFinite(tempToTransfer) || !HeatAPI.isFinite(heatToTransfer) || heatToTransfer <= HeatAPI.EPSILON) {
            return 0;
        }

        // Limit the request to the finite energy available on both sides. This prevents a
        // saturated sink from silently destroying heat that was already removed from the source.
        double[] sourceBefore = snapshotHandlerHeat(this, side);
        double[] sinkBefore = snapshotHandlerHeat(sink, null);
        double sourceAvailable = getAvailableHeat(sourceBefore);
        double sinkRoom = getAvailableHeatRoom(sinkBefore);
        heatToTransfer = Math.min(heatToTransfer, Math.min(sourceAvailable, sinkRoom));
        if (!HeatAPI.isFinite(heatToTransfer) || heatToTransfer <= HeatAPI.EPSILON) {
            return 0;
        }
        handleHeat(-heatToTransfer, side);
        sink.handleHeat(heatToTransfer);
        double removed = getHeatDecrease(sourceBefore, snapshotHandlerHeat(this, side));
        double accepted = getHeatIncrease(sinkBefore, snapshotHandlerHeat(sink, null));
        if (accepted < removed - HeatAPI.EPSILON) {
            //Restore any heat the sink could not accept.
            handleHeat(removed - accepted, side);
        } else if (removed < accepted - HeatAPI.EPSILON) {
            //Do not create heat if a custom handler reports more than it received.
            sink.handleHeat(-(accepted - removed));
        }
        double actualTransfer = Math.min(removed, accepted);
        return actualTransfer > HeatAPI.EPSILON ? actualTransfer / heatCapacity : 0;
    }

    default double incrementAdjacentTransfer(double currentAdjacentTransfer, double tempToTransfer, EnumFacing side) {
        return currentAdjacentTransfer + tempToTransfer;
    }

    /** Returns a finite total heat estimate without losing unsaturated child capacitors. */
    static double getHandlerHeat(IHeatHandler handler, @Nullable EnumFacing side) {
        return getAvailableHeat(snapshotHandlerHeat(handler, side));
    }

    static double[] snapshotHandlerHeat(IHeatHandler handler, @Nullable EnumFacing side) {
        int count = side != null && handler instanceof ISidedHeatHandler sided ?
              sided.getHeatCapacitorCount(side) : handler.getHeatCapacitorCount();
        if (count <= 0) {
            return new double[0];
        }
        double[] heat = new double[count];
        for (int capacitor = 0; capacitor < count; capacitor++) {
            double temperature;
            double capacity;
            if (side != null && handler instanceof ISidedHeatHandler sided) {
                temperature = sided.getTemperature(capacitor, side);
                capacity = sided.getHeatCapacity(capacitor, side);
            } else {
                temperature = handler.getTemperature(capacitor);
                capacity = handler.getHeatCapacity(capacitor);
            }
            heat[capacitor] = HeatAPI.multiplyHeat(HeatAPI.sanitizeTemperature(temperature),
                  HeatAPI.sanitizeHeatCapacity(capacity));
        }
        return heat;
    }

    static double getAvailableHeat(double[] heat) {
        double total = 0;
        for (double stored : heat) {
            if (stored > 0) {
                total = stored >= HeatAPI.MAX_HEAT - total ? HeatAPI.MAX_HEAT : total + stored;
            }
        }
        return total;
    }

    static double getAvailableHeatRoom(double[] heat) {
        double total = 0;
        for (double stored : heat) {
            double room = Math.max(0, HeatAPI.MAX_HEAT - stored);
            if (room > 0) {
                total = room >= HeatAPI.MAX_HEAT - total ? HeatAPI.MAX_HEAT : total + room;
            }
        }
        return total;
    }

    static double getHeatDecrease(double[] before, double[] after) {
        return getHeatChange(before, after, false);
    }

    static double getHeatIncrease(double[] before, double[] after) {
        return getHeatChange(before, after, true);
    }

    static double getHeatChange(double[] before, double[] after, boolean increase) {
        double total = 0;
        int count = Math.min(before.length, after.length);
        for (int capacitor = 0; capacitor < count; capacitor++) {
            double change = increase ? after[capacitor] - before[capacitor] : before[capacitor] - after[capacitor];
            if (HeatAPI.isFinite(change) && change > 0) {
                total = change >= HeatAPI.MAX_HEAT - total ? HeatAPI.MAX_HEAT : total + change;
            }
        }
        return total;
    }
}
