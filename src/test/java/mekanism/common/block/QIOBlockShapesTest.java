package mekanism.common.block;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QIOBlockShapesTest {

    private static final double EPSILON = 1.0E-9;

    @Test
    void dashboardShapeTracksEverySelectedFace() {
        assertBox(QIOBlockShapes.DASHBOARD[EnumFacing.UP.ordinal()][0], 1, 0, 1, 15, 1, 15);
        assertBox(QIOBlockShapes.DASHBOARD[EnumFacing.DOWN.ordinal()][0], 1, 15, 1, 15, 16, 15);
        assertBox(QIOBlockShapes.DASHBOARD[EnumFacing.NORTH.ordinal()][0], 1, 1, 15, 15, 15, 16);
        assertBox(QIOBlockShapes.DASHBOARD[EnumFacing.SOUTH.ordinal()][0], 1, 1, 0, 15, 15, 1);
        assertBox(QIOBlockShapes.DASHBOARD[EnumFacing.WEST.ordinal()][0], 15, 1, 1, 16, 15, 15);
        assertBox(QIOBlockShapes.DASHBOARD[EnumFacing.EAST.ordinal()][0], 0, 1, 1, 1, 15, 15);
    }

    @Test
    void qioBusBaseTracksEverySelectedFace() {
        assertBox(QIOBlockShapes.IMPORTER[EnumFacing.UP.ordinal()][0], 4, 0, 4, 12, 1, 12);
        assertBox(QIOBlockShapes.IMPORTER[EnumFacing.DOWN.ordinal()][0], 4, 15, 4, 12, 16, 12);
        assertBox(QIOBlockShapes.IMPORTER[EnumFacing.NORTH.ordinal()][0], 4, 4, 15, 12, 12, 16);
        assertBox(QIOBlockShapes.IMPORTER[EnumFacing.SOUTH.ordinal()][0], 4, 4, 0, 12, 12, 1);
        assertBox(QIOBlockShapes.IMPORTER[EnumFacing.WEST.ordinal()][0], 15, 4, 4, 16, 12, 12);
        assertBox(QIOBlockShapes.IMPORTER[EnumFacing.EAST.ordinal()][0], 0, 4, 4, 1, 12, 12);
    }

    @Test
    void allUpstreamShapePartsArePreserved() {
        for (EnumFacing facing : EnumFacing.VALUES) {
            assertEquals(1, QIOBlockShapes.DASHBOARD[facing.ordinal()].length);
            assertEquals(19, QIOBlockShapes.IMPORTER[facing.ordinal()].length);
            assertEquals(22, QIOBlockShapes.EXPORTER[facing.ordinal()].length);
            assertEquals(13, QIOBlockShapes.REDSTONE_ADAPTER[facing.ordinal()].length);
        }
    }

    private static void assertBox(AxisAlignedBB box, double minX, double minY, double minZ,
          double maxX, double maxY, double maxZ) {
        assertEquals(minX / 16, box.minX, EPSILON);
        assertEquals(minY / 16, box.minY, EPSILON);
        assertEquals(minZ / 16, box.minZ, EPSILON);
        assertEquals(maxX / 16, box.maxX, EPSILON);
        assertEquals(maxY / 16, box.maxY, EPSILON);
        assertEquals(maxZ / 16, box.maxZ, EPSILON);
    }
}
