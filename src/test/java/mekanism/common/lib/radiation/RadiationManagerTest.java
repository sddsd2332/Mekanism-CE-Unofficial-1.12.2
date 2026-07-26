package mekanism.common.lib.radiation;

import mekanism.api.Chunk3D;
import mekanism.api.Coord4D;
import mekanism.common.config.MekanismConfig;
import mekanism.common.TestBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RadiationManagerTest {

    private int originalChunkRadius;
    private double originalDecayRate;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @BeforeEach
    void saveConfig() {
        originalChunkRadius = MekanismConfig.current().general.radiationChunkCheckRadius.val();
        originalDecayRate = MekanismConfig.current().general.radiationSourceDecayRate.val();
        MekanismConfig.current().general.radiationChunkCheckRadius.set(5);
    }

    @AfterEach
    void restoreConfig() {
        MekanismConfig.current().general.radiationChunkCheckRadius.set(originalChunkRadius);
        MekanismConfig.current().general.radiationSourceDecayRate.set(originalDecayRate);
    }

    @Test
    void cuboidBoundaryIncludesCornersButExcludesNextBlock() {
        RadiationManager manager = new RadiationManager();
        Coord4D target = new Coord4D(0, 64, 0, 0);
        manager.radiate(new Coord4D(80, 64, 80, 0), 1);
        manager.radiate(new Coord4D(81, 64, 0, 0), 10);

        LevelAndMaxMagnitude result = manager.getRadiationLevelAndMaxMagnitude(target);

        assertEquals(RadiationManager.BASELINE + 1D / 12_800D, result.getLevel(), 1E-12);
        assertEquals(1, result.getMaxMagnitude());
    }

    @Test
    void dimensionsAreIsolated() {
        RadiationManager manager = new RadiationManager();
        manager.radiate(new Coord4D(0, 64, 0, 1), 5);

        assertEquals(RadiationManager.BASELINE, manager.getRadiationLevel(new Coord4D(0, 64, 0, 0)));
        assertEquals(RadiationManager.BASELINE + 5, manager.getRadiationLevel(new Coord4D(0, 64, 0, 1)));
    }

    @Test
    void radiationAtSamePositionMergesWithoutChangingCounts() {
        RadiationManager manager = new RadiationManager();
        Coord4D source = new Coord4D(1, 64, 1, 0);

        manager.radiate(source, 2);
        manager.radiate(source, 3);

        assertEquals(1, manager.getSourceCount(0));
        assertEquals(1, manager.getChunkCount(0));
        assertEquals(5, manager.getRadiationSources().get(new Chunk3D(source), source).getMagnitude());
    }

    @Test
    void chunkAndSingleRemovalMaintainDimensionCounts() {
        RadiationManager manager = new RadiationManager();
        Coord4D first = new Coord4D(1, 64, 1, 0);
        Coord4D second = new Coord4D(2, 64, 2, 0);
        Coord4D third = new Coord4D(32, 64, 0, 0);
        manager.radiate(first, 1);
        manager.radiate(second, 1);
        manager.radiate(third, 1);

        manager.removeRadiationSource(first);
        assertEquals(2, manager.getSourceCount(0));
        assertEquals(2, manager.getChunkCount(0));

        manager.removeRadiationSources(new Chunk3D(second));
        assertEquals(1, manager.getSourceCount(0));
        assertEquals(1, manager.getChunkCount(0));

        manager.removeRadiationSource(third);
        assertFalse(manager.hasRadiationSources(0));
        assertEquals(0, manager.getChunkCount(0));
    }

    @Test
    void sparseAndDenseIndexesProduceSameLocalResult() {
        Coord4D target = new Coord4D(0, 64, 0, 0);
        Coord4D localSource = new Coord4D(10, 64, 0, 0);
        RadiationManager sparse = new RadiationManager();
        sparse.radiate(localSource, 2);

        RadiationManager dense = new RadiationManager();
        dense.radiate(localSource, 2);
        for (int chunk = 1_000; chunk < 1_122; chunk++) {
            dense.radiate(new Coord4D(chunk * 16, 64, 0, 0), 1);
        }

        assertTrue(dense.getChunkCount(0) > 121);
        assertEquals(sparse.getRadiationLevel(target), dense.getRadiationLevel(target), 0);
    }

    @Test
    void decayRemovesSourcesAndTheirChunkIndex() {
        RadiationManager manager = new RadiationManager();
        manager.radiate(new Coord4D(0, 64, 0, 0), 1);
        MekanismConfig.current().general.radiationSourceDecayRate.set(0);

        manager.decaySources();

        assertFalse(manager.hasRadiationSources(0));
        assertEquals(0, manager.getSourceCount(0));
        assertEquals(0, manager.getChunkCount(0));
    }

    @Test
    void invalidSourcesAreIgnoredTinySourcesAccumulateAndInfinitySaturates() {
        RadiationManager manager = new RadiationManager();
        Coord4D source = new Coord4D(0, 64, 0, 0);
        manager.radiate(source, Double.NaN);
        manager.radiate(source, -1);
        assertFalse(manager.hasRadiationSources(0));

        manager.radiate(source, RadiationManager.MIN_MAGNITUDE / 2);
        manager.radiate(source, RadiationManager.MIN_MAGNITUDE / 2);
        assertTrue(manager.hasRadiationSources(0));
        assertEquals(RadiationManager.MIN_MAGNITUDE,
              manager.getRadiationSources().get(new Chunk3D(source), source).getMagnitude());

        manager.radiate(source, Double.POSITIVE_INFINITY);
        assertEquals(Double.MAX_VALUE, manager.getRadiationSources().get(new Chunk3D(source), source).getMagnitude());
    }
}
