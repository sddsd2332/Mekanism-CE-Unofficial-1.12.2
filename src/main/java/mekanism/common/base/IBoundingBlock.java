package mekanism.common.base;

import mekanism.api.Coord4D;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Internal interface.  A bounding block is not actually a 'bounding' block, it is really just a fake block that is used to mimic actual block bounds.
 *
 * @author AidanBrady
 */
public interface IBoundingBlock {

    /**
     * Small render-only allowance for TESR/model vertices which land just outside
     * an integer block footprint because of rotations, piston interpolation, or
     * floating point rounding. This does not change the blocks occupied by a
     * machine; it only keeps vanilla frustum culling from clipping the model.
     */
    double RENDER_BOUNDS_EPSILON = 1D / 16D;

    /** Result of attempting the shared, preflighted bounding-block placement path. */
    enum PlacementResult {
        SUCCESS,
        BLOCKED,
        LEGACY
    }

    /** A position occupied by either a normal or capability-forwarding bounding block. */
    final class BoundingBlockData {

        private final BlockPos position;
        private final boolean advanced;

        public BoundingBlockData(@Nonnull BlockPos position, boolean advanced) {
            this.position = position.toImmutable();
            this.advanced = advanced;
        }

        @Nonnull
        public BlockPos getPosition() {
            return position;
        }

        public boolean isAdvanced() {
            return advanced;
        }
    }

    /**
     * Called when the main block is placed.
     */
    void onPlace();

    /**
     * Called when any part of the structure is broken.
     */
    void onBreak();

    /**
     * Describes every auxiliary block occupied by this machine. Implementations in the
     * base mod use this as the single layout source for validation, placement and removal.
     * The default keeps binary compatibility with older addons that only implement
     * {@link #onPlace()} and {@link #onBreak()}.
     */
    default void collectBoundingBlocks(@Nonnull BiConsumer<BlockPos, Boolean> consumer) {
    }

    /** Called after the shared path has successfully created the full layout. */
    default void onBoundingBlocksPlaced() {
    }

    @Nonnull
    default List<BoundingBlockData> getBoundingBlocks() {
        List<BoundingBlockData> blocks = new ArrayList<>();
        collectBoundingBlocks((position, advanced) -> blocks.add(new BoundingBlockData(position, advanced)));
        return blocks.isEmpty() ? Collections.emptyList() : blocks;
    }

    /**
     * Builds a finite world-space render box from the machine's declared occupied blocks.
     * Returns {@code null} for legacy implementations which do not expose their layout, so
     * callers can preserve the old fail-open rendering behavior for binary-compatible addons.
     */
    @Nullable
    default AxisAlignedBB getBoundingBlockRenderBounds(@Nonnull BlockPos main) {
        int[] bounds = {
              Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
              Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE
        };
        boolean[] found = {false};
        collectBoundingBlocks((position, advanced) -> {
            if (position == null) {
                return;
            }
            found[0] = true;
            bounds[0] = Math.min(bounds[0], position.getX());
            bounds[1] = Math.min(bounds[1], position.getY());
            bounds[2] = Math.min(bounds[2], position.getZ());
            bounds[3] = Math.max(bounds[3], position.getX());
            bounds[4] = Math.max(bounds[4], position.getY());
            bounds[5] = Math.max(bounds[5], position.getZ());
        });
        if (!found[0]) {
            return null;
        }
        bounds[0] = Math.min(bounds[0], main.getX());
        bounds[1] = Math.min(bounds[1], main.getY());
        bounds[2] = Math.min(bounds[2], main.getZ());
        bounds[3] = Math.max(bounds[3], main.getX());
        bounds[4] = Math.max(bounds[4], main.getY());
        bounds[5] = Math.max(bounds[5], main.getZ());
        return new AxisAlignedBB(bounds[0] - RENDER_BOUNDS_EPSILON,
              bounds[1] - RENDER_BOUNDS_EPSILON,
              bounds[2] - RENDER_BOUNDS_EPSILON,
              bounds[3] + 1D + RENDER_BOUNDS_EPSILON,
              bounds[4] + 1D + RENDER_BOUNDS_EPSILON,
              bounds[5] + 1D + RENDER_BOUNDS_EPSILON);
    }

    /** Performs a complete replaceability check before creating any auxiliary blocks. */
    @Nonnull
    default PlacementResult tryPlaceBoundingBlocks(@Nonnull World world, @Nonnull Coord4D main) {
        List<BoundingBlockData> blocks = getBoundingBlocks();
        if (blocks.isEmpty()) {
            return PlacementResult.LEGACY;
        }
        if (MekanismUtils.tryPlaceBoundingBlocks(world, main, blocks)) {
            onBoundingBlocksPlaced();
            return PlacementResult.SUCCESS;
        }
        return PlacementResult.BLOCKED;
    }

    /**
     * Removes only bounding blocks which are still linked to this machine. Returns false
     * for legacy addon implementations so their existing removal callback can be used.
     */
    default boolean removeBoundingBlocks(@Nonnull World world, @Nonnull BlockPos main) {
        List<BoundingBlockData> blocks = getBoundingBlocks();
        if (blocks.isEmpty()) {
            return false;
        }
        MekanismUtils.removeBoundingBlocks(world, main, blocks);
        return true;
    }

    /**
     * Used for getting the proper BlockFaceShape for the bounding block.
     *
     * @param face   The face of the block at the offset that the shape is needed for.
     * @param offset Offset from the implementation of IBoundingBlock
     * @return A BlockFaceShape
     */
    @Nonnull
    default BlockFaceShape getOffsetBlockFaceShape(@Nonnull EnumFacing face, @Nonnull Vec3i offset) {
        return BlockFaceShape.UNDEFINED;
    }
}
