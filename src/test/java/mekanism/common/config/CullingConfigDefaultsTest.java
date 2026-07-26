package mekanism.common.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CullingConfigDefaultsTest {

    @Test
    void visualCullingAndDistanceLodsAreOptIn() {
        ClientConfig config = new ClientConfig();

        assertFalse(config.GazeCullingTracking.val());
        assertFalse(config.GazeCullingOpenGLTracking.val());
        assertFalse(config.enableSelectionWireframeRendering.val());
        assertEquals(0, config.windGeneratorBladeRenderDistance.val());
        assertEquals(0, config.largeWindGeneratorFanRenderDistance.val());
    }
}
