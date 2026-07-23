package mekanism.common.tile;

import mekanism.common.content.boiler.SynchronizedBoilerData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileEntitySuperheatingElementTest {

    private static final String MULTIBLOCK_ID = "superheating-test";

    @AfterEach
    void cleanupHotState() {
        SynchronizedBoilerData.hotMap.remove(MULTIBLOCK_ID);
        SynchronizedBoilerData.clientHotMap.remove(MULTIBLOCK_ID);
    }

    @Test
    void missingRendererStatePreservesSynchronizedState() {
        assertTrue(TileEntitySuperheatingElement.resolveClientActive(MULTIBLOCK_ID, true));
        assertFalse(TileEntitySuperheatingElement.resolveClientActive(MULTIBLOCK_ID, false));
    }

    @Test
    void rendererStateOverridesSynchronizedState() {
        SynchronizedBoilerData.clientHotMap.put(MULTIBLOCK_ID, false);
        assertFalse(TileEntitySuperheatingElement.resolveClientActive(MULTIBLOCK_ID, true));

        SynchronizedBoilerData.clientHotMap.put(MULTIBLOCK_ID, true);
        assertTrue(TileEntitySuperheatingElement.resolveClientActive(MULTIBLOCK_ID, false));
    }

    @Test
    void detachedElementIsAlwaysInactive() {
        SynchronizedBoilerData.clientHotMap.put(MULTIBLOCK_ID, true);
        assertFalse(TileEntitySuperheatingElement.resolveClientActive(null, true));
    }

    @Test
    void serverRebindingImmediatelyUsesSharedBoilerState() {
        TileEntitySuperheatingElement element = new TileEntitySuperheatingElement();
        SynchronizedBoilerData.hotMap.put(MULTIBLOCK_ID, true);

        element.setMultiblock(MULTIBLOCK_ID);
        assertTrue(element.getActive());

        element.setMultiblock(null);
        assertFalse(element.getActive());
    }
}
