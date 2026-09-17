package mekanism.common.lib.radiation;

public final class LevelAndMaxMagnitude {

    public static final LevelAndMaxMagnitude BASELINE = new LevelAndMaxMagnitude(RadiationManager.BASELINE, RadiationManager.BASELINE);

    public static final LevelAndMaxMagnitude UNAVAILABLE = new LevelAndMaxMagnitude(Double.NaN, Double.NaN);
    public boolean isAvailable() { return !Double.isNaN(level); }

    private final double level;
    private final double maxMagnitude;

    public LevelAndMaxMagnitude(double level, double maxMagnitude) {
        this.level = Double.isNaN(level) ? Double.NaN : RadiationUtil.sanitizeAtLeastBaseline(level);
        this.maxMagnitude = Double.isNaN(maxMagnitude) ? Double.NaN : RadiationUtil.sanitizeAtLeastBaseline(maxMagnitude);
    }

    public double getLevel() {
        return level;
    }

    public double getMaxMagnitude() {
        return maxMagnitude;
    }
}
