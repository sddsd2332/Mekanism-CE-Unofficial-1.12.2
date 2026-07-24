package mekanism.common.base;

import mekanism.api.Coord4D;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
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
