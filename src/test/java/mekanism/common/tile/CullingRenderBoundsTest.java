package mekanism.common.tile;

import mekanism.api.Coord4D;
import mekanism.common.TestBootstrap;
import mekanism.common.content.sps.SynchronizedSPSData;
import mekanism.common.content.tank.SynchronizedTankData;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import mekanism.common.tile.multiblock.TileEntityDynamicTank;
import mekanism.common.tile.multiblock.TileEntitySPSCasing;
import mekanism.common.tile.multiblock.TileEntityThermalEvaporationController;
import mekanism.generators.common.tile.TileEntityWindGenerator;
import mekanism.generators.common.tile.turbine.TileEntityTurbineRotor;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CullingRenderBoundsTest {

    private static final BlockPos POS = new BlockPos(10, 64, -20);

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void specialMachineBoundsCoverTheirEffects() {
        assertBox(TileEntityTeleporter.getRenderBoundingBox(POS), 10, 64, -20, 11, 67, -19);
        assertBox(TileEntityWindGenerator.getRenderBoundingBox(POS), 8, 64, -22, 13, 71, -17);
        assertBox(TileEntityDigitalMiner.getVisualizationRenderBoundingBox(POS, 8, 20, 70),
              2, 20, -28, 19, 71, -11);
        assertBox(TileEntityTurbineRotor.getStandaloneRenderBoundingBox(POS), 6, 62, -24, 15, 67, -15);
    }

    @Test
    void thermalEvaporationBoundsCoverTheWholeStructure() {
        assertBox(TileEntityThermalEvaporationController.getRenderBoundingBox(new BlockPos(11, 65, -19), 18),
              10, 64, -20, 14, 82, -16);
    }

    @Test
    void multiblockRendererUsesStructureBoundsOnlyOnTheRendererTile() {
        TileEntityDynamicTank tank = new TileEntityDynamicTank();
        tank.setPos(POS);
        tank.structure = new SynchronizedTankData(tank);
        tank.structure.renderLocation = new Coord4D(10, 65, -20, 0);
        tank.structure.volLength = 5;
        tank.structure.volHeight = 6;
        tank.structure.volWidth = 4;
        tank.clientHasStructure = true;
        tank.isRendering = true;
        assertBox(tank.getRenderBoundingBox(), 10, 64, -20, 15, 70, -16);

        tank.isRendering = false;
        assertBox(tank.getRenderBoundingBox(), 10, 64, -20, 11, 65, -19);
    }

    @Test
    void spsBoundsIncludeCoreAndBoltExpansion() {
        TileEntitySPSCasing sps = new TileEntitySPSCasing();
        sps.setPos(POS);
        sps.structure = new SynchronizedSPSData();
        sps.structure.renderLocation = new Coord4D(10, 65, -20, 0);
        sps.structure.volLength = 7;
        sps.structure.volHeight = 7;
        sps.structure.volWidth = 7;
        sps.clientHasStructure = true;
        sps.isRendering = true;
        assertBox(sps.getRenderBoundingBox(), 9, 63, -21, 18, 72, -12);
    }

    private static void assertBox(AxisAlignedBB box, double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ) {
        assertEquals(minX, box.minX);
        assertEquals(minY, box.minY);
        assertEquals(minZ, box.minZ);
        assertEquals(maxX, box.maxX);
        assertEquals(maxY, box.maxY);
        assertEquals(maxZ, box.maxZ);
        assertTrue(Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ));
        assertTrue(Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ));
    }
}
