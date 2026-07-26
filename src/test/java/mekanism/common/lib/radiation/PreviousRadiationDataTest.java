package mekanism.common.lib.radiation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreviousRadiationDataTest {

    @Test
    void suppressesChangesBelowDisplayedSiPrecision() {
        PreviousRadiationData previous = PreviousRadiationData.of(1);

        assertNull(PreviousRadiationData.compareTo(previous, 1.009));
        assertNotNull(PreviousRadiationData.compareTo(previous, 1.01));
    }

    @Test
    void synchronizesWhenMagnitudeDropsToANewDigitCount() {
        PreviousRadiationData previous = PreviousRadiationData.of(1);

        assertNotNull(PreviousRadiationData.compareTo(previous, 0.999));
    }

    @Test
    void firstValueAndInvalidValuesProduceStableState() {
        assertNotNull(PreviousRadiationData.compareTo(null, 1));
        PreviousRadiationData nan = PreviousRadiationData.of(Double.NaN);
        assertNull(PreviousRadiationData.compareTo(nan, RadiationManager.BASELINE));
    }
}
