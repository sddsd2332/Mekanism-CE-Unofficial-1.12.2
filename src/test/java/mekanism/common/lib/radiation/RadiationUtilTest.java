package mekanism.common.lib.radiation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RadiationUtilTest {

    @Test
    void closedFormDecayTimeHandlesNormalAndTerminalRates() {
        assertEquals(140, RadiationUtil.getDecayTime(0.001, 0.5));
        assertEquals(0, RadiationUtil.getDecayTime(RadiationManager.MIN_MAGNITUDE, 0.5));
        assertEquals(20, RadiationUtil.getDecayTime(1, 0));
        assertEquals(Long.MAX_VALUE, RadiationUtil.getDecayTime(1, 1));
    }

    @Test
    void sanitizationRejectsInvalidValuesAndSaturatesInfinity() {
        assertEquals(0, RadiationUtil.sanitizeMagnitude(Double.NaN));
        assertEquals(0, RadiationUtil.sanitizeMagnitude(-1));
        assertEquals(Double.MAX_VALUE, RadiationUtil.sanitizeMagnitude(Double.POSITIVE_INFINITY));
        assertEquals(RadiationManager.BASELINE, RadiationUtil.sanitizeAtLeastBaseline(0));
    }

    @Test
    void additionSaturatesWithoutOverflowing() {
        assertEquals(3, RadiationUtil.addClamped(1, 2));
        assertEquals(Double.MAX_VALUE, RadiationUtil.addClamped(Double.MAX_VALUE, 1));
        assertEquals(1, RadiationUtil.addClamped(1, Double.NaN));
    }

    @Test
    void scalesTreatNaNAsNoRadiationAndInfinityAsExtreme() {
        assertEquals(RadiationManager.RadiationScale.NONE, RadiationManager.RadiationScale.get(Double.NaN));
        assertEquals(RadiationManager.RadiationScale.EXTREME, RadiationManager.RadiationScale.get(Double.POSITIVE_INFINITY));
        assertEquals(0, RadiationManager.RadiationScale.getScaledDoseSeverity(Double.NaN));
    }
}
