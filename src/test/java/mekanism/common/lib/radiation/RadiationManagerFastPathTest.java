package mekanism.common.lib.radiation;

import mekanism.api.Coord4D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RadiationManagerFastPathTest {

    @Test
    void emptySourceTableReturnsBaselineWithoutBuildingAChunkSearchArea() {
        RadiationManager manager = new RadiationManager();

        assertEquals(RadiationManager.BASELINE, manager.getRadiationLevel(new Coord4D(0, 64, 0, 0)));
    }

    @Test
    void clientParticleBoundHandlesDisabledAndExtremeConfigurations() {
        assertEquals(0, RadiationManager.getClientParticleRandomBound(RadiationManager.RadiationScale.NONE, 100, 256));
        assertEquals(0, RadiationManager.getClientParticleRandomBound(RadiationManager.RadiationScale.LOW, 0, 256));
        assertEquals(0, RadiationManager.getClientParticleRandomBound(RadiationManager.RadiationScale.LOW, -1, 256));
        assertEquals(0, RadiationManager.getClientParticleRandomBound(RadiationManager.RadiationScale.LOW, 100, 0));
        assertEquals(100, RadiationManager.getClientParticleRandomBound(RadiationManager.RadiationScale.LOW, 100, 256));
        assertEquals(256, RadiationManager.getClientParticleRandomBound(RadiationManager.RadiationScale.EXTREME, 100, 256));
        assertEquals(10_000, RadiationManager.getClientParticleRandomBound(
              RadiationManager.RadiationScale.EXTREME, Integer.MAX_VALUE, 10_000));
    }
}
