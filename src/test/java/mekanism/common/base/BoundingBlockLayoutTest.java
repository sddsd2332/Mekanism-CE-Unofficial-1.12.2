package mekanism.common.base;

import mekanism.common.TestBootstrap;
import mekanism.common.base.IBoundingBlock.BoundingBlockData;
import mekanism.common.config.GeneratorsConfig;
import mekanism.common.config.MekanismConfig;
import mekanism.common.config.MultiblockMachineConfig;
import mekanism.common.tile.TileEntityModificationStation;
import mekanism.common.tile.TileEntitySecurityDesk;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import mekanism.common.tile.machine.TileEntityIsotopicCentrifuge;
import mekanism.common.tile.machine.TileEntitySeismicVibrator;
import mekanism.common.tile.machine.TileEntitySolarNeutronActivator;
import mekanism.generators.common.tile.TileEntityAdvancedSolarGenerator;
import mekanism.generators.common.tile.TileEntityWindGenerator;
import mekanism.multiblockmachine.common.tile.generator.TileEntityLargeGasGenerator;
import mekanism.multiblockmachine.common.tile.generator.TileEntityLargeWindGenerator;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeChemicalInfuser;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeChemicalWasher;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeElectrolyticSeparator;
import mekanism.multiblockmachine.common.tile.machine.TileEntityLargeSolarNeutronActivator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundingBlockLayoutTest {

    private static final BlockPos ORIGIN = new BlockPos(10, 64, -4);

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
        if (MekanismConfig.current().generators == null) {
            MekanismConfig.current().generators = new GeneratorsConfig();
        }
        if (MekanismConfig.current().multiblock == null) {
            MekanismConfig.current().multiblock = new MultiblockMachineConfig();
        }
    }

    @Test
    void upstreamMachineLayoutsMatchModernPlacementShapes() {
        assertLayout(atOrigin(new TileEntitySecurityDesk()), 1, 0);

        TileEntityModificationStation station = atOrigin(new TileEntityModificationStation());
        station.facing = EnumFacing.NORTH;
        assertLayout(station, 3, 0);
        Set<BlockPos> stationPositions = positions(station.getBoundingBlocks());
        EnumFacing right = mekanism.common.util.MekanismUtils.getRight(station.facing);
        assertTrue(stationPositions.contains(ORIGIN.up()));
        assertTrue(stationPositions.contains(ORIGIN.offset(right)));
        assertTrue(stationPositions.contains(ORIGIN.offset(right).up()));

        assertLayout(atOrigin(new TileEntityDigitalMiner()), 17, 17);
        assertLayout(atOrigin(new TileEntitySolarNeutronActivator()), 1, 0);
        assertLayout(atOrigin(new TileEntitySeismicVibrator()), 1, 0);
        assertLayout(atOrigin(new TileEntityIsotopicCentrifuge()), 1, 0);
        assertLayout(atOrigin(new TileEntityWindGenerator()), 4, 0);
        assertLayout(atOrigin(new TileEntityAdvancedSolarGenerator()), 10, 0);
    }

    @Test
    void addonMachineLayoutsAreCompleteAndNonOverlapping() {
        assertLayout(atOrigin(new TileEntityLargeSolarNeutronActivator()), 26, 8);
        assertLayout(atOrigin(new TileEntityLargeElectrolyticSeparator()), 17, 8);
        assertLayout(atOrigin(new TileEntityLargeChemicalWasher()), 26, 8);
        assertLayout(atOrigin(new TileEntityLargeChemicalInfuser()), 17, 8);

        TileEntityLargeGasGenerator gasGenerator = atOrigin(new TileEntityLargeGasGenerator());
        gasGenerator.facing = EnumFacing.NORTH;
        assertLayout(gasGenerator, 26, 7);
    }

    @Test
    void largeWindLayoutUsesItsRealShapeForEveryDirection() {
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            TileEntityLargeWindGenerator generator = atOrigin(new TileEntityLargeWindGenerator());
            generator.facing = facing;
            assertLayout(generator, 1_351, 48);
        }
    }

    @Test
    void declaredLayoutsProduceFiniteRenderBounds() {
        assertBox(atOrigin(new TileEntitySecurityDesk()).getRenderBoundingBox(), 10, 64, -4, 11, 66, -3);
        assertBox(atOrigin(new TileEntityDigitalMiner()).getRenderBoundingBox(), 9, 64, -5, 12, 66, -2);
        assertBox(atOrigin(new TileEntityAdvancedSolarGenerator()).getRenderBoundingBox(), 9, 64, -5, 12, 67, -2);

        TileEntityLargeGasGenerator gasGenerator = atOrigin(new TileEntityLargeGasGenerator());
        gasGenerator.facing = EnumFacing.NORTH;
        assertBox(gasGenerator.getRenderBoundingBox(), 9, 64, -5, 12, 67, -2);
    }

    @Test
    void legacyLayoutsRemainFailOpen() {
        IBoundingBlock legacy = new IBoundingBlock() {
            @Override
            public void onPlace() {
            }

            @Override
            public void onBreak() {
            }
        };
        assertNull(legacy.getBoundingBlockRenderBounds(ORIGIN));
    }

    private static <T extends TileEntity> T atOrigin(T tile) {
        tile.setPos(ORIGIN);
        return tile;
    }

    private static void assertLayout(IBoundingBlock block, int expectedSize, long expectedAdvanced) {
        List<BoundingBlockData> layout = block.getBoundingBlocks();
        assertEquals(expectedSize, layout.size());
        assertEquals(expectedSize, positions(layout).size(), "Bounding layout contains duplicate positions");
        assertEquals(expectedAdvanced, layout.stream().filter(BoundingBlockData::isAdvanced).count());
        assertFalse(positions(layout).contains(ORIGIN), "Bounding layout must not include the main block");
    }

    private static Set<BlockPos> positions(List<BoundingBlockData> layout) {
        Set<BlockPos> positions = new HashSet<>();
        for (BoundingBlockData data : layout) {
            positions.add(data.getPosition());
        }
        return positions;
    }

    private static void assertBox(AxisAlignedBB box, double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ) {
        double epsilon = IBoundingBlock.RENDER_BOUNDS_EPSILON + 1.0E-9D;
        assertTrue(box.minX <= minX && box.minX >= minX - epsilon);
        assertTrue(box.minY <= minY && box.minY >= minY - epsilon);
        assertTrue(box.minZ <= minZ && box.minZ >= minZ - epsilon);
        assertTrue(box.maxX >= maxX && box.maxX <= maxX + epsilon);
        assertTrue(box.maxY >= maxY && box.maxY <= maxY + epsilon);
        assertTrue(box.maxZ >= maxZ && box.maxZ <= maxZ + epsilon);
        assertTrue(Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ));
        assertTrue(Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ));
    }
}
