package mekanism.common.util;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Contract;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

public class WorldUtils {

    /**
     * Checks to see if the block at the position can see the sky, and it is daytime.
     *
     * @param world World to check in.
     * @param pos   Position to check.
     * @return {@code true} if it can.
     */
    @Contract("null, _ -> false")
    public static boolean canSeeSun(@Nullable World world, BlockPos pos) {
        //Note: We manually handle the world#isDaytime check by just checking the subtracted skylight
        // as vanilla returns false if the world's time is set to a fixed value even if that time
        // would effectively be daytime
        return world != null && world.provider.hasSkyLight() && world.getSkylightSubtracted() < 4 && world.canSeeSky(pos);
    }

    /**
     * Vanilla copy of {@link net.minecraft.client.world.ClientWorld#getSkyDarken(float)} used to be World#getSunBrightness
     */
    public static float getSunBrightness(World world, float partialTicks) {
        float f = world.getCelestialAngle(partialTicks);
        float f1 = 1.0F - (MathHelper.cos(f * ((float) Math.PI * 2F)) * 2.0F + 0.2F);
        f1 = MathHelper.clamp(f1, 0.0F, 1.0F);
        f1 = 1.0F - f1;
        f1 = (float) (f1 * (1.0D - world.getRainStrength(partialTicks) * 5.0F / 16.0D));
        f1 = (float) (f1 * (1.0D - world.getThunderStrength(partialTicks) * 5.0F / 16.0D));
        return f1 * 0.8F + 0.2F;
    }

    /**
     * Gets the distance to a defined positions.
     *
     * @return the distance to the defined positions
     */
    public static double distanceBetween(BlockPos start, BlockPos end) {
        return MathHelper.sqrt(start.distanceSq(end));
    }

    /**
     * Gets a blockstate if the location is loaded
     *
     * @param world world
     * @param pos   position
     * @return optional containing the blockstate if found, empty optional if not loaded
     */
    @Nonnull
    public static Optional<IBlockState> getBlockState(@Nullable IBlockAccess world, @Nonnull BlockPos pos) {
        if (!isBlockLoaded(world, pos)) {
            //If the world is null, or it is a world reader and the block is not loaded, return empty
            return Optional.empty();
        }
        return Optional.of(world.getBlockState(pos));
    }


    /**
     * Checks if a position is in bounds of the world, and is loaded
     *
     * @param world world
     * @param pos   position
     * @return True if the position is loaded or the given world is of a superclass of IWorldReader that does not have a concept of being loaded.
     */
    @Contract("null, _ -> false")
    public static boolean isBlockLoaded(@Nullable IBlockAccess world, @Nonnull BlockPos pos) {
        if (world == null || !isValid(pos)) {
            return false;
        } else if (world instanceof World w) {
            //Note: We don't bother checking if it is a world and then isBlockPresent because
            // all that does is also validate the y value is in bounds, and we already check to make
            // sure the position is valid both in the y and xz directions
            return w.isBlockLoaded(pos);
        }
        return true;
    }


    public static boolean isValid(BlockPos pos) {
        return !isOutsideBuildHeight(pos) && pos.getX() >= -30000000 && pos.getZ() >= -30000000 && pos.getX() < 30000000 && pos.getZ() < 30000000;
    }

    public static boolean isOutsideBuildHeight(BlockPos pos) {
        return pos.getY() < 0 || pos.getY() >= 256;
    }


    /**
     * Gets a tile entity if the location is loaded
     *
     * @param world world
     * @param pos   position
     * @return tile entity if found, null if either not found or not loaded
     */
    @Nullable
    @Contract("null, _ -> null")
    public static TileEntity getTileEntity(@Nullable IBlockAccess world, @Nonnull BlockPos pos) {
        if (!isBlockLoaded(world, pos)) {
            //If the world is null, or it is a world reader and the block is not loaded, return null
            return null;
        }
        return world.getTileEntity(pos);
    }


    /**
     * Marks a chunk as dirty if it is currently loaded
     */
    public static void markChunkDirty(World world, BlockPos pos) {
        if (isBlockLoaded(world, pos)) {
            world.getChunk(pos).markDirty();
        }
        //TODO: This line below is now (1.16+) called by the mark chunk dirty method (without even validating if it is loaded).
        // And with it causes issues where chunks are easily ghost loaded. Why was it added like that and do we need to somehow
        // also update neighboring comparators
        //world.updateComparatorOutputLevel(pos, world.getBlockState(pos).getBlock()); //Notify neighbors of changes
    }
}
