package mekanism.api.heat;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public final class HeatAPI {

    private HeatAPI() {
    }

    public static final double AMBIENT_TEMP = 300;
    public static final double AIR_INVERSE_COEFFICIENT = 10_000;
    public static final double DEFAULT_HEAT_CAPACITY = 1;
    public static final double DEFAULT_INVERSE_CONDUCTION = 1;
    public static final double DEFAULT_INVERSE_INSULATION = 0;
    public static final double EPSILON = 1.0E-6;
    /**
     * Keeps heat calculations finite while leaving substantially more range than any legitimate machine can reach.
     */
    public static final double MAX_HEAT = Double.MAX_VALUE / 4D;

    public static double getAmbientTemp(double biomeTemp) {
        // A malformed biome provider must not poison every capacitor in the block.
        if (Double.isNaN(biomeTemp)) {
            biomeTemp = 0.8D;
        }
        biomeTemp = Math.max(-5, Math.min(5, biomeTemp));
        return AMBIENT_TEMP + 25 * (biomeTemp - 0.8);
    }

    public static double getAmbientTemp(@Nullable World world, BlockPos pos) {
        if (world == null) {
            return AMBIENT_TEMP;
        }
        return getAmbientTemp(world.getBiomeForCoordsBody(pos).getTemperature(pos));
    }

    public static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public static double sanitizeHeat(double heat, double fallback) {
        if (!isFinite(heat)) {
            return isFinite(fallback) ? Math.max(0, Math.min(MAX_HEAT, fallback)) : 0;
        }
        return Math.max(0, Math.min(MAX_HEAT, heat));
    }

    public static double sanitizeHeatCapacity(double heatCapacity) {
        if (Double.isNaN(heatCapacity) || heatCapacity < 1) {
            return DEFAULT_HEAT_CAPACITY;
        }
        return Math.min(MAX_HEAT, heatCapacity);
    }

    public static double multiplyHeat(double temperature, double heatCapacity) {
        if (Double.isNaN(temperature) || temperature <= 0 || Double.isNaN(heatCapacity) || heatCapacity <= 0) {
            return 0;
        }
        if (temperature == Double.POSITIVE_INFINITY || heatCapacity == Double.POSITIVE_INFINITY) {
            return MAX_HEAT;
        }
        return temperature >= MAX_HEAT / heatCapacity ? MAX_HEAT : temperature * heatCapacity;
    }

    public static double multiplyHeatSigned(double temperatureDifference, double heatCapacity) {
        if (Double.isNaN(temperatureDifference) || temperatureDifference == 0) {
            return 0;
        }
        return Math.copySign(multiplyHeat(Math.abs(temperatureDifference), heatCapacity), temperatureDifference);
    }

    public static double addHeatClamped(double heat, double transfer) {
        double current = sanitizeHeat(heat, 0);
        if (!isFinite(transfer) || Math.abs(transfer) <= EPSILON) {
            return current;
        }
        if (transfer > 0 && transfer >= MAX_HEAT - current) {
            return MAX_HEAT;
        }
        return Math.max(0, current + transfer);
    }

    public static double getFinalTemperature(double sourceTemperature, double sourceHeatCapacity,
          double sinkTemperature, double sinkHeatCapacity) {
        sourceTemperature = sanitizeTemperature(sourceTemperature);
        sinkTemperature = sanitizeTemperature(sinkTemperature);
        double sourceCapacity = sanitizeHeatCapacity(sourceHeatCapacity);
        double sinkCapacity = sanitizeHeatCapacity(sinkHeatCapacity);
        double ratio;
        if (sourceCapacity >= sinkCapacity) {
            ratio = (sinkCapacity / sourceCapacity) / (1 + sinkCapacity / sourceCapacity);
        } else {
            ratio = 1 / (1 + sourceCapacity / sinkCapacity);
        }
        return sanitizeTemperature(sourceTemperature + (sinkTemperature - sourceTemperature) * ratio);
    }

    public static double sanitizeTemperature(double temperature) {
        if (Double.isNaN(temperature)) {
            return AMBIENT_TEMP;
        } else if (temperature == Double.POSITIVE_INFINITY) {
            return MAX_HEAT;
        }
        return Math.max(0, Math.min(MAX_HEAT, temperature));
    }

    public static double sanitizeInverseConduction(double inverseConduction) {
        if (Double.isNaN(inverseConduction) || inverseConduction < 1) {
            return DEFAULT_INVERSE_CONDUCTION;
        }
        return inverseConduction == Double.POSITIVE_INFINITY ? MAX_HEAT : Math.min(MAX_HEAT, inverseConduction);
    }

    public static double sanitizeInverseInsulation(double inverseInsulation) {
        if (Double.isNaN(inverseInsulation) || inverseInsulation < 0) {
            return DEFAULT_INVERSE_INSULATION;
        }
        return inverseInsulation == Double.POSITIVE_INFINITY ? MAX_HEAT : Math.min(MAX_HEAT, inverseInsulation);
    }

    public static double getCapacityWeight(double heatCapacity, double maxHeatCapacity) {
        if (!isFinite(heatCapacity) || heatCapacity <= 0 || !isFinite(maxHeatCapacity) || maxHeatCapacity <= 0) {
            return 0;
        }
        return heatCapacity / maxHeatCapacity;
    }

    public static class HeatTransfer {

        private final double adjacentTransfer;
        private final double environmentTransfer;

        public HeatTransfer(double adjacentTransfer, double environmentTransfer) {
            this.adjacentTransfer = adjacentTransfer;
            this.environmentTransfer = environmentTransfer;
        }

        public double adjacentTransfer() {
            return adjacentTransfer;
        }

        public double environmentTransfer() {
            return environmentTransfer;
        }
    }
}
