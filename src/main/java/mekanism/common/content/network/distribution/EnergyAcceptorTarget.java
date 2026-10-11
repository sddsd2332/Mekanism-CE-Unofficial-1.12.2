package mekanism.common.content.network.distribution;

import mekanism.common.base.EnergyAcceptorWrapper;
import mekanism.common.lib.distribution.SplitInfo;
import mekanism.common.lib.distribution.Target;
import net.minecraft.util.EnumFacing;

public class EnergyAcceptorTarget extends Target<EnergyAcceptorTarget.SideHandler, Double, Double> {

    public EnergyAcceptorTarget() {
    }

    public EnergyAcceptorTarget(int expectedSize) {
        super(expectedSize);
    }

    public void addHandler(EnumFacing side, EnergyAcceptorWrapper handler) {
        addHandler(new SideHandler(handler, side));
    }

    @Override
    public Double sendToSingle(Double amount, Double resource) {
        SideHandler target = handlers.iterator().next();
        return amount > 0 ? target.handler.acceptEnergy(target.side, amount, false) : 0D;
    }

    @Override
    protected void acceptAmount(SideHandler handler, SplitInfo<Double> splitInfo, Double amount) {
        // Full acceptors participate in the split without a zero-sized external transfer.
        splitInfo.send(amount > 0 ? handler.handler.acceptEnergy(handler.side, amount, false) : 0D);
    }

    @Override
    protected Double simulate(SideHandler handler, Double energyToSend) {
        return handler.handler.acceptEnergy(handler.side, energyToSend, true);
    }

    public static class SideHandler {

        private final EnergyAcceptorWrapper handler;
        private final EnumFacing side;

        private SideHandler(EnergyAcceptorWrapper handler, EnumFacing side) {
            this.handler = handler;
            this.side = side;
        }
    }
}
