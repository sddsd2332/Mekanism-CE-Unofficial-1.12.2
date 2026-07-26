package mekanism.client.render;

import mekanism.common.TestBootstrap;
import mekanism.common.config.MekanismConfig;
import mekanism.common.config.MultiblockMachineConfig;
import mekanism.multiblockmachine.common.tile.generator.TileEntityLargeWindGenerator;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LargeWindGeneratorSelectionWireframeStressTest {

    private static final int HALF_DEGREE_SAMPLES = 720;
    private static final int MAX_CACHED_ANIMATION_STATES = 24;
    private static final int MAX_SEGMENTS_PER_STATE = 7_000;
    private static final long MAX_RETAINED_HEAP_BYTES = 64L * 1_048_576L;
    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    private boolean previousSelectionWireframeSetting;

    @BeforeAll
    static void bootstrapMinecraft() {
        TestBootstrap.bootstrapMinecraft();
        if (MekanismConfig.current().multiblock == null) {
            MekanismConfig.current().multiblock = new MultiblockMachineConfig();
        }
    }

    @BeforeEach
    void enableSelectionWireframes() {
        previousSelectionWireframeSetting = MekanismConfig.current().client.enableSelectionWireframeRendering.val();
        MekanismConfig.current().client.enableSelectionWireframeRendering.set(true);
    }

    @AfterEach
    void clearSelectionCaches() throws ReflectiveOperationException {
        try {
            clearStaticMap(SpecialSelectionWireframeRegistry.class, "PROVIDERS");
            clearStaticMap(SelectionWireframeRenderer.class, "PREPARED_WIREFRAME_CACHE");
        } finally {
            MekanismConfig.current().client.enableSelectionWireframeRendering.set(previousSelectionWireframeSetting);
        }
    }

    @Test
    void fullRotationStaysWithinAnimationCacheBudget() {
        TileEntityLargeWindGenerator generator = new TileEntityLargeWindGenerator();
        generator.setPos(ORIGIN);
        generator.facing = EnumFacing.NORTH;
        IBlockState state = Blocks.STONE.getDefaultState();
        IBlockAccess world = new SingleTileBlockAccess(generator, state);

        Map<Integer, JsonModelSelectionBoxCache.OutlineBox[]> outlinesByState = new HashMap<>();
        Map<Integer, SelectionWireframeRenderer.PreparedWireframe> preparedByState = new HashMap<>();

        forceGc();
        long heapBefore = usedHeap();
        long coldStart = System.nanoTime();
        int outlineCount = -1;
        int minSegments = Integer.MAX_VALUE;
        int maxSegments = 0;
        AxisAlignedBB modelBounds = null;
        for (int sample = 0; sample < HALF_DEGREE_SAMPLES; sample++) {
            generator.setAngle(sample / 2.0D);
            int key = generator.getSelectionWireframeAnimationCacheKey(state, world, ORIGIN);
            assertTrue(key >= 0 && key < MAX_CACHED_ANIMATION_STATES);

            JsonModelSelectionBoxCache.OutlineBox[] outlines = SpecialSelectionWireframeRegistry.getWireframes(state, world, ORIGIN);
            SelectionWireframeRenderer.PreparedWireframe prepared = SelectionWireframeRenderer.getOrPrepareWireframe(outlines);
            assertTrue(outlines.length > 0);
            assertTrue(prepared.segmentCount() > 0);
            if (outlineCount == -1) {
                outlineCount = outlines.length;
            } else {
                assertEquals(outlineCount, outlines.length);
            }
            minSegments = Math.min(minSegments, prepared.segmentCount());
            maxSegments = Math.max(maxSegments, prepared.segmentCount());
            JsonModelSelectionBoxCache.OutlineBox[] existingOutlines = outlinesByState.putIfAbsent(key, outlines);
            SelectionWireframeRenderer.PreparedWireframe existingPrepared = preparedByState.putIfAbsent(key, prepared);
            if (existingOutlines != null) {
                assertSame(existingOutlines, outlines);
                assertSame(existingPrepared, prepared);
            } else {
                modelBounds = includeWireframeBounds(modelBounds, outlines);
            }
        }
        assertRenderBoundsContainModel(generator.getRenderBoundingBox(), modelBounds);
        assertEquals(MAX_CACHED_ANIMATION_STATES, outlinesByState.size());
        assertEquals(MAX_CACHED_ANIMATION_STATES, preparedByState.size());
        long coldNanos = System.nanoTime() - coldStart;

        long warmStart = System.nanoTime();
        for (int sample = 0; sample < HALF_DEGREE_SAMPLES; sample++) {
            generator.setAngle(sample / 2.0D);
            int key = generator.getSelectionWireframeAnimationCacheKey(state, world, ORIGIN);
            JsonModelSelectionBoxCache.OutlineBox[] outlines = SpecialSelectionWireframeRegistry.getWireframes(state, world, ORIGIN);
            assertSame(outlinesByState.get(key), outlines);
            assertSame(preparedByState.get(key), SelectionWireframeRenderer.getOrPrepareWireframe(outlines));
        }
        long warmNanos = System.nanoTime() - warmStart;
        forceGc();
        long retainedHeap = Math.max(0L, usedHeap() - heapBefore);
        assertTrue(maxSegments <= MAX_SEGMENTS_PER_STATE,
              "Prepared line count exceeded the large wind selection budget: " + maxSegments);
        assertTrue(retainedHeap <= MAX_RETAINED_HEAP_BYTES,
              "Retained selection cache exceeded 64 MiB: " + retainedHeap + " bytes");

        System.out.printf(Locale.ROOT,
              "large-wind-selection samples=%d cachedStates=%d outlines/state=%d segments/state=%d..%d cold=%.1fms warm=%.1fms retained=%.1fMiB maxHeap=%.1fMiB%n",
              HALF_DEGREE_SAMPLES, outlinesByState.size(), outlineCount, minSegments, maxSegments,
              coldNanos / 1_000_000.0D, warmNanos / 1_000_000.0D,
              retainedHeap / 1_048_576.0D, Runtime.getRuntime().maxMemory() / 1_048_576.0D);
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static AxisAlignedBB includeWireframeBounds(AxisAlignedBB combinedBounds,
                                                        JsonModelSelectionBoxCache.OutlineBox[] outlines) {
        for (JsonModelSelectionBoxCache.OutlineBox outline : outlines) {
            AxisAlignedBB bounds = outline.getBounds();
            if (bounds == null) {
                continue;
            }
            AxisAlignedBB worldBounds = bounds.offset(ORIGIN);
            combinedBounds = combinedBounds == null ? worldBounds : combinedBounds.union(worldBounds);
        }
        return combinedBounds;
    }

    private static void assertRenderBoundsContainModel(AxisAlignedBB renderBounds, AxisAlignedBB modelBounds) {
        double epsilon = 1.0E-6D;
        assertTrue(modelBounds != null
                        && renderBounds.minX <= modelBounds.minX + epsilon
                        && renderBounds.minY <= modelBounds.minY + epsilon
                        && renderBounds.minZ <= modelBounds.minZ + epsilon
                        && renderBounds.maxX >= modelBounds.maxX - epsilon
                        && renderBounds.maxY >= modelBounds.maxY - epsilon
                        && renderBounds.maxZ >= modelBounds.maxZ - epsilon,
              () -> "Large wind model bounds " + modelBounds + " exceed render bounds " + renderBounds);
    }

    private static void forceGc() {
        System.gc();
        System.runFinalization();
        System.gc();
    }

    @SuppressWarnings("unchecked")
    private static void clearStaticMap(Class<?> owner, String fieldName) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((Map<Object, Object>) field.get(null)).clear();
    }

    private static final class SingleTileBlockAccess implements IBlockAccess {

        private final TileEntity tile;
        private final IBlockState state;

        private SingleTileBlockAccess(TileEntity tile, IBlockState state) {
            this.tile = tile;
            this.state = state;
        }

        @Override
        public TileEntity getTileEntity(BlockPos pos) {
            return ORIGIN.equals(pos) ? tile : null;
        }

        @Override
        public int getCombinedLight(BlockPos pos, int lightValue) {
            return lightValue;
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return state;
        }

        @Override
        public boolean isAirBlock(BlockPos pos) {
            return false;
        }

        @Override
        public Biome getBiome(BlockPos pos) {
            return Biome.getBiome(1);
        }

        @Override
        public int getStrongPower(BlockPos pos, EnumFacing direction) {
            return 0;
        }

        @Override
        public WorldType getWorldType() {
            return WorldType.DEFAULT;
        }

        @Override
        public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean defaultValue) {
            return defaultValue;
        }
    }
}
