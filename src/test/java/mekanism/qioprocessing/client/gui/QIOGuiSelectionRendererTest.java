package mekanism.qioprocessing.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QIOGuiSelectionRendererTest {

    @Test
    void supergiantPhaseCompletesOneLapEveryTwentyFourHundredMilliseconds() {
        assertEquals(0F, QIOGuiSelectionRenderer.phase(0), 0.0001F);
        assertEquals(0.25F, QIOGuiSelectionRenderer.phase(600), 0.0001F);
        assertEquals(0.5F, QIOGuiSelectionRenderer.phase(1_200), 0.0001F);
        assertEquals(0.75F, QIOGuiSelectionRenderer.phase(1_800), 0.0001F);
        assertEquals(0F, QIOGuiSelectionRenderer.phase(2_400), 0.0001F);
    }

    @Test
    void supergiantBorderUsesSeventyTwoPixelsAndRepeatsCorners() {
        assertEquals(72, QIOGuiSelectionRenderer.borderPixels(18, 18));

        assertPoint(0, 0, 0);
        assertPoint(17, 17, 0);
        assertPoint(18, 17, 0);
        assertPoint(35, 17, 17);
        assertPoint(36, 17, 17);
        assertPoint(53, 0, 17);
        assertPoint(54, 0, 17);
        assertPoint(71, 0, 0);
    }

    @Test
    void hueAdvancesClockwiseAndWrapsWithAnimationPhase() {
        int pixels = QIOGuiSelectionRenderer.borderPixels(18, 18);
        assertEquals(0F, QIOGuiSelectionRenderer.hue(0, pixels, 0F), 0.0001F);
        assertEquals(0.25F, QIOGuiSelectionRenderer.hue(18, pixels, 0F), 0.0001F);
        assertEquals(0.5F, QIOGuiSelectionRenderer.hue(36, pixels, 0F), 0.0001F);
        assertEquals(0.75F, QIOGuiSelectionRenderer.hue(54, pixels, 0F), 0.0001F);
        assertEquals(0F, QIOGuiSelectionRenderer.hue(54, pixels, 0.25F), 0.0001F);
    }

    private static void assertPoint(int index, int expectedX, int expectedY) {
        assertEquals(expectedX, QIOGuiSelectionRenderer.pixelX(18, 18, index));
        assertEquals(expectedY, QIOGuiSelectionRenderer.pixelY(18, 18, index));
    }
}
