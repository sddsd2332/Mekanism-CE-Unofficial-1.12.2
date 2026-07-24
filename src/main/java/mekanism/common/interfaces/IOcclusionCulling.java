package mekanism.common.interfaces;

import mekanism.common.Mekanism;
import mekanism.common.base.IBoundingBlock;
import mekanism.common.tile.TileEntityBoundingBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.*;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Client-side occlusion culling hook for tile entities.
 * Return true to skip rendering this tile for the current frame.
 */
public interface IOcclusionCulling {

    @SideOnly(Side.CLIENT)
    int BOUNDING_SAMPLE_CACHE_INTERVAL = 20;
    @SideOnly(Side.CLIENT)
    int BOUNDING_SCAN_LIMIT = 4096;
    @SideOnly(Side.CLIENT)
    int BOUNDING_DIRECT_POINT_LIMIT = 64;
    @SideOnly(Side.CLIENT)
    int BOUNDING_SAMPLED_POINT_LIMIT = 48;
    @SideOnly(Side.CLIENT)
    int BOUNDING_PROBE_POINT_LIMIT = 512;
    @SideOnly(Side.CLIENT)
    double BOUNDING_EDGE_PROBE_MIN = 0.02D;
    @SideOnly(Side.CLIENT)
    double BOUNDING_EDGE_PROBE_MAX = 0.98D;
    @SideOnly(Side.CLIENT)
    double OCCLUSION_VIEW_DOT_THRESHOLD = -0.15D;
    @SideOnly(Side.CLIENT)
    int OCCLUSION_AABB_FACE_MAX_STEPS = 4;
    @SideOnly(Side.CLIENT)
    int OCCLUSION_AABB_MAX_RAY_BUDGET = 96;
    @SideOnly(Side.CLIENT)
    double OCCLUSION_AABB_EXPAND_EPSILON = 0.02D;
    @SideOnly(Side.CLIENT)
    int OCCLUSION_GPU_QUERY_STALE_TICKS = 200;
    @SideOnly(Side.CLIENT)
    int OCCLUSION_GPU_QUERY_CLEANUP_INTERVAL = 20;
    Map<IOcclusionCulling, CacheEntry> OCCLUSION_CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    Map<IOcclusionCulling, OcclusionResultCacheEntry> OCCLUSION_RESULT_CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    // Keep query owners alive until explicit stale cleanup so WeakHashMap expunging cannot orphan
    // an OpenGL query ID before it is deleted.
    Map<IOcclusionCulling, GpuQueryState> GPU_QUERY_CACHE = Collections.synchronizedMap(new IdentityHashMap<>());
    long[] GPU_QUERY_LAST_CLEANUP_TICK = new long[]{Long.MIN_VALUE};
    boolean[] GPU_QUERY_FORCE_DISABLE = new boolean[]{false};
    boolean[] GPU_QUERY_UNSUPPORTED_LOGGED = new boolean[]{false};
    boolean[] GPU_QUERY_FALLBACK_LOGGED = new boolean[]{false};
    long[] OPTIFINE_COMPATIBILITY_LAST_CHECK = new long[]{Long.MIN_VALUE};
    boolean[] OPTIFINE_RENDER_PATH_INCOMPATIBLE = new boolean[]{false};

    @SideOnly(Side.CLIENT)
    default boolean shouldCullForOcclusion() {
        World world = getOcclusionWorld();
        BlockPos pos = getOcclusionPos();
        if (world == null || pos == null) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            return false;
        }
        if (mc.gameSettings.thirdPersonView != 0) {
            return false;
        }
        Entity renderView = mc.getRenderViewEntity();
        if (renderView == null) {
            return false;
        }

        float partialTicks = mc.getRenderPartialTicks();
        Vec3d eyePos = renderView.getPositionEyes(partialTicks);
        Vec3d lookVec = renderView.getLook(partialTicks);
        long worldTime = world.getTotalWorldTime();
        Boolean cachedResult = cullingGetCachedResult(world, worldTime, eyePos, lookVec);
        if (cachedResult != null) {
            return cachedResult;
        }
        List<Vec3d> samplePoints = computeOcclusionSamplePoints();
        if (samplePoints == null || samplePoints.isEmpty()) {
            samplePoints = Collections.singletonList(new Vec3d(pos).add(0.5D, 0.5D, 0.5D));
        }
        Map<Vec3d, Boolean> rayCache = new HashMap<>();

        boolean anyPointInView = false;
        for (Vec3d samplePoint : samplePoints) {
            Vec3d toPoint = samplePoint.subtract(eyePos);
            double lengthSq = toPoint.lengthSquared();
            if (lengthSq <= 1.0E-8D) {
                anyPointInView = true;
                break;
            }
            double facingDot = lookVec.dotProduct(toPoint.scale(1.0D / Math.sqrt(lengthSq)));
            if (facingDot > OCCLUSION_VIEW_DOT_THRESHOLD) {
                anyPointInView = true;
                break;
            }
        }
        if (!anyPointInView) {
            return cullingCacheResult(world, worldTime, eyePos, lookVec, true);
        }
        if (useOpenGlOcclusionCulling()) {
            Boolean gpuCulled = cullingComputeOpenGlResult(samplePoints, worldTime);
            if (Boolean.FALSE.equals(gpuCulled)) {
                // A visible GPU result is sufficient to render. An occluded result is asynchronous
                // and may describe the previous camera position, so confirm it with current CPU rays.
                return cullingCacheResult(world, worldTime, eyePos, lookVec, false);
            }
        }

