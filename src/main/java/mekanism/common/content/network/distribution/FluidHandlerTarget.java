package mekanism.common.content.network.distribution;

import mekanism.common.lib.distribution.SplitInfo;
import mekanism.common.lib.distribution.Target;
import mekanism.common.util.FluidContainerUtils;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class FluidHandlerTarget extends Target<IFluidHandler, Integer, FluidStack> {

    public FluidHandlerTarget(FluidStack type) {
        this.extra = type;
    }

    public FluidHandlerTarget(FluidStack type, int expectedSize) {
        super(expectedSize);
        this.extra = type;
    }

    @Override
    protected void acceptAmount(IFluidHandler handler, SplitInfo<Integer> splitInfo, Integer amount) {
        splitInfo.send(handler.fill(FluidContainerUtils.copyWithAmount(extra, amount), true));
    }

    @Override
    protected Integer simulate(IFluidHandler handler, FluidStack fluidStack) {
        return handler.fill(fluidStack.copy(), false);
    }
}
