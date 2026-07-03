package mekanism.common.lib.distribution;

public class IntegerSplitInfo extends SplitInfo<Integer> {

    private int amountToSplit;
    private int amountPerTarget;
    private int sentSoFar;

    public IntegerSplitInfo(int amountToSplit, int totalTargets) {
        super(totalTargets);
        this.amountToSplit = amountToSplit;
        amountPerTarget = toSplitAmong == 0 ? 0 : amountToSplit / toSplitAmong;
    }

    @Override
    public void send(Integer amountNeeded) {
        amountToSplit -= amountNeeded;
        sentSoFar += amountNeeded;
        toSplitAmong--;
        if (amountNeeded != amountPerTarget && toSplitAmong != 0) {
            int amountPerLast = amountPerTarget;
            amountPerTarget = amountToSplit / toSplitAmong;
            if (!amountPerChanged && amountPerTarget != amountPerLast) {
                amountPerChanged = true;
            }
        }
    }

    @Override
    public Integer getShareAmount() {
        return amountPerTarget;
    }

    @Override
    public Integer getRemainderAmount() {
        return toSplitAmong == 0 ? amountPerTarget : amountPerTarget + amountToSplit % toSplitAmong;
    }

    @Override
    public Integer getTotalSent() {
        return sentSoFar;
    }
}