        for (Vec3d samplePoint : samplePoints) {
            if (cullingCanSeePointCached(eyePos, samplePoint, rayCache)) {
                return cullingCacheResult(world, worldTime, eyePos, lookVec, false);
            }
        }
        // Borrowed from entity-culling AABB-side tracing: if corner probes are all blocked,
        // still allow rendering when any visible face sample can be seen.
        if (cullingIsAabbFaceVisible(eyePos, samplePoints, rayCache)) {
            return cullingCacheResult(world, worldTime, eyePos, lookVec, false);
        }
        return cullingCacheResult(world, worldTime, eyePos, lookVec, true);
    }

    @SideOnly(Side.CLIENT)
    @Nullable
    default Boolean cullingGetCachedResult(World world, long worldTime, Vec3d eyePos, Vec3d lookVec) {
        OcclusionResultCacheEntry entry = OCCLUSION_RESULT_CACHE.get(this);
        if (entry != null && entry.world == world && entry.tick == worldTime &&
              entry.eyePos.equals(eyePos) && entry.lookVec.equals(lookVec)) {
            return entry.culled;
        }
        return null;
    }

    @SideOnly(Side.CLIENT)
    default boolean cullingCacheResult(World world, long worldTime, Vec3d eyePos, Vec3d lookVec, boolean culled) {
        OCCLUSION_RESULT_CACHE.put(this, new OcclusionResultCacheEntry(world, worldTime, eyePos, lookVec, culled));
        return culled;
    }

    static boolean cullingIsBeyondRenderDistance(double distanceSquared, double maxDistanceSquared) {
        return Double.isFinite(distanceSquared) && Double.isFinite(maxDistanceSquared) && maxDistanceSquared >= 0 &&
              distanceSquared > maxDistanceSquared;
    }

    @SideOnly(Side.CLIENT)
    default boolean useOpenGlOcclusionCulling() {
        return false;
    }

    @SideOnly(Side.CLIENT)
    default int getOpenGlOcclusionQueryInterval() {
        return 2;
    }

    @SideOnly(Side.CLIENT)
    default List<Vec3d> computeOcclusionSamplePoints() {
        World world = getOcclusionWorld();
        BlockPos pos = getOcclusionPos();
        if (pos == null) {
            return Collections.emptyList();
        }
        if (world == null) {
            return cullingGetSingleBlockOcclusionSamplePoints(pos);
        }
        long worldTime = world.getTotalWorldTime();
        CacheEntry entry = OCCLUSION_CACHE.get(this);
        if (entry != null && entry.world == world && cullingIsSampleCacheFresh(entry.tick, worldTime) && !entry.points.isEmpty()) {
            return entry.points;
        }

        List<Vec3d> rebuiltPoints = this instanceof IBoundingBlock ? cullingBuildBoundingOcclusionSamplePoints(world, pos) : cullingGetSingleBlockOcclusionSamplePoints(pos);
        OCCLUSION_CACHE.put(this, new CacheEntry(world, worldTime, rebuiltPoints));
        return rebuiltPoints;
    }

    static boolean cullingIsSampleCacheFresh(long cachedTick, long currentTick) {
        long age = currentTick - cachedTick;
        return age >= 0 && age < BOUNDING_SAMPLE_CACHE_INTERVAL;
    }

    @SideOnly(Side.CLIENT)
    default boolean isOcclusionTargetHitPos(BlockPos hitPos) {
        BlockPos pos = getOcclusionPos();
        if (pos != null && pos.equals(hitPos)) {
            return true;
        }
        World world = getOcclusionWorld();
        if (world == null || !(this instanceof IBoundingBlock)) {
            return false;
        }
        return cullingIsLinkedBoundingBlock(world, pos, hitPos);
    }

    @SideOnly(Side.CLIENT)
    default World getOcclusionWorld() {
        if (this instanceof TileEntity tile) {
            return tile.getWorld();
        }
        return null;
    }

    @SideOnly(Side.CLIENT)
    @Nullable
    default BlockPos getOcclusionPos() {
        if (this instanceof TileEntity tile) {
            return tile.getPos();
        }
        return null;
    }

    @SideOnly(Side.CLIENT)
    default List<Vec3d> cullingGetSingleBlockOcclusionSamplePoints(BlockPos pos) {
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        double xMin = x + BOUNDING_EDGE_PROBE_MIN;
        double yMin = y + BOUNDING_EDGE_PROBE_MIN;
        double zMin = z + BOUNDING_EDGE_PROBE_MIN;
        double xMax = x + BOUNDING_EDGE_PROBE_MAX;
        double yMax = y + BOUNDING_EDGE_PROBE_MAX;
        double zMax = z + BOUNDING_EDGE_PROBE_MAX;

        List<Vec3d> points = new ArrayList<>(9);
        points.add(new Vec3d(x + 0.5D, y + 0.5D, z + 0.5D));
        points.add(new Vec3d(xMin, yMin, zMin));
        points.add(new Vec3d(xMin, yMin, zMax));
        points.add(new Vec3d(xMin, yMax, zMin));
        points.add(new Vec3d(xMin, yMax, zMax));
        points.add(new Vec3d(xMax, yMin, zMin));
        points.add(new Vec3d(xMax, yMin, zMax));
        points.add(new Vec3d(xMax, yMax, zMin));
        points.add(new Vec3d(xMax, yMax, zMax));
        return points;
    }

    @SideOnly(Side.CLIENT)
    default List<Vec3d> cullingBuildBoundingOcclusionSamplePoints(World world, BlockPos pos) {
        Set<BlockPos> structureBlocks = cullingCollectBoundingStructureBlocks(world, pos);
        if (structureBlocks.size() <= 1) {
            return Collections.singletonList(new Vec3d(pos).add(0.5D, 0.5D, 0.5D));
        }

        Set<BlockPos> selected = new HashSet<>();
        selected.add(pos);

        if (structureBlocks.size() <= BOUNDING_DIRECT_POINT_LIMIT) {
            selected.addAll(structureBlocks);
            return cullingToProbePoints(selected);
        }

        Set<BlockPos> boundaryBlocks = cullingCollectBoundaryBlocks(structureBlocks);
        if (boundaryBlocks.isEmpty()) {
            boundaryBlocks = structureBlocks;
        }

        cullingAddExtremes(structureBlocks, selected);
        List<BlockPos> sortedBoundary = new ArrayList<>(boundaryBlocks);
        sortedBoundary.sort(Comparator.comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        int stride = Math.max(1, sortedBoundary.size() / BOUNDING_SAMPLED_POINT_LIMIT);
        for (int i = 0; i < sortedBoundary.size() && selected.size() < BOUNDING_SAMPLED_POINT_LIMIT; i += stride) {
            selected.add(sortedBoundary.get(i));
        }
        if (selected.size() < BOUNDING_SAMPLED_POINT_LIMIT) {
            for (BlockPos boundary : sortedBoundary) {
                if (selected.size() >= BOUNDING_SAMPLED_POINT_LIMIT) {
                    break;
                }
                selected.add(boundary);
            }
        }
        return cullingToProbePoints(selected);
    }

    @SideOnly(Side.CLIENT)
    default Set<BlockPos> cullingCollectBoundingStructureBlocks(World world, BlockPos pos) {
        Set<BlockPos> structureBlocks = new HashSet<>();
        structureBlocks.add(pos);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        for (EnumFacing side : EnumFacing.VALUES) {
            queue.add(pos.offset(side));
        }

        while (!queue.isEmpty() && structureBlocks.size() < BOUNDING_SCAN_LIMIT) {
            BlockPos current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            if (!cullingIsLinkedBoundingBlock(world, pos, current)) {
                continue;
            }
            structureBlocks.add(current);
            for (EnumFacing side : EnumFacing.VALUES) {
                BlockPos next = current.offset(side);
                if (!visited.contains(next)) {
                    queue.add(next);
                }
            }
        }
        return structureBlocks;
    }

    @SideOnly(Side.CLIENT)
    default Set<BlockPos> cullingCollectBoundaryBlocks(Set<BlockPos> structureBlocks) {
        Set<BlockPos> boundaryBlocks = new HashSet<>();
        for (BlockPos structurePos : structureBlocks) {
            for (EnumFacing side : EnumFacing.VALUES) {
                if (!structureBlocks.contains(structurePos.offset(side))) {
                    boundaryBlocks.add(structurePos);
                    break;
                }
            }
        }
        return boundaryBlocks;
    }

    @SideOnly(Side.CLIENT)
    default void cullingAddExtremes(Set<BlockPos> structureBlocks, Set<BlockPos> selected) {
        BlockPos minX = null;
        BlockPos maxX = null;
        BlockPos minY = null;
        BlockPos maxY = null;
        BlockPos minZ = null;
        BlockPos maxZ = null;
        for (BlockPos structurePos : structureBlocks) {
            if (minX == null || structurePos.getX() < minX.getX()) {
                minX = structurePos;
            }
            if (maxX == null || structurePos.getX() > maxX.getX()) {
                maxX = structurePos;
            }
            if (minY == null || structurePos.getY() < minY.getY()) {
                minY = structurePos;
            }
            if (maxY == null || structurePos.getY() > maxY.getY()) {
                maxY = structurePos;
            }
            if (minZ == null || structurePos.getZ() < minZ.getZ()) {
                minZ = structurePos;
            }
            if (maxZ == null || structurePos.getZ() > maxZ.getZ()) {
                maxZ = structurePos;
            }
        }
        if (minX != null) {
            selected.add(minX);
        }
        if (maxX != null) {
            selected.add(maxX);
        }
        if (minY != null) {
            selected.add(minY);
        }
        if (maxY != null) {
            selected.add(maxY);
        }
        if (minZ != null) {
            selected.add(minZ);
        }
        if (maxZ != null) {
            selected.add(maxZ);
        }
    }

    @SideOnly(Side.CLIENT)
    default List<Vec3d> cullingToProbePoints(Set<BlockPos> points) {
        List<BlockPos> orderedPoints = new ArrayList<>(points);
        orderedPoints.sort(Comparator.comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        List<Vec3d> centers = new ArrayList<>(Math.min(BOUNDING_PROBE_POINT_LIMIT, orderedPoints.size() * 9));
        for (BlockPos point : orderedPoints) {
            if (centers.size() >= BOUNDING_PROBE_POINT_LIMIT) {
                break;
            }
            double x = point.getX();
            double y = point.getY();
            double z = point.getZ();
            centers.add(new Vec3d(x + 0.5D, y + 0.5D, z + 0.5D));
            if (centers.size() >= BOUNDING_PROBE_POINT_LIMIT) {
                continue;
            }
            double xMin = x + BOUNDING_EDGE_PROBE_MIN;
            double xMax = x + BOUNDING_EDGE_PROBE_MAX;
            double yMin = y + BOUNDING_EDGE_PROBE_MIN;
            double yMax = y + BOUNDING_EDGE_PROBE_MAX;
            double zMin = z + BOUNDING_EDGE_PROBE_MIN;
            double zMax = z + BOUNDING_EDGE_PROBE_MAX;

            centers.add(new Vec3d(xMin, yMin, zMin));
            if (centers.size() >= BOUNDING_PROBE_POINT_LIMIT) {
                continue;
            }
            centers.add(new Vec3d(xMin, yMin, zMax));
            if (centers.size() >= BOUNDING_PROBE_POINT_LIMIT) {
                continue;
            }
            centers.add(new Vec3d(xMin, yMax, zMin));
            if (centers.size() >= BOUNDING_PROBE_POINT_LIMIT) {
                continue;
            }
            centers.add(new Vec3d(xMin, yMax, zMax));
            if (centers.size() >= BOUNDING_PROBE_POINT_LIMIT) {
                continue;
            }
            centers.add(new Vec3d(xMax, yMin, zMin));
            if (centers.size() >= BOUNDING_PROBE_POINT_LIMIT) {
                continue;
            }
            centers.add(new Vec3d(xMax, yMin, zMax));
            if (centers.size() >= BOUNDING_PROBE_POINT_LIMIT) {
                continue;
            }
            centers.add(new Vec3d(xMax, yMax, zMin));
            if (centers.size() >= BOUNDING_PROBE_POINT_LIMIT) {
                continue;
            }
            centers.add(new Vec3d(xMax, yMax, zMax));
        }
        return centers;
    }

    @SideOnly(Side.CLIENT)
    default boolean cullingIsLinkedBoundingBlock(World world, @Nullable BlockPos mainPos, BlockPos candidatePos) {
        if (mainPos == null || !world.isBlockLoaded(candidatePos, false)) {
            return false;
        }
        TileEntity tileEntity = world.getTileEntity(candidatePos);
        return tileEntity instanceof TileEntityBoundingBlock bounding && mainPos.equals(bounding.getMainPos());
    }

    @SideOnly(Side.CLIENT)
    default boolean cullingCanSeePoint(Vec3d eyePos, Vec3d target) {
        World world = getOcclusionWorld();
        if (world == null) {
            return true;
        }
        Vec3d start = eyePos;
        Vec3d direction = target.subtract(eyePos);
        double distanceSq = direction.lengthSquared();
        if (distanceSq < 1.0E-8D) {
            return true;
        }
        Vec3d directionNorm = direction.scale(1.0D / Math.sqrt(distanceSq));
        for (int i = 0; i < 16; i++) {
            RayTraceResult trace = world.rayTraceBlocks(start, target, false, true, false);
            if (trace == null || trace.typeOfHit != RayTraceResult.Type.BLOCK) {
                return true;
            }
            BlockPos hitPos = trace.getBlockPos();
            if (isOcclusionTargetHitPos(hitPos)) {
                return true;
            }
            IBlockState hitState = world.getBlockState(hitPos);
            if (hitState == null || !cullingIsTransparentOccluder(hitState)) {
                return false;
            }
            if (trace.hitVec == null) {
                return false;
            }
            start = trace.hitVec.add(directionNorm.scale(0.01D));
            if (start.squareDistanceTo(target) < 1.0E-6D) {
                return true;
            }
        }
        return true;
    }

    @SideOnly(Side.CLIENT)
    default boolean cullingCanSeePointCached(Vec3d eyePos, Vec3d target, Map<Vec3d, Boolean> rayCache) {
        Boolean cached = rayCache.get(target);
        if (cached != null) {
            return cached;
        }
        boolean visible = cullingCanSeePoint(eyePos, target);
        rayCache.put(target, visible);
        return visible;
    }

    @SideOnly(Side.CLIENT)
    default boolean cullingIsAabbFaceVisible(Vec3d eyePos, List<Vec3d> samplePoints, Map<Vec3d, Boolean> rayCache) {
        if (samplePoints.isEmpty()) {
            return false;
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3d point : samplePoints) {
            if (point.x < minX) {
                minX = point.x;
            }
            if (point.y < minY) {
                minY = point.y;
            }
            if (point.z < minZ) {
                minZ = point.z;
            }
            if (point.x > maxX) {
                maxX = point.x;
            }
            if (point.y > maxY) {
                maxY = point.y;
            }
            if (point.z > maxZ) {
                maxZ = point.z;
            }
        }
        minX -= OCCLUSION_AABB_EXPAND_EPSILON;
        minY -= OCCLUSION_AABB_EXPAND_EPSILON;
        minZ -= OCCLUSION_AABB_EXPAND_EPSILON;
        maxX += OCCLUSION_AABB_EXPAND_EPSILON;
        maxY += OCCLUSION_AABB_EXPAND_EPSILON;
        maxZ += OCCLUSION_AABB_EXPAND_EPSILON;

        if (eyePos.x >= minX && eyePos.x <= maxX
                && eyePos.y >= minY && eyePos.y <= maxY
                && eyePos.z >= minZ && eyePos.z <= maxZ) {
            return true;
        }

        int stepsX = Math.max(1, Math.min(OCCLUSION_AABB_FACE_MAX_STEPS, (int) Math.ceil(maxX - minX)));
        int stepsY = Math.max(1, Math.min(OCCLUSION_AABB_FACE_MAX_STEPS, (int) Math.ceil(maxY - minY)));
        int stepsZ = Math.max(1, Math.min(OCCLUSION_AABB_FACE_MAX_STEPS, (int) Math.ceil(maxZ - minZ)));
        int[] budget = new int[]{OCCLUSION_AABB_MAX_RAY_BUDGET};

        if (eyePos.x < minX && cullingCheckFaceX(eyePos, minX, minY, maxY, minZ, maxZ, stepsY, stepsZ, rayCache, budget)) {
            return true;
        }
        if (eyePos.x > maxX && cullingCheckFaceX(eyePos, maxX, minY, maxY, minZ, maxZ, stepsY, stepsZ, rayCache, budget)) {
            return true;
        }
        if (eyePos.y < minY && cullingCheckFaceY(eyePos, minY, minX, maxX, minZ, maxZ, stepsX, stepsZ, rayCache, budget)) {
            return true;
        }
        if (eyePos.y > maxY && cullingCheckFaceY(eyePos, maxY, minX, maxX, minZ, maxZ, stepsX, stepsZ, rayCache, budget)) {
            return true;
        }
        if (eyePos.z < minZ && cullingCheckFaceZ(eyePos, minZ, minX, maxX, minY, maxY, stepsX, stepsY, rayCache, budget)) {
            return true;
        }
        if (eyePos.z > maxZ && cullingCheckFaceZ(eyePos, maxZ, minX, maxX, minY, maxY, stepsX, stepsY, rayCache, budget)) {
            return true;
        }
        return false;
    }

    @SideOnly(Side.CLIENT)
    default boolean cullingCheckFaceX(Vec3d eyePos, double x, double minY, double maxY, double minZ, double maxZ, int stepsY, int stepsZ,
                                      Map<Vec3d, Boolean> rayCache, int[] budget) {
        for (int iy = 0; iy <= stepsY; iy++) {
            double y = cullingLerp(minY, maxY, iy, stepsY);
            for (int iz = 0; iz <= stepsZ; iz++) {
                if (budget[0]-- <= 0) {
                    return false;
                }
                double z = cullingLerp(minZ, maxZ, iz, stepsZ);
                if (cullingCanSeePointCached(eyePos, new Vec3d(x, y, z), rayCache)) {
                    return true;
                }
            }
        }
        return false;
    }

    @SideOnly(Side.CLIENT)
    default boolean cullingCheckFaceY(Vec3d eyePos, double y, double minX, double maxX, double minZ, double maxZ, int stepsX, int stepsZ,
                                      Map<Vec3d, Boolean> rayCache, int[] budget) {
        for (int ix = 0; ix <= stepsX; ix++) {
            double x = cullingLerp(minX, maxX, ix, stepsX);
            for (int iz = 0; iz <= stepsZ; iz++) {
                if (budget[0]-- <= 0) {
                    return false;
                }
                double z = cullingLerp(minZ, maxZ, iz, stepsZ);
                if (cullingCanSeePointCached(eyePos, new Vec3d(x, y, z), rayCache)) {
                    return true;
                }
            }
        }
        return false;
    }

    @SideOnly(Side.CLIENT)
    default boolean cullingCheckFaceZ(Vec3d eyePos, double z, double minX, double maxX, double minY, double maxY, int stepsX, int stepsY,
                                      Map<Vec3d, Boolean> rayCache, int[] budget) {
        for (int ix = 0; ix <= stepsX; ix++) {
            double x = cullingLerp(minX, maxX, ix, stepsX);
            for (int iy = 0; iy <= stepsY; iy++) {
                if (budget[0]-- <= 0) {
                    return false;
                }
                double y = cullingLerp(minY, maxY, iy, stepsY);
                if (cullingCanSeePointCached(eyePos, new Vec3d(x, y, z), rayCache)) {
                    return true;
                }
            }
        }
        return false;
    }

    @SideOnly(Side.CLIENT)
    default double cullingLerp(double min, double max, int index, int steps) {
        if (steps <= 0) {
            return min;
        }
        return min + (max - min) * ((double) index / (double) steps);
    }

    @SideOnly(Side.CLIENT)
    default boolean cullingIsTransparentOccluder(IBlockState state) {
        if (state.getMaterial().isLiquid()) {
            return true;
        }
        if (!state.isFullCube()) {
            return true;
        }
        if (!state.isOpaqueCube()) {
            return true;
        }
        return state.getBlock().isTranslucent(state);
    }

    @SideOnly(Side.CLIENT)
    default Boolean cullingComputeOpenGlResult(List<Vec3d> samplePoints, long worldTime) {
        if (!cullingIsOpenGlQuerySupported()) {
            if (!GPU_QUERY_UNSUPPORTED_LOGGED[0]) {
                GPU_QUERY_UNSUPPORTED_LOGGED[0] = true;
                Mekanism.logger.warn("GazeCulling OpenGL path unsupported on this client; falling back to CPU occlusion culling.");
            }
            return null;
        }
        if (GPU_QUERY_FORCE_DISABLE[0]) {
            return null;
        }
        cullingCleanupStaleGpuQueries(worldTime);
        GpuQueryState state = GPU_QUERY_CACHE.computeIfAbsent(this, ignored -> new GpuQueryState());
        state.lastTouchedTick = worldTime;

        try {
            if (state.queryPending && state.queryId != -1) {
                if (cullingIsQueryResultAvailable(state)) {
                    state.lastVisible = cullingConsumeQueryResult(state);
                }
            }
            int interval = Math.max(1, getOpenGlOcclusionQueryInterval());
            if (!state.queryPending && (state.lastIssueTick == Long.MIN_VALUE || worldTime - state.lastIssueTick >= interval)) {
                double[] aabb = cullingComputeAabb(samplePoints);
                if (aabb != null) {
                    cullingIssueQuery(state, aabb[0], aabb[1], aabb[2], aabb[3], aabb[4], aabb[5]);
                    state.lastIssueTick = worldTime;
                }
            }
            return !state.lastVisible;
        } catch (Throwable ignored) {
            GPU_QUERY_FORCE_DISABLE[0] = true;
            if (!GPU_QUERY_FALLBACK_LOGGED[0]) {
                GPU_QUERY_FALLBACK_LOGGED[0] = true;
                Mekanism.logger.warn("GazeCulling OpenGL path failed at runtime; switched to CPU occlusion culling fallback.");
            }
            cullingClearGpuQueryCache();
            return null;
        }
    }

    @SideOnly(Side.CLIENT)
    default boolean cullingIsOpenGlQuerySupported() {
        try {
            if (cullingHasIncompatibleOptifineRenderPath()) {
                return false;
            }
            ContextCapabilities caps = GLContext.getCapabilities();
            return caps != null && (caps.OpenGL15 || caps.GL_ARB_occlusion_query);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SideOnly(Side.CLIENT)
    default boolean cullingHasIncompatibleOptifineRenderPath() {
        long now = System.currentTimeMillis();
        if (OPTIFINE_COMPATIBILITY_LAST_CHECK[0] != Long.MIN_VALUE
                && now - OPTIFINE_COMPATIBILITY_LAST_CHECK[0] < 1_000L) {
            return OPTIFINE_RENDER_PATH_INCOMPATIBLE[0];
        }
        OPTIFINE_COMPATIBILITY_LAST_CHECK[0] = now;
        boolean incompatible = false;
        try {
            Class<?> config = Class.forName("Config", false, IOcclusionCulling.class.getClassLoader());
            Object fastRender = config.getMethod("isFastRender").invoke(null);
            incompatible = Boolean.TRUE.equals(fastRender);
            if (!incompatible) {
                Object shaders = config.getMethod("isShaders").invoke(null);
                incompatible = Boolean.TRUE.equals(shaders);
            }
        } catch (ClassNotFoundException ignored) {
            // OptiFine is not installed.
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Unknown OptiFine version. Leave the normal capability checks in control.
        }
        OPTIFINE_RENDER_PATH_INCOMPATIBLE[0] = incompatible;
        return incompatible;
    }

    @SideOnly(Side.CLIENT)
    default void cullingDiscardOpenGlQuery() {
        GpuQueryState state;
        synchronized (GPU_QUERY_CACHE) {
            state = GPU_QUERY_CACHE.remove(this);
        }
        cullingDeleteQuery(state);
    }

    @SideOnly(Side.CLIENT)
    default void cullingCleanupStaleGpuQueries(long worldTime) {
        if (GPU_QUERY_LAST_CLEANUP_TICK[0] != Long.MIN_VALUE && worldTime < GPU_QUERY_LAST_CLEANUP_TICK[0]) {
            // World changes and /time operations can move the clock backwards. Pending results
            // describe the old render state and their issue timestamps would otherwise never expire.
            cullingClearGpuQueryCache();
            GPU_QUERY_LAST_CLEANUP_TICK[0] = Long.MIN_VALUE;
        }
        if (GPU_QUERY_LAST_CLEANUP_TICK[0] != Long.MIN_VALUE
                && worldTime - GPU_QUERY_LAST_CLEANUP_TICK[0] < OCCLUSION_GPU_QUERY_CLEANUP_INTERVAL) {
            return;
        }
        GPU_QUERY_LAST_CLEANUP_TICK[0] = worldTime;
        synchronized (GPU_QUERY_CACHE) {
            Iterator<Map.Entry<IOcclusionCulling, GpuQueryState>> iterator = GPU_QUERY_CACHE.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<IOcclusionCulling, GpuQueryState> entry = iterator.next();
                GpuQueryState state = entry.getValue();
                if (state == null) {
                    iterator.remove();
                    continue;
                }
                if (state.lastTouchedTick != Long.MIN_VALUE && worldTime - state.lastTouchedTick > OCCLUSION_GPU_QUERY_STALE_TICKS) {
                    cullingDeleteQuery(state);
                    iterator.remove();
                }
            }
        }
    }

    @SideOnly(Side.CLIENT)
    default void cullingClearGpuQueryCache() {
        cullingClearAllGpuQueries();
    }

    @SideOnly(Side.CLIENT)
    static void cullingClearClientCaches() {
        cullingClearAllGpuQueries();
        synchronized (OCCLUSION_CACHE) {
            OCCLUSION_CACHE.clear();
        }
        synchronized (OCCLUSION_RESULT_CACHE) {
            OCCLUSION_RESULT_CACHE.clear();
        }
    }

    @SideOnly(Side.CLIENT)
    static void cullingRemoveClientCacheEntry(IOcclusionCulling culling) {
        OCCLUSION_CACHE.remove(culling);
        OCCLUSION_RESULT_CACHE.remove(culling);
        GpuQueryState state;
        synchronized (GPU_QUERY_CACHE) {
            state = GPU_QUERY_CACHE.remove(culling);
        }
        cullingDeleteQueryState(state);
    }

    @SideOnly(Side.CLIENT)
    static void cullingClearAllGpuQueries() {
        synchronized (GPU_QUERY_CACHE) {
            for (GpuQueryState state : GPU_QUERY_CACHE.values()) {
                cullingDeleteQueryState(state);
            }
            GPU_QUERY_CACHE.clear();
        }
        GPU_QUERY_LAST_CLEANUP_TICK[0] = Long.MIN_VALUE;
    }

    @SideOnly(Side.CLIENT)
    default boolean cullingIsQueryResultAvailable(GpuQueryState state) {
        if (state.queryId == -1 || !state.queryPending) {
            return false;
        }
        if (state.useArbApi) {
            return ARBOcclusionQuery.glGetQueryObjectiARB(state.queryId, ARBOcclusionQuery.GL_QUERY_RESULT_AVAILABLE_ARB) != 0;
        }
        return GL15.glGetQueryObjecti(state.queryId, GL15.GL_QUERY_RESULT_AVAILABLE) != 0;
    }

    @SideOnly(Side.CLIENT)
    default boolean cullingConsumeQueryResult(GpuQueryState state) {
        int samples;
        if (state.useArbApi) {
            samples = ARBOcclusionQuery.glGetQueryObjectiARB(state.queryId, ARBOcclusionQuery.GL_QUERY_RESULT_ARB);
        } else {
            samples = GL15.glGetQueryObjecti(state.queryId, GL15.GL_QUERY_RESULT);
        }
        cullingDeleteQuery(state);
        state.queryPending = false;
        return samples > 0;
    }

    @SideOnly(Side.CLIENT)
    default void cullingDeleteQuery(GpuQueryState state) {
        cullingDeleteQueryState(state);
    }

    @SideOnly(Side.CLIENT)
    static void cullingDeleteQueryState(GpuQueryState state) {
        if (state == null || state.queryId == -1) {
            return;
        }
        try {
            if (state.useArbApi) {
                ARBOcclusionQuery.glDeleteQueriesARB(state.queryId);
            } else {
                GL15.glDeleteQueries(state.queryId);
            }
        } catch (Throwable ignored) {
        }
        state.queryId = -1;
        state.queryPending = false;
    }

    @SideOnly(Side.CLIENT)
    default void cullingIssueQuery(GpuQueryState state, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        ContextCapabilities caps = GLContext.getCapabilities();
        QueryApi queryApi = cullingSelectQueryApi(caps);
        if (queryApi == QueryApi.NONE) {
            throw new IllegalStateException("No supported OpenGL occlusion query API found");
        }
        cullingDeleteQuery(state);
        boolean useArb = queryApi == QueryApi.ARB;
        int queryId = useArb ? ARBOcclusionQuery.glGenQueriesARB() : GL15.glGenQueries();
        state.queryId = queryId;
        state.useArbApi = useArb;
        state.queryTarget = queryApi == QueryApi.GL33_ANY_SAMPLES ? GL33.GL_ANY_SAMPLES_PASSED
                : (useArb ? ARBOcclusionQuery.GL_SAMPLES_PASSED_ARB : GL15.GL_SAMPLES_PASSED);

        double x0 = minX - TileEntityRendererDispatcher.staticPlayerX;
        double y0 = minY - TileEntityRendererDispatcher.staticPlayerY;
        double z0 = minZ - TileEntityRendererDispatcher.staticPlayerZ;
        double x1 = maxX - TileEntityRendererDispatcher.staticPlayerX;
        double y1 = maxY - TileEntityRendererDispatcher.staticPlayerY;
        double z1 = maxZ - TileEntityRendererDispatcher.staticPlayerZ;

        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            try {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(false);
                GL11.glColorMask(false, false, false, false);

                cullingBeginQuery(state, queryId);
                try {
                    boolean drawing = false;
                    try {
                        GL11.glBegin(GL11.GL_QUADS);
                        drawing = true;
                        // +X
                        GL11.glVertex3d(x1, y0, z0);
                        GL11.glVertex3d(x1, y1, z0);
                        GL11.glVertex3d(x1, y1, z1);
                        GL11.glVertex3d(x1, y0, z1);
                        // -X
                        GL11.glVertex3d(x0, y0, z1);
                        GL11.glVertex3d(x0, y1, z1);
                        GL11.glVertex3d(x0, y1, z0);
                        GL11.glVertex3d(x0, y0, z0);
                        // +Y
                        GL11.glVertex3d(x0, y1, z0);
                        GL11.glVertex3d(x0, y1, z1);
                        GL11.glVertex3d(x1, y1, z1);
                        GL11.glVertex3d(x1, y1, z0);
                        // -Y
                        GL11.glVertex3d(x0, y0, z1);
                        GL11.glVertex3d(x0, y0, z0);
                        GL11.glVertex3d(x1, y0, z0);
                        GL11.glVertex3d(x1, y0, z1);
                        // +Z
                        GL11.glVertex3d(x1, y0, z1);
                        GL11.glVertex3d(x1, y1, z1);
                        GL11.glVertex3d(x0, y1, z1);
                        GL11.glVertex3d(x0, y0, z1);
                        // -Z
                        GL11.glVertex3d(x0, y0, z0);
                        GL11.glVertex3d(x0, y1, z0);
                        GL11.glVertex3d(x1, y1, z0);
                        GL11.glVertex3d(x1, y0, z0);
                    } finally {
                        if (drawing) {
                            GL11.glEnd();
                        }
                    }
                } finally {
                    cullingEndQuery(state);
                }
            } finally {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
        } finally {
            try {
                GL11.glPopAttrib();
            } finally {
                GL11.glMatrixMode(previousMatrixMode);
            }
        }
        state.queryPending = true;
    }

    @SideOnly(Side.CLIENT)
    default void cullingBeginQuery(GpuQueryState state, int queryId) {
        if (state.useArbApi) {
            ARBOcclusionQuery.glBeginQueryARB(state.queryTarget, queryId);
        } else {
            GL15.glBeginQuery(state.queryTarget, queryId);
        }
    }

    @SideOnly(Side.CLIENT)
    default void cullingEndQuery(GpuQueryState state) {
        if (state.useArbApi) {
            ARBOcclusionQuery.glEndQueryARB(state.queryTarget);
        } else {
            GL15.glEndQuery(state.queryTarget);
        }
    }

    @SideOnly(Side.CLIENT)
    default QueryApi cullingSelectQueryApi(@Nullable ContextCapabilities caps) {
        if (caps == null) {
            return QueryApi.NONE;
        }
        // Prefer newer query target first, then progressively degrade.
        if (caps.OpenGL33) {
            return QueryApi.GL33_ANY_SAMPLES;
        }
        if (caps.OpenGL15) {
            return QueryApi.GL15_SAMPLES;
        }
        if (caps.GL_ARB_occlusion_query) {
            return QueryApi.ARB;
        }
        return QueryApi.NONE;
    }

    @SideOnly(Side.CLIENT)
    @Nullable
    default double[] cullingComputeAabb(List<Vec3d> samplePoints) {
        if (samplePoints.isEmpty()) {
            return null;
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3d point : samplePoints) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }
        minX -= OCCLUSION_AABB_EXPAND_EPSILON;
        minY -= OCCLUSION_AABB_EXPAND_EPSILON;
        minZ -= OCCLUSION_AABB_EXPAND_EPSILON;
        maxX += OCCLUSION_AABB_EXPAND_EPSILON;
        maxY += OCCLUSION_AABB_EXPAND_EPSILON;
        maxZ += OCCLUSION_AABB_EXPAND_EPSILON;
        if (!(minX <= maxX) || !(minY <= maxY) || !(minZ <= maxZ)) {
            return null;
        }
        return new double[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    class CacheEntry {

        public final World world;
        public final long tick;
        public final List<Vec3d> points;

        public CacheEntry(World world, long tick, List<Vec3d> points) {
            this.world = world;
            this.tick = tick;
            this.points = points;
        }
    }

    class OcclusionResultCacheEntry {

        public final World world;
        public final long tick;
        public final Vec3d eyePos;
        public final Vec3d lookVec;
        public final boolean culled;

        public OcclusionResultCacheEntry(World world, long tick, Vec3d eyePos, Vec3d lookVec, boolean culled) {
            this.world = world;
            this.tick = tick;
            this.eyePos = eyePos;
            this.lookVec = lookVec;
            this.culled = culled;
        }
    }

    class GpuQueryState {

        public int queryId = -1;
        public boolean queryPending = false;
        public boolean lastVisible = true;
        public boolean useArbApi = false;
        public int queryTarget = GL15.GL_SAMPLES_PASSED;
        public long lastTouchedTick = Long.MIN_VALUE;
        public long lastIssueTick = Long.MIN_VALUE;
    }

    enum QueryApi {
        NONE,
        GL33_ANY_SAMPLES,
        GL15_SAMPLES,
        ARB
    }
}
