package mekanism.common.inventory.container;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectedWindowDataTest {

    @Test
    void qioFrequencyWindowCanBePinned() {
        assertTrue(SelectedWindowData.WindowType.QIO_FREQUENCY.canPin());
    }
}
