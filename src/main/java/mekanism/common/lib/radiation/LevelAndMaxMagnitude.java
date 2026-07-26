package mekanism.common.lib.radiation;

public final class LevelAndMaxMagnitude {

    public static final LevelAndMaxMagnitude BASELINE = new LevelAndMaxMagnitude(RadiationManager.BASELINE, RadiationManager.BASELINE);

    private final double level;
    private final double maxMagnitude;

    public LevelAndMaxMagnitude(double level, double maxMagnitude) {
        this.level = RadiationUtil.sanitizeAtLeastBaseline(level);
        this.maxMagnitude = RadiationUtil.sanitizeAtLeastBaseline(maxMagnitude);
    }

    public double getLevel() {
        return level;
    }

    public double getMaxMagnitude() {
        return maxMagnitude;
    }
}
