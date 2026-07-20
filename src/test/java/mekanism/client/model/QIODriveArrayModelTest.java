package mekanism.client.model;

import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class QIODriveArrayModelTest {

    private static final float EPSILON = 1.0E-6F;

    @Test
    void eastAndWestPositionsMatchBlockstateRotation() {
        assertArrayEquals(new float[]{0.25F, 0.5F, 0.25F},
              QIODriveArrayModel.rotatePosition(0.25F, 0.5F, 0.75F, EnumFacing.EAST), EPSILON);
        assertArrayEquals(new float[]{0.75F, 0.5F, 0.75F},
              QIODriveArrayModel.rotatePosition(0.25F, 0.5F, 0.75F, EnumFacing.WEST), EPSILON);
    }

    @Test
    void eastAndWestNormalsMatchBlockstateRotation() {
        assertArrayEquals(new float[]{-1, 0, 0},
              QIODriveArrayModel.rotateVector(0, 0, 1, EnumFacing.EAST), EPSILON);
        assertArrayEquals(new float[]{1, 0, 0},
              QIODriveArrayModel.rotateVector(0, 0, 1, EnumFacing.WEST), EPSILON);
    }
}
