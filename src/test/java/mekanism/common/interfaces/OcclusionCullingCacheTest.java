package mekanism.common.interfaces;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcclusionCullingCacheTest {

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
        IOcclusionCulling.GPU_QUERY_LAST_CLEANUP_TICK[0] = 10;

        IOcclusionCulling.cullingClearClientCaches();

        assertTrue(IOcclusionCulling.OCCLUSION_CACHE.isEmpty());
        assertTrue(IOcclusionCulling.GPU_QUERY_CACHE.isEmpty());
        assertEquals(Long.MIN_VALUE, IOcclusionCulling.GPU_QUERY_LAST_CLEANUP_TICK[0]);
    }
}
