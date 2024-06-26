package mekanism.common.entity.ai;

import mekanism.common.entity.EntityRobit;
import net.minecraft.entity.player.EntityPlayer;

public class RobitAIFollow extends RobitAIBase {

    /**
     * The robit's owner.
     */
    private EntityPlayer theOwner;

    /**
     * The distance between the owner the robit must be at in order for the protocol to begin.
     */
    private float maxDist;

    /**
     * The distance between the owner the robit must reach before it stops the protocol.
     */
    private float minDist;

    public RobitAIFollow(EntityRobit entityRobit, float speed, float min, float max) {
        super(entityRobit, speed);
        minDist = min;
        maxDist = max;
    }

    @Override
    public boolean shouldExecute() {
        EntityPlayer player = theRobit.getOwner();
        if (player == null) {
            return false;
        } else if (theRobit.world.provider.getDimension() != player.world.provider.getDimension()) {
            return false;
        } else if (!theRobit.getFollowing()) {
            //Still looks up at the player if on chargepad or not following
            theRobit.getLookHelper().setLookPositionWithEntity(player, 6.0F, theRobit.getVerticalFaceSpeed() / 10);
            return false;
        } else if (theRobit.getDistanceSq(player) < (minDist * minDist)) {
            return false;
        } else if (theRobit.getEnergy() == 0) {
            return false;
        }
        theOwner = player;
        return true;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return !thePathfinder.noPath() && theRobit.getDistanceSq(theOwner) > (maxDist * maxDist) && theRobit.getFollowing() && theRobit.getEnergy() > 0
                && theOwner.world.provider.getDimension() == theRobit.world.provider.getDimension();
    }

    @Override
    public void resetTask() {
        theOwner = null;
        super.resetTask();
    }

    @Override
    public void updateTask() {
        if (theRobit.getFollowing()) {
            updateTask(theOwner);
        }
    }
}
