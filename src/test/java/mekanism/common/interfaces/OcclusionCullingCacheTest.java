package mekanism.common.interfaces;

import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcclusionCullingCacheTest {

    @AfterEach
    void clearCaches() {
        IOcclusionCulling.cullingClearClientCaches();
    }

    @Test
    void samplesInsideTheSameBlockKeepIndependentVisibility() {
        IOcclusionCulling culling = new IOcclusionCulling() {
            @Override
            public boolean cullingCanSeePoint(Vec3d eyePos, Vec3d target) {
                return target.x > 0.5D;
            }
        };
        Map<Vec3d, Boolean> cache = new HashMap<>();
        Vec3d eye = new Vec3d(-1, 0.5D, 0.5D);

        assertFalse(culling.cullingCanSeePointCached(eye, new Vec3d(0.25D, 0.5D, 0.5D), cache));
        assertTrue(culling.cullingCanSeePointCached(eye, new Vec3d(0.75D, 0.5D, 0.5D), cache));
    }

    @Test
    void sampleCacheExpiresAndTreatsTimeRollbackAsStale() {
        assertTrue(IOcclusionCulling.cullingIsSampleCacheFresh(10, 10));
        assertTrue(IOcclusionCulling.cullingIsSampleCacheFresh(10, 29));
        assertFalse(IOcclusionCulling.cullingIsSampleCacheFresh(10, 30));
        assertFalse(IOcclusionCulling.cullingIsSampleCacheFresh(10, 9));
    }

    @Test
    void clientWorldCleanupReleasesCpuCacheOwners() {
        IOcclusionCulling culling = new IOcclusionCulling() {
        };
        IOcclusionCulling.OCCLUSION_CACHE.put(culling,
              new IOcclusionCulling.CacheEntry(null, 10, Collections.singletonList(Vec3d.ZERO)));
        IOcclusionCulling.OCCLUSION_RESULT_CACHE.put(culling,
              new IOcclusionCulling.OcclusionResultCacheEntry(null, 10, Vec3d.ZERO, Vec3d.ZERO, true));

        IOcclusionCulling.cullingClearClientCaches();

        assertTrue(IOcclusionCulling.OCCLUSION_CACHE.isEmpty());
        assertTrue(IOcclusionCulling.OCCLUSION_RESULT_CACHE.isEmpty());
    }

    @Test
    void finalResultCacheFailsOpenWhenCameraMovesWithinTheSameTick() {
        IOcclusionCulling culling = new IOcclusionCulling() {
        };
        Vec3d eye = new Vec3d(1, 2, 3);
        Vec3d look = new Vec3d(0, 0, 1);

        assertNull(culling.cullingGetCachedResult(null, 20, eye, look));
        assertTrue(culling.cullingCacheResult(null, 20, eye, look, true));
        assertTrue(culling.cullingGetCachedResult(null, 20, eye, look));
        assertNull(culling.cullingGetCachedResult(null, 21, eye, look));
        assertFalse(culling.cullingGetCachedResult(null, 20, eye.add(0.01, 0, 0), look));
        assertFalse(culling.cullingGetCachedResult(null, 20, eye, look.add(0.01, 0, 0)));
    }

    @Test
    void visibleResultCanBeReusedForAStableCameraButCulledResultCannot() {
        IOcclusionCulling culling = new IOcclusionCulling() {
        };
        Vec3d eye = new Vec3d(1, 2, 3);
        Vec3d look = new Vec3d(0, 0, 1);

        assertFalse(culling.cullingCacheResult(null, 20, eye, look, false));
        assertFalse(culling.cullingGetCachedResult(null, 21, eye.add(0.05, 0, 0), look));
        assertNull(culling.cullingGetCachedResult(null, 22, eye, look));

        assertTrue(culling.cullingCacheResult(null, 20, eye, look, true));
        assertNull(culling.cullingGetCachedResult(null, 21, eye.add(0.05, 0, 0), look));
    }

    @Test
    void renderBoundsChangesInvalidateResultCache() {
        class BoundsChangingCulling implements IOcclusionCulling {
            private AxisAlignedBB bounds = new AxisAlignedBB(0, 0, 0, 1, 1, 1);

            @Override
            public AxisAlignedBB cullingGetRenderBounds() {
                return bounds;
            }

            void changeBounds() {
                bounds = new AxisAlignedBB(0, 0, 0, 2, 1, 1);
            }
        }
        BoundsChangingCulling culling = new BoundsChangingCulling();
        Vec3d eye = new Vec3d(1, 2, 3);
        Vec3d look = new Vec3d(0, 0, 1);
        culling.cullingCacheResult(null, 20, eye, look, false);

        culling.changeBounds();
        assertNull(culling.cullingGetCachedResult(null, 20, eye, look));
        assertFalse(IOcclusionCulling.cullingAreSameBounds(new AxisAlignedBB(0, 0, 0, 1, 1, 1), culling.cullingGetRenderBounds()));
    }

    @Test
    void finiteRenderBoundsAreIncludedInOcclusionSamples() {
        IOcclusionCulling culling = new IOcclusionCulling() {
        };
        List<Vec3d> merged = culling.cullingMergeRenderBoundsSamplePoints(
              Collections.singletonList(Vec3d.ZERO), new AxisAlignedBB(10, 20, 30, 11, 21, 31));
        assertTrue(merged.contains(Vec3d.ZERO));
        assertTrue(merged.contains(new Vec3d(10.5D, 20.5D, 30.5D)));
        assertTrue(merged.size() >= 2);
    }

    @Test
    void cappedStructureProbesRetainEverySelectedBlockCenter() {
        IOcclusionCulling culling = new IOcclusionCulling() {
        };
        Set<BlockPos> blocks = new HashSet<>();
        for (int index = 0; index < IOcclusionCulling.BOUNDING_SAMPLED_POINT_LIMIT; index++) {
            blocks.add(new BlockPos(index, index % 3, -index));
        }

        List<Vec3d> probes = culling.cullingToProbePoints(blocks);
        assertTrue(probes.size() <= IOcclusionCulling.BOUNDING_PROBE_POINT_LIMIT);
        for (BlockPos block : blocks) {
            assertTrue(probes.contains(new Vec3d(block).add(0.5D, 0.5D, 0.5D)));
        }
    }

    @Test
    void removingTileReleasesAllOfItsCacheEntries() {
        IOcclusionCulling culling = new IOcclusionCulling() {
        };
        IOcclusionCulling.OCCLUSION_CACHE.put(culling,
              new IOcclusionCulling.CacheEntry(null, 10, Collections.singletonList(Vec3d.ZERO)));
        IOcclusionCulling.OCCLUSION_RESULT_CACHE.put(culling,
              new IOcclusionCulling.OcclusionResultCacheEntry(null, 10, Vec3d.ZERO, Vec3d.ZERO, false));
        IOcclusionCulling.cullingRemoveClientCacheEntry(culling);

        assertFalse(IOcclusionCulling.OCCLUSION_CACHE.containsKey(culling));
        assertFalse(IOcclusionCulling.OCCLUSION_RESULT_CACHE.containsKey(culling));
    }

    @Test
    void distancePrefilterOnlyAppliesToFiniteValidRanges() {
        assertTrue(IOcclusionCulling.cullingIsBeyondRenderDistance(101, 100));
        assertFalse(IOcclusionCulling.cullingIsBeyondRenderDistance(100, 100));
        assertFalse(IOcclusionCulling.cullingIsBeyondRenderDistance(101, Double.POSITIVE_INFINITY));
        assertFalse(IOcclusionCulling.cullingIsBeyondRenderDistance(Double.NaN, 100));
        assertFalse(IOcclusionCulling.cullingIsBeyondRenderDistance(101, -1));
    }
}
