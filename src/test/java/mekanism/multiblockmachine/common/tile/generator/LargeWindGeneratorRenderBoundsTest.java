package mekanism.multiblockmachine.common.tile.generator;

import mekanism.common.TestBootstrap;
import mekanism.common.config.MekanismConfig;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LargeWindGeneratorRenderBoundsTest {

    private static final BlockPos POS = new BlockPos(10, 64, -20);

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
    }

    @Test
    void renderBoundsFollowHorizontalFacing() {
        assertBox(TileEntityLargeWindGenerator.getRenderBoundingBox(POS, EnumFacing.NORTH),
              -11, 63, -25, 32, 132, -14);
        assertBox(TileEntityLargeWindGenerator.getRenderBoundingBox(POS, EnumFacing.SOUTH),
              -11, 63, -25, 32, 132, -14);
        assertBox(TileEntityLargeWindGenerator.getRenderBoundingBox(POS, EnumFacing.WEST),
              5, 63, -41, 16, 132, 2);
        assertBox(TileEntityLargeWindGenerator.getRenderBoundingBox(POS, EnumFacing.EAST),
              5, 63, -41, 16, 132, 2);
    }

    @Test
    void globalRendererStillUsesConfiguredTileRenderDistance() {
        boolean previousGlobal = MekanismConfig.current().client.largeWindGeneratorisGlobalRenderer.val();
        int previousRange = MekanismConfig.current().client.terRange.val();
        try {
            MekanismConfig.current().client.largeWindGeneratorisGlobalRenderer.set(true);
            MekanismConfig.current().client.terRange.set(96);

            TileEntityLargeWindGenerator generator = new TileEntityLargeWindGenerator();
            assertTrue(Double.isFinite(generator.getMaxRenderDistanceSquared()));
            assertEquals(96D * 96D, generator.getMaxRenderDistanceSquared());
        } finally {
            MekanismConfig.current().client.largeWindGeneratorisGlobalRenderer.set(previousGlobal);
            MekanismConfig.current().client.terRange.set(previousRange);
        }
    }

    private static void assertBox(AxisAlignedBB box, double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ) {
        assertEquals(minX, box.minX);
        assertEquals(minY, box.minY);
        assertEquals(minZ, box.minZ);
        assertEquals(maxX, box.maxX);
        assertEquals(maxY, box.maxY);
        assertEquals(maxZ, box.maxZ);
    }
}
