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

    public void reset(FluidStack type) {
        super.reset();
        this.extra = type;
    }

    @Override
    public Integer sendToSingle(Integer amount, FluidStack resource) {
        return resource == null || amount <= 0 ? 0 : handlers.iterator().next().fill(FluidContainerUtils.copyWithAmount(resource, amount), true);
    }

    @Override
    protected void acceptAmount(IFluidHandler handler, SplitInfo<Integer> splitInfo, Integer amount) {
        splitInfo.send(amount > 0 ? handler.fill(FluidContainerUtils.copyWithAmount(extra, amount), true) : 0);
    }

    @Override
    protected Integer simulate(IFluidHandler handler, FluidStack fluidStack) {
        return handler.fill(fluidStack.copy(), false);
    }
}
