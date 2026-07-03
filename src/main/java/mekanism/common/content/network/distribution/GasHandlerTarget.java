package mekanism.common.content.network.distribution;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasHandler;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.lib.distribution.SplitInfo;
import mekanism.common.lib.distribution.Target;
import net.minecraft.util.EnumFacing;

public class GasHandlerTarget extends Target<GasHandlerTarget.SideHandler, Integer, GasStack> {

    public GasHandlerTarget(GasStack type) {
        this.extra = type;
    }

    public GasHandlerTarget(GasStack type, int expectedSize) {
        super(expectedSize);
        this.extra = type;
    }

    public void addHandler(EnumFacing side, IGasHandler handler) {
        addHandler(new SideHandler(handler, side));
    }

    @Override
    protected void acceptAmount(SideHandler handler, SplitInfo<Integer> splitInfo, Integer amount) {
        if (extra == null || extra.getGas() == null || amount == null || amount <= 0) {
            return;
        }
        splitInfo.send(GasInventorySlot.insertGas(handler.handler, handler.side, new GasStack(extra.getGas(), amount), true));
    }

    @Override
    protected Integer simulate(SideHandler handler, GasStack gasStack) {
        return GasInventorySlot.insertGas(handler.handler, handler.side, gasStack, false);
    }

    public static class SideHandler {

        private final IGasHandler handler;
        private final EnumFacing side;

        private SideHandler(IGasHandler handler, EnumFacing side) {
            this.handler = handler;
            this.side = side;
        }
    }
}
