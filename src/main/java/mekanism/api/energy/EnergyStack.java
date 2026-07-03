package mekanism.api.energy;

/**
 * Created by ben on 27/03/15.
 */
public class EnergyStack {

    public double amount;

    public EnergyStack(double newAmount) {
        amount = newAmount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public void setAmountClamped(double amount, double capacity) {
        setAmount(Math.max(0, capacity <= 0 ? amount : Math.min(capacity, amount)));
    }
}
