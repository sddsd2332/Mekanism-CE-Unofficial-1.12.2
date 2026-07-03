package mekanism.common.lib;

/**
 * Tracks the amount of energy received during the previous server tick.
 *
 * <p>1.12 still uses double precision energy values, so this mirrors the high-version
 * tracker semantics without changing the local energy type.</p>
 */
public class LastEnergyTracker {

    private double lastEnergyReceived;
    private double currentEnergyReceived;
    private long currentGameTime;

    public void received(long gameTime, double amount) {
        if (currentGameTime == gameTime) {
            currentEnergyReceived += amount;
        } else {
            lastEnergyReceived = currentEnergyReceived;
            currentGameTime = gameTime;
            currentEnergyReceived = amount;
        }
    }

    public double getLastEnergyReceived() {
        return lastEnergyReceived;
    }

    public void setLastEnergyReceived(double lastEnergyReceived) {
        this.lastEnergyReceived = lastEnergyReceived;
    }
}
