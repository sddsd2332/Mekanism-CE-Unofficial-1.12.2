package mekanism.common.tile.qio;

import mekanism.common.base.IEnergyWrapper;
import mekanism.qioprocessing.common.tile.QIOCraftingProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QIOEnergyExposureTest {

    @Test
    void ordinaryQIOComponentsAreNotEnergyTiles() {
        assertFalse(IEnergyWrapper.class.isAssignableFrom(TileEntityQIOComponent.class));
        assertFalse(IEnergyWrapper.class.isAssignableFrom(TileEntityQIODashboard.class));
        assertFalse(IEnergyWrapper.class.isAssignableFrom(TileEntityQIODriveArray.class));
        assertFalse(IEnergyWrapper.class.isAssignableFrom(TileEntityQIOImporter.class));
        assertFalse(IEnergyWrapper.class.isAssignableFrom(TileEntityQIOExporter.class));
        assertFalse(IEnergyWrapper.class.isAssignableFrom(TileEntityQIORedstoneAdapter.class));
    }

    @Test
    void craftingProcessorRemainsAnEnergyTile() {
        assertTrue(IEnergyWrapper.class.isAssignableFrom(QIOCraftingProcessor.class));
    }
}
