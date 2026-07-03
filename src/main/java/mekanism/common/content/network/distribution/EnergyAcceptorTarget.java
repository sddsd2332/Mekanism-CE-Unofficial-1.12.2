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
    protected void acceptAmount(SideHandler handler, SplitInfo<Double> splitInfo, Double amount) {
        splitInfo.send(handler.handler.acceptEnergy(handler.side, amount, false));
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
