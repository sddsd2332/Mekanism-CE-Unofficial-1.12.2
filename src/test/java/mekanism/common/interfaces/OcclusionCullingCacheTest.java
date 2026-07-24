package mekanism.common.interfaces;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void clientWorldCleanupReleasesCpuAndGpuCacheOwners() {
        IOcclusionCulling culling = new IOcclusionCulling() {
        };
        IOcclusionCulling.OCCLUSION_CACHE.put(culling,
              new IOcclusionCulling.CacheEntry(null, 10, Collections.singletonList(Vec3d.ZERO)));
        IOcclusionCulling.GPU_QUERY_CACHE.put(culling, new IOcclusionCulling.GpuQueryState());
        IOcclusionCulling.OCCLUSION_RESULT_CACHE.put(culling,
              new IOcclusionCulling.OcclusionResultCacheEntry(null, 10, Vec3d.ZERO, Vec3d.ZERO, true));
        IOcclusionCulling.GPU_QUERY_LAST_CLEANUP_TICK[0] = 10;

        IOcclusionCulling.cullingClearClientCaches();

        assertTrue(IOcclusionCulling.OCCLUSION_CACHE.isEmpty());
        assertTrue(IOcclusionCulling.GPU_QUERY_CACHE.isEmpty());
        assertTrue(IOcclusionCulling.OCCLUSION_RESULT_CACHE.isEmpty());
        assertEquals(Long.MIN_VALUE, IOcclusionCulling.GPU_QUERY_LAST_CLEANUP_TICK[0]);
    }

    @Test
    void finalResultCacheRequiresSameTickAndCamera() {
        IOcclusionCulling culling = new IOcclusionCulling() {
        };
        Vec3d eye = new Vec3d(1, 2, 3);
        Vec3d look = new Vec3d(0, 0, 1);

        assertNull(culling.cullingGetCachedResult(null, 20, eye, look));
        assertTrue(culling.cullingCacheResult(null, 20, eye, look, true));
        assertTrue(culling.cullingGetCachedResult(null, 20, eye, look));
        assertNull(culling.cullingGetCachedResult(null, 21, eye, look));
        assertNull(culling.cullingGetCachedResult(null, 20, eye.add(0.01, 0, 0), look));
        assertNull(culling.cullingGetCachedResult(null, 20, eye, look.add(0.01, 0, 0)));
    }

    @Test
    void removingTileReleasesAllOfItsCacheEntries() {
        IOcclusionCulling culling = new IOcclusionCulling() {
        };
        IOcclusionCulling.OCCLUSION_CACHE.put(culling,
              new IOcclusionCulling.CacheEntry(null, 10, Collections.singletonList(Vec3d.ZERO)));
        IOcclusionCulling.OCCLUSION_RESULT_CACHE.put(culling,
              new IOcclusionCulling.OcclusionResultCacheEntry(null, 10, Vec3d.ZERO, Vec3d.ZERO, false));
        IOcclusionCulling.GPU_QUERY_CACHE.put(culling, new IOcclusionCulling.GpuQueryState());

        IOcclusionCulling.cullingRemoveClientCacheEntry(culling);

        assertFalse(IOcclusionCulling.OCCLUSION_CACHE.containsKey(culling));
        assertFalse(IOcclusionCulling.OCCLUSION_RESULT_CACHE.containsKey(culling));
        assertFalse(IOcclusionCulling.GPU_QUERY_CACHE.containsKey(culling));
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
