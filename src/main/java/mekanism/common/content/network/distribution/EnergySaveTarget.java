package mekanism.common.content.network.distribution;

import mcp.MethodsReturnNonnullByDefault;
import mekanism.api.energy.IEnergyContainer;
import mekanism.common.lib.distribution.SplitInfo;
import mekanism.common.lib.distribution.Target;

import javax.annotation.ParametersAreNonnullByDefault;

public class EnergySaveTarget extends Target<EnergySaveTarget.SaveHandler,Double,Double> {

    public EnergySaveTarget(int expectedSize) {
        super(expectedSize);
    }

    @Override
    protected void acceptAmount(SaveHandler handler, SplitInfo<Double> splitInfo, Double amount) {
        handler.acceptAmount(splitInfo, amount);
    }

    @Override
    protected Double simulate(SaveHandler handler, Double energyToSend) {
        return handler.simulate(energyToSend);
    }

    public void save() {
        for (SaveHandler handler : handlers) {
            handler.save();
        }
    }

    public void addDelegate(IEnergyContainer delegate) {
        this.addHandler(new SaveHandler(delegate));
    }

    @ParametersAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    public static class SaveHandler {
        private final IEnergyContainer delegate;
        private double currentStored = 0;

        public SaveHandler(IEnergyContainer delegate) {
            this.delegate = delegate;
        }

        protected void acceptAmount(SplitInfo<Double> splitInfo, double amount) {
            amount = Math.min(amount, delegate.getMaxEnergy() - currentStored);
            currentStored += amount;
            splitInfo.send(amount);
        }

        protected double simulate(double energyToSend) {
            return Math.min(energyToSend, delegate.getMaxEnergy() - currentStored);
        }

        protected void save() {
            delegate.setEnergy(currentStored);
        }
    }
}
