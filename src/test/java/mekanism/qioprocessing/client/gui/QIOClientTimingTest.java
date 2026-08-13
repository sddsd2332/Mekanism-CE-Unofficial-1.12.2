package mekanism.qioprocessing.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOClientTimingTest {

    @Test
    void unsetTimestampIsImmediatelyElapsedWithoutOverflow() {
        assertTrue(QIOClientTiming.elapsed(1, Long.MIN_VALUE, 10));
    }

    @Test
    void elapsedRequiresTheRequestedDelay() {
        assertFalse(QIOClientTiming.elapsed(19, 10, 10));
        assertTrue(QIOClientTiming.elapsed(20, 10, 10));
    }

    @Test
    void timestampsThatMoveBackwardsDoNotAppearElapsed() {
        assertFalse(QIOClientTiming.elapsed(9, 10, 1));
    }
}
