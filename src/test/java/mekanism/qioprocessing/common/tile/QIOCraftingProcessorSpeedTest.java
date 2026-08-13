package mekanism.qioprocessing.common.tile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOCraftingProcessorSpeedTest {

    @Test
    void defaultUpgradeRangeScalesFromTwoHundredTicksToOne() {
        assertEquals(200, QIOCraftingProcessor.workbenchProcessingTicks(0, 8));
        assertEquals(1, QIOCraftingProcessor.workbenchProcessingTicks(8, 8));
        assertMonotonic(8);
    }

    @Test
    void addonDefinedUpgradeRangeStillUsesTheEntireCurve() {
        assertEquals(200, QIOCraftingProcessor.workbenchProcessingTicks(0, 16));
        assertTrue(QIOCraftingProcessor.workbenchProcessingTicks(8, 16) > 1);
        assertEquals(1, QIOCraftingProcessor.workbenchProcessingTicks(16, 16));
        assertMonotonic(16);
    }

    @Test
    void unavailableSpeedUpgradesRetainTheBaseDuration() {
        assertEquals(200, QIOCraftingProcessor.workbenchProcessingTicks(0, 0));
        assertEquals(200, QIOCraftingProcessor.workbenchProcessingTicks(8, 0));
    }

    private static void assertMonotonic(int limit) {
        int previous = QIOCraftingProcessor.workbenchProcessingTicks(0, limit);
        for (int installed = 1; installed <= limit; installed++) {
            int current = QIOCraftingProcessor.workbenchProcessingTicks(installed, limit);
            assertTrue(current <= previous);
            assertTrue(current >= 1);
            previous = current;
        }
    }
}
