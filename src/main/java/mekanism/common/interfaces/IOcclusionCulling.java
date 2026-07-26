package mekanism.common.interfaces;

import mekanism.client.render.OptifineRenderCompat;
import mekanism.common.base.IBoundingBlock;
import mekanism.common.tile.TileEntityBoundingBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

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
    int BOUNDING_PROBE_POINT_LIMIT = 96;
    @SideOnly(Side.CLIENT)
    double BOUNDING_EDGE_PROBE_MIN = 0.02D;
    @SideOnly(Side.CLIENT)
    double BOUNDING_EDGE_PROBE_MAX = 0.98D;
    @SideOnly(Side.CLIENT)
    double OCCLUSION_VIEW_DOT_THRESHOLD = -0.15D;
    @SideOnly(Side.CLIENT)
    int OCCLUSION_AABB_FACE_MAX_STEPS = 4;
    @SideOnly(Side.CLIENT)
    int OCCLUSION_AABB_MAX_RAY_BUDGET = 64;
    @SideOnly(Side.CLIENT)
    double OCCLUSION_AABB_EXPAND_EPSILON = 0.02D;
    /** Visible results may be reused briefly when doing so can only cause extra rendering. */
    @SideOnly(Side.CLIENT)
    int OCCLUSION_VISIBLE_RESULT_CACHE_TICKS = 2;
    @SideOnly(Side.CLIENT)
    double OCCLUSION_VISIBLE_CAMERA_POSITION_EPSILON = 0.125D;
    @SideOnly(Side.CLIENT)
    double OCCLUSION_VISIBLE_CAMERA_LOOK_DOT = 0.9995D;
    Map<IOcclusionCulling, CacheEntry> OCCLUSION_CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    Map<IOcclusionCulling, OcclusionResultCacheEntry> OCCLUSION_RESULT_CACHE = Collections.synchronizedMap(new WeakHashMap<>());

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
        if (OptifineRenderCompat.isShadowPass()) {
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
        if (!cullingIsFiniteVec(eyePos) || !cullingIsFiniteVec(lookVec) || lookVec.lengthSquared() <= 1.0E-8D) {
            return false;
        }
        long worldTime = world.getTotalWorldTime();
        AxisAlignedBB renderBounds = cullingGetRenderBounds();
        Boolean cachedResult = cullingGetCachedResult(world, worldTime, eyePos, lookVec, renderBounds);
        if (cachedResult != null) {
            return cachedResult;
        }
        List<Vec3d> samplePoints = cullingMergeRenderBoundsSamplePoints(computeOcclusionSamplePoints(), renderBounds);
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
            return cullingCacheResult(world, worldTime, eyePos, lookVec, true, renderBounds);
        }
        for (Vec3d samplePoint : samplePoints) {
            if (cullingCanSeePointCached(eyePos, samplePoint, rayCache)) {
                return cullingCacheResult(world, worldTime, eyePos, lookVec, false, renderBounds);
            }
        }
        // Borrowed from entity-culling AABB-side tracing: if corner probes are all blocked,
        // still allow rendering when any visible face sample can be seen.
        if (cullingIsAabbFaceVisible(eyePos, samplePoints, rayCache)) {
            return cullingCacheResult(world, worldTime, eyePos, lookVec, false, renderBounds);
        }
        return cullingCacheResult(world, worldTime, eyePos, lookVec, true, renderBounds);
    }

    @SideOnly(Side.CLIENT)
    @Nullable
    default Boolean cullingGetCachedResult(World world, long worldTime, Vec3d eyePos, Vec3d lookVec) {
        return cullingGetCachedResult(world, worldTime, eyePos, lookVec, cullingGetRenderBounds());
    }

    @SideOnly(Side.CLIENT)
    @Nullable
    default Boolean cullingGetCachedResult(World world, long worldTime, Vec3d eyePos, Vec3d lookVec,
                                           @Nullable AxisAlignedBB renderBounds) {
        OcclusionResultCacheEntry entry = OCCLUSION_RESULT_CACHE.get(this);
        if (entry == null || entry.world != world || !cullingAreSameBounds(entry.renderBounds, renderBounds)) {
            return null;
        }
        if (entry.tick == worldTime) {
            if (entry.eyePos.equals(eyePos) && entry.lookVec.equals(lookVec)) {
                return entry.culled;
            }
            // The camera can move several render frames inside one world tick. Do
            // not repeat the full ray set for each frame; fail open until the next
            // tick so movement can never reuse an obsolete hidden result.
            return false;
        }
        // Reusing a visible result can only render an object which has since become
        // hidden. Never reuse a stale "culled" result, as that could hide geometry.
        if (!entry.culled && cullingIsVisibleResultCacheFresh(entry.tick, worldTime)
              && cullingIsCameraClose(entry.eyePos, entry.lookVec, eyePos, lookVec)) {
            return false;
        }
        return null;
    }

    @SideOnly(Side.CLIENT)
    default boolean cullingCacheResult(World world, long worldTime, Vec3d eyePos, Vec3d lookVec, boolean culled) {
        return cullingCacheResult(world, worldTime, eyePos, lookVec, culled, cullingGetRenderBounds());
    }

    @SideOnly(Side.CLIENT)
    default boolean cullingCacheResult(World world, long worldTime, Vec3d eyePos, Vec3d lookVec, boolean culled,
                                       @Nullable AxisAlignedBB renderBounds) {
        OCCLUSION_RESULT_CACHE.put(this, new OcclusionResultCacheEntry(world, worldTime, eyePos, lookVec, culled, renderBounds));
        return culled;
    }

    @SideOnly(Side.CLIENT)
    static boolean cullingIsVisibleResultCacheFresh(long cachedTick, long currentTick) {
        long age = currentTick - cachedTick;
        return age >= 0 && age < OCCLUSION_VISIBLE_RESULT_CACHE_TICKS;
    }

    @SideOnly(Side.CLIENT)
    static boolean cullingIsCameraClose(Vec3d cachedEye, Vec3d cachedLook, Vec3d eye, Vec3d look) {
        if (cachedEye == null || cachedLook == null || eye == null || look == null
              || !Double.isFinite(cachedEye.x) || !Double.isFinite(cachedEye.y) || !Double.isFinite(cachedEye.z)
              || !Double.isFinite(eye.x) || !Double.isFinite(eye.y) || !Double.isFinite(eye.z)) {
            return false;
        }
        if (cachedEye.squareDistanceTo(eye) > OCCLUSION_VISIBLE_CAMERA_POSITION_EPSILON * OCCLUSION_VISIBLE_CAMERA_POSITION_EPSILON) {
            return false;
        }
        double cachedLength = cachedLook.lengthSquared();
        double currentLength = look.lengthSquared();
        if (!Double.isFinite(cachedLength) || !Double.isFinite(currentLength)
              || cachedLength <= 1.0E-8D || currentLength <= 1.0E-8D) {
            return false;
        }
        return cachedLook.dotProduct(look) / Math.sqrt(cachedLength * currentLength) >= OCCLUSION_VISIBLE_CAMERA_LOOK_DOT;
    }

    @SideOnly(Side.CLIENT)
    static boolean cullingIsFiniteVec(@Nullable Vec3d vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    @SideOnly(Side.CLIENT)
    @Nullable
    default AxisAlignedBB cullingGetRenderBounds() {
        if (this instanceof TileEntity tile) {
            try {
                return tile.getRenderBoundingBox();
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    @SideOnly(Side.CLIENT)
    static boolean cullingAreSameBounds(@Nullable AxisAlignedBB first, @Nullable AxisAlignedBB second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return Double.compare(first.minX, second.minX) == 0 && Double.compare(first.minY, second.minY) == 0
              && Double.compare(first.minZ, second.minZ) == 0 && Double.compare(first.maxX, second.maxX) == 0
              && Double.compare(first.maxY, second.maxY) == 0 && Double.compare(first.maxZ, second.maxZ) == 0;
    }

    static boolean cullingIsBeyondRenderDistance(double distanceSquared, double maxDistanceSquared) {
        return Double.isFinite(distanceSquared) && Double.isFinite(maxDistanceSquared) && maxDistanceSquared >= 0 &&
              distanceSquared > maxDistanceSquared;
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
        AxisAlignedBB renderBounds = cullingGetRenderBounds();
        CacheEntry entry = OCCLUSION_CACHE.get(this);
        if (entry != null && entry.world == world && cullingIsSampleCacheFresh(entry.tick, worldTime)
              && cullingAreSameBounds(entry.renderBounds, renderBounds) && !entry.points.isEmpty()) {
            return entry.points;
        }

        List<Vec3d> rebuiltPoints = this instanceof IBoundingBlock ? cullingBuildBoundingOcclusionSamplePoints(world, pos) : cullingGetSingleBlockOcclusionSamplePoints(pos);
        OCCLUSION_CACHE.put(this, new CacheEntry(world, worldTime, rebuiltPoints, renderBounds));
        return rebuiltPoints;
    }

    static boolean cullingIsSampleCacheFresh(long cachedTick, long currentTick) {
        long age = currentTick - cachedTick;
        return age >= 0 && age < BOUNDING_SAMPLE_CACHE_INTERVAL;
    }

    @SideOnly(Side.CLIENT)
    default List<Vec3d> cullingMergeRenderBoundsSamplePoints(List<Vec3d> samplePoints) {
        return cullingMergeRenderBoundsSamplePoints(samplePoints, cullingGetRenderBounds());
    }

    @SideOnly(Side.CLIENT)
    default List<Vec3d> cullingMergeRenderBoundsSamplePoints(@Nullable List<Vec3d> samplePoints,
                                                             @Nullable AxisAlignedBB renderBounds) {
        List<Vec3d> renderPoints = cullingGetAabbOcclusionSamplePoints(renderBounds);
        int baseSize = samplePoints == null ? 0 : samplePoints.size();
        List<Vec3d> merged = new ArrayList<>(Math.min(BOUNDING_PROBE_POINT_LIMIT, renderPoints.size() + baseSize));
        Set<Vec3d> seen = new HashSet<>();
        // Reserve space for the finite render box first. For large structures the
        // structural sampler is capped, and the model envelope must not be the part
        // which gets dropped when that cap is reached.
        for (Vec3d point : renderPoints) {
            if (cullingIsFiniteVec(point) && seen.add(point)) {
                merged.add(point);
            }
        }
        if (samplePoints != null) {
            for (Vec3d point : samplePoints) {
                if (merged.size() >= BOUNDING_PROBE_POINT_LIMIT) {
                    break;
                }
                if (cullingIsFiniteVec(point) && seen.add(point)) {
                    merged.add(point);
                }
            }
        }
        return merged;
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
        return cullingGetAabbOcclusionSamplePoints(new AxisAlignedBB(pos));
    }

    @SideOnly(Side.CLIENT)
    default List<Vec3d> cullingGetAabbOcclusionSamplePoints(@Nullable AxisAlignedBB bounds) {
        if (bounds == null || !Double.isFinite(bounds.minX) || !Double.isFinite(bounds.minY) || !Double.isFinite(bounds.minZ)
              || !Double.isFinite(bounds.maxX) || !Double.isFinite(bounds.maxY) || !Double.isFinite(bounds.maxZ)
              || bounds.maxX <= bounds.minX || bounds.maxY <= bounds.minY || bounds.maxZ <= bounds.minZ) {
            return Collections.emptyList();
        }
        double insetX = Math.min(BOUNDING_EDGE_PROBE_MIN, (bounds.maxX - bounds.minX) * 0.25D);
        double insetY = Math.min(BOUNDING_EDGE_PROBE_MIN, (bounds.maxY - bounds.minY) * 0.25D);
        double insetZ = Math.min(BOUNDING_EDGE_PROBE_MIN, (bounds.maxZ - bounds.minZ) * 0.25D);
        double xMin = bounds.minX + insetX;
        double yMin = bounds.minY + insetY;
        double zMin = bounds.minZ + insetZ;
        double xMax = bounds.maxX - insetX;
        double yMax = bounds.maxY - insetY;
        double zMax = bounds.maxZ - insetZ;

        List<Vec3d> points = new ArrayList<>(9);
        points.add(new Vec3d((bounds.minX + bounds.maxX) * 0.5D, (bounds.minY + bounds.maxY) * 0.5D,
              (bounds.minZ + bounds.maxZ) * 0.5D));
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
        List<Vec3d> probes = new ArrayList<>(Math.min(BOUNDING_PROBE_POINT_LIMIT, orderedPoints.size() * 9));
        // Cover every selected structure block before spending the remaining budget
        // on corners. Sequentially adding all nine points per block biases the cap
        // toward the first few positions and can entirely miss a distant extreme.
        for (BlockPos point : orderedPoints) {
            if (probes.size() >= BOUNDING_PROBE_POINT_LIMIT) {
                return probes;
            }
            probes.add(new Vec3d(point).add(0.5D, 0.5D, 0.5D));
        }
        for (int corner = 0; corner < 8 && probes.size() < BOUNDING_PROBE_POINT_LIMIT; corner++) {
            for (BlockPos point : orderedPoints) {
                if (probes.size() >= BOUNDING_PROBE_POINT_LIMIT) {
                    break;
                }
                double x = point.getX() + ((corner & 4) == 0 ? BOUNDING_EDGE_PROBE_MIN : BOUNDING_EDGE_PROBE_MAX);
                double y = point.getY() + ((corner & 2) == 0 ? BOUNDING_EDGE_PROBE_MIN : BOUNDING_EDGE_PROBE_MAX);
                double z = point.getZ() + ((corner & 1) == 0 ? BOUNDING_EDGE_PROBE_MIN : BOUNDING_EDGE_PROBE_MAX);
                probes.add(new Vec3d(x, y, z));
            }
        }
        return probes;
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
        if (!cullingIsFiniteVec(eyePos) || !cullingIsFiniteVec(target)) {
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
    static void cullingClearClientCaches() {
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
    }

    class CacheEntry {

        public final World world;
        public final long tick;
        public final List<Vec3d> points;
        @Nullable
        public final AxisAlignedBB renderBounds;

        public CacheEntry(World world, long tick, List<Vec3d> points) {
            this(world, tick, points, null);
        }

        public CacheEntry(World world, long tick, List<Vec3d> points, @Nullable AxisAlignedBB renderBounds) {
            this.world = world;
            this.tick = tick;
            this.points = points;
            this.renderBounds = renderBounds;
        }
    }

    class OcclusionResultCacheEntry {

        public final World world;
        public final long tick;
        public final Vec3d eyePos;
        public final Vec3d lookVec;
        public final boolean culled;
        @Nullable
        public final AxisAlignedBB renderBounds;

        public OcclusionResultCacheEntry(World world, long tick, Vec3d eyePos, Vec3d lookVec, boolean culled) {
            this(world, tick, eyePos, lookVec, culled, null);
        }

        public OcclusionResultCacheEntry(World world, long tick, Vec3d eyePos, Vec3d lookVec, boolean culled,
                                         @Nullable AxisAlignedBB renderBounds) {
            this.world = world;
            this.tick = tick;
            this.eyePos = eyePos;
            this.lookVec = lookVec;
            this.culled = culled;
            this.renderBounds = renderBounds;
        }
    }

}
