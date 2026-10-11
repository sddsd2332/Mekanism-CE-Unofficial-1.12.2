package mekanism.common.lib.distribution;

public class DoubleSplitInfo extends SplitInfo<Double> {

    private double amountToSplit;
    private double amountPerTarget;
    private double sentSoFar;

    public DoubleSplitInfo(double amountToSplit, int totalTargets) {
        super(totalTargets);
        this.amountToSplit = amountToSplit;
        amountPerTarget = toSplitAmong == 0 ? 0 : amountToSplit / toSplitAmong;
        sentSoFar = 0;
    }

    @Override
    public void send(Double amountNeeded) {
        //If we are giving it, then lower the amount we are checking/splitting
        amountToSplit = amountToSplit - amountNeeded;
        sentSoFar = sentSoFar + amountNeeded;
        toSplitAmong--;
        //Only recalculate it if it is not willing to accept/doesn't want the
        // full per side split
        if (!amountNeeded.equals(amountPerTarget) && toSplitAmong != 0) {
            double amountPerLast = amountPerTarget;
            amountPerTarget = amountToSplit / toSplitAmong;
            if (!amountPerChanged && amountPerTarget != amountPerLast) {
                amountPerChanged = true;
            }
        }
    }

    @Override
    public Double getShareAmount() {
        return amountPerTarget;
    }

    @Override
    public Double getRemainderAmount() {
        //TODO: Decide if we want to try and adjust for the very small amount that may get lost/be a remainder
        // currently we just ignore it
        return amountPerTarget;
    }

    @Override
    public Double getTotalSent() {
        return sentSoFar;
    }
}
