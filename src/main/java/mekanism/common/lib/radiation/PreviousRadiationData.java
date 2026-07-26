package mekanism.common.lib.radiation;

import javax.annotation.Nullable;

/** Tracks the smallest change visible at the two-decimal SI precision used by radiation displays. */
final class PreviousRadiationData {

    private final double magnitude;
    private final int power;
    private final double base;

    private PreviousRadiationData(double magnitude, int power, double base) {
        this.magnitude = magnitude;
        this.power = power;
        this.base = base;
    }

    static PreviousRadiationData of(double magnitude) {
        double sanitized = RadiationUtil.sanitizeAtLeastBaseline(magnitude);
        int power = getPower(sanitized);
        int siPower = Math.floorDiv(power, 3) * 3;
        return new PreviousRadiationData(sanitized, power, Math.pow(10, siPower - 2));
    }

    @Nullable
    static PreviousRadiationData compareTo(@Nullable PreviousRadiationData previous, double magnitude) {
        double sanitized = RadiationUtil.sanitizeAtLeastBaseline(magnitude);
        if (previous == null || Math.abs(sanitized - previous.magnitude) >= previous.base) {
            return of(sanitized);
        }
        if (sanitized < previous.magnitude) {
            int power = getPower(sanitized);
            if (power < previous.power) {
                return of(sanitized);
            }
        }
        return null;
    }

    private static int getPower(double magnitude) {
        return (int) Math.floor(Math.log10(magnitude));
    }
}
