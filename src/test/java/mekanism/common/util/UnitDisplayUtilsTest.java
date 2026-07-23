package mekanism.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnitDisplayUtilsTest {

    @Test
    void decimalFormattingDoesNotNarrowLargeValuesToInt() {
        assertEquals(1.0E30, UnitDisplayUtils.roundDecimals(1.0E30, 2));
        assertEquals(-12.34, UnitDisplayUtils.roundDecimals(-12.349, 2), 1.0E-12);
    }
}
