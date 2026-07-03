package mekanism.common.util;

import mekanism.common.content.network.distribution.EnergyAcceptorTarget;
import mekanism.common.lib.distribution.DoubleSplitInfo;
import mekanism.common.lib.distribution.IntegerSplitInfo;
import mekanism.common.lib.distribution.SplitInfo;
import mekanism.common.lib.distribution.Target;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.function.BiConsumer;

public class EmitUtils {

    /**
     * @param <HANDLER>        The handler of our target.
     * @param <TYPE>           The type of the number
     * @param <EXTRA>          Any extra information we may need.
     * @param <TARGET>         The emitter target.
     * @param availableTargets The targets to distribute toSend fairly among.
     * @param splitInfo        Information containing the split.
     * @param toSend           Any extra information such as gas stack or fluid stack.
     * @return The amount that actually got sent.
     */
    private static <HANDLER, TYPE extends Number & Comparable<TYPE>, EXTRA, TARGET extends Target<HANDLER, TYPE, EXTRA>> TYPE sendToAcceptors(
            TARGET availableTargets, SplitInfo<TYPE> splitInfo, EXTRA toSend) {
        if (availableTargets.getHandlerCount() == 0) {
            return splitInfo.getTotalSent();
        }

        //Simulate addition, sending when the requested amount is less than the amountPer
        // splitInfo gets adjusted to account for how much is actually sent
        availableTargets.sendPossible(toSend, splitInfo);

        //Only run this if we changed the amountPer from when we first/last ran things
        while (splitInfo.amountPerChanged) {
            splitInfo.amountPerChanged = false;
            //splitInfo gets adjusted to account for how much is actually sent,
            // and if amountPer got changed again and we need to rerun this
            availableTargets.shiftNeeded(splitInfo);
        }

        //Evenly distribute the remaining amount we have to give between all targets and handlers
        // splitInfo gets adjusted to account for how much is actually sent
        availableTargets.sendRemainingSplit(splitInfo);
        return splitInfo.getTotalSent();
    }

    /**
     * @param <HANDLER>        The handler of our target.
     * @param <EXTRA>          Any extra information we may need
     * @param <TARGET>         The emitter target
     * @param availableTargets The targets to distribute toSend fairly among.
     * @param amountToSplit    The amount to split between all the targets
     * @param toSend           Any extra information such as gas stack or fluid stack.
     * @return The amount that actually got sent.
     */
    public static <HANDLER, EXTRA, TARGET extends Target<HANDLER, Integer, EXTRA>> int sendToAcceptors(TARGET availableTargets, int amountToSplit, EXTRA toSend) {
        return sendToAcceptors(availableTargets, new IntegerSplitInfo(amountToSplit, availableTargets.getHandlerCount()), toSend);
    }

    /**
     * @param availableTargets The EnergyAcceptorWrapper targets to send energy fairly to.
     * @param amountToSplit    The amount of energy to attempt to send
     * @return The amount that actually got sent
     */
    public static double sendToAcceptors(EnergyAcceptorTarget availableTargets, double amountToSplit) {
        return sendToAcceptors(availableTargets, new DoubleSplitInfo(amountToSplit, availableTargets.getHandlerCount()), amountToSplit);
    }

    /**
     * @param availableTargets The EnergyAcceptorWrapper targets to send energy fairly to.
     * @param amountToSplit    The amount of energy to attempt to send
     * @return The amount that actually got sent
     */
    public static <HANDLER, TARGET extends Target<HANDLER, Double, Double>> double sendToAcceptors(TARGET availableTargets, double amountToSplit) {
        return sendToAcceptors(availableTargets, new DoubleSplitInfo(amountToSplit, availableTargets.getHandlerCount()), amountToSplit);
    }

    /**
     * Simple helper to loop over each side of the block and complete an action for each tile found
     *
     * @param world  - world to access
     * @param center - location to center search on
     * @param sides  - sides to search
     * @param action - action to complete
     */
    public static void forEachSide(World world, BlockPos center, Iterable<EnumFacing> sides, BiConsumer<TileEntity, EnumFacing> action) {
        if (sides != null) {
            //Loop provided sides
            for (EnumFacing side : sides) {
                //Validate we have a block loaded in world, prevents ghost chunk loading
                final BlockPos pos = center.offset(side);
                if (world.isBlockLoaded(pos)) {
                    //Get tile and provide if not null
                    final TileEntity tileEntity = world.getTileEntity(pos);
                    if (tileEntity != null) {
                        action.accept(tileEntity, side);
                    }
                }
            }
        }
    }
}
