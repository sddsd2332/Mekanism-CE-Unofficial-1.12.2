package mekanism.common.content.gear;

import mekanism.common.content.gear.mekatool.ModuleVeinMiningUnit;
import mekanism.common.util.MultipartUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public interface IBlastingItem {

    Map<BlockPos, IBlockState> getBlastedBlocks(World world, EntityPlayer player, ItemStack stack, BlockPos pos, IBlockState state);

    default Map<BlockPos, IBlockState> getBlastedBlocksForRendering(World world, EntityPlayer player, ItemStack stack, BlockPos pos, IBlockState state) {
        return getBlastedBlocks(world, player, stack, pos, state);
    }

    static Map<BlockPos, IBlockState> findPositions(World world, BlockPos targetPos, EntityPlayer player, int radius) {
        if (radius > 0) {
            EnumFacing targetSide = getTargetSide(world, targetPos, player);
            if (targetSide != null) {
                Vec3i lower, upper;
                switch (targetSide) {
                    case UP, DOWN -> {
                        lower = new Vec3i(-radius, 0, -radius);
                        upper = new Vec3i(radius, 0, radius);
                    }
                    case EAST, WEST -> {
                        lower = new Vec3i(0, -1, -radius);
                        upper = new Vec3i(0, 2 * radius - 1, radius);
                    }
                    case NORTH, SOUTH -> {
                        lower = new Vec3i(-radius, -1, 0);
                        upper = new Vec3i(radius, 2 * radius - 1, 0);
                    }
                    default -> {
                        lower = new Vec3i(0, 0, 0);
                        upper = new Vec3i(0, 0, 0);
                    }
                }
                Map<BlockPos, IBlockState> found = new HashMap<>();
                for (BlockPos nextPos : BlockPos.getAllInBoxMutable(targetPos.add(lower), targetPos.add(upper))) {
                    IBlockState nextState = world.getBlockState(nextPos);
                    if (canBlastBlock(world, nextPos, nextState)) {
                        found.put(nextPos.toImmutable(), nextState);
                    }
                }
                return found;
            }
        }
        return Collections.emptyMap();
    }


    /**
     * Very watered down version of {@link VoxelShape#clip(Vec3, Vec3, BlockPos)}, that instead of creating an extra voxel shape from the bounds and then checking if it
     * is a full side and using a quicker get nearest, we just do a single call on the overall AABB to get the direction after doing the initial check from
     * {@link VoxelShape#clip(Vec3, Vec3, BlockPos)}. In theory this might be ever so slightly worse performance wise for a full cube, it shouldn't be noticeably worse,
     * and for cases where there are more complex non-full block shapes, then we can skip a handful of checks as well as creating excess objects for the voxel shape and
     * hit result as the only details we care about from the hit result is the direction we hit.
     */
    @Nullable
    static EnumFacing getTargetSide(World world, BlockPos targetPos, EntityPlayer player) {
        Pair<Vec3d, Vec3d> rayTraceVectors = MultipartUtils.getRayTraceVectors(player);
        Vec3d start = rayTraceVectors.getLeft();
        Vec3d end = rayTraceVectors.getRight();
        Vec3d distance = end.subtract(start);
        if (distance.lengthSquared() < 1.0E-7D) {
            return null;
        }
        IBlockState targetState = world.getBlockState(targetPos);
        AxisAlignedBB shape = targetState.getBoundingBox(world, targetPos);
        if (shape != null) {
            //todo
            double[] ignoredMinDistance = {1.0D};
            return getDirection(shape.offset(targetPos), start, ignoredMinDistance, null, end.x - start.x, end.y - start.y, end.z - start.z);
        }
        return null;
    }

    @Nullable
    static EnumFacing getDirection(AxisAlignedBB pAabb, Vec3d pStart, double[] pMinDistance, @Nullable EnumFacing pFacing, double pDeltaX, double pDeltaY, double pDeltaZ) {
        if (pDeltaX > 1.0E-7D) {
            pFacing = clipPoint(pMinDistance, pFacing, pDeltaX, pDeltaY, pDeltaZ, pAabb.minX, pAabb.minY, pAabb.maxY, pAabb.minZ, pAabb.maxZ, EnumFacing.WEST, pStart.x, pStart.y, pStart.z);
        } else if (pDeltaX < -1.0E-7D) {
            pFacing = clipPoint(pMinDistance, pFacing, pDeltaX, pDeltaY, pDeltaZ, pAabb.maxX, pAabb.minY, pAabb.maxY, pAabb.minZ, pAabb.maxZ, EnumFacing.EAST, pStart.x, pStart.y, pStart.z);
        }

        if (pDeltaY > 1.0E-7D) {
            pFacing = clipPoint(pMinDistance, pFacing, pDeltaY, pDeltaZ, pDeltaX, pAabb.minY, pAabb.minZ, pAabb.maxZ, pAabb.minX, pAabb.maxX, EnumFacing.DOWN, pStart.y, pStart.z, pStart.x);
        } else if (pDeltaY < -1.0E-7D) {
            pFacing = clipPoint(pMinDistance, pFacing, pDeltaY, pDeltaZ, pDeltaX, pAabb.maxY, pAabb.minZ, pAabb.maxZ, pAabb.minX, pAabb.maxX, EnumFacing.UP, pStart.y, pStart.z, pStart.x);
        }

        if (pDeltaZ > 1.0E-7D) {
            pFacing = clipPoint(pMinDistance, pFacing, pDeltaZ, pDeltaX, pDeltaY, pAabb.minZ, pAabb.minX, pAabb.maxX, pAabb.minY, pAabb.maxY, EnumFacing.NORTH, pStart.z, pStart.x, pStart.y);
        } else if (pDeltaZ < -1.0E-7D) {
            pFacing = clipPoint(pMinDistance, pFacing, pDeltaZ, pDeltaX, pDeltaY, pAabb.maxZ, pAabb.minX, pAabb.maxX, pAabb.minY, pAabb.maxY, EnumFacing.SOUTH, pStart.z, pStart.x, pStart.y);
        }

        return pFacing;
    }

    @Nullable
    static EnumFacing clipPoint(double[] pMinDistance, @Nullable EnumFacing pPrevDirection, double pDistanceSide, double pDistanceOtherA, double pDistanceOtherB, double pMinSide, double pMinOtherA, double pMaxOtherA, double pMinOtherB, double pMaxOtherB, EnumFacing pHitSide, double pStartSide, double pStartOtherA, double pStartOtherB) {
        double d0 = (pMinSide - pStartSide) / pDistanceSide;
        double d1 = pStartOtherA + d0 * pDistanceOtherA;
        double d2 = pStartOtherB + d0 * pDistanceOtherB;
        if (0.0D < d0 && d0 < pMinDistance[0] && pMinOtherA - 1.0E-7D < d1 && d1 < pMaxOtherA + 1.0E-7D && pMinOtherB - 1.0E-7D < d2 && d2 < pMaxOtherB + 1.0E-7D) {
            pMinDistance[0] = d0;
            return pHitSide;
        } else {
            return pPrevDirection;
        }
    }

    static boolean canBlastBlock(World world, BlockPos pos, IBlockState state) {
        return !state.getBlock().isAir(state, world, pos) && !state.getMaterial().isLiquid() && ModuleVeinMiningUnit.canVeinBlock(state) && state.getBlockHardness(world, pos) > 0;
    }


}
