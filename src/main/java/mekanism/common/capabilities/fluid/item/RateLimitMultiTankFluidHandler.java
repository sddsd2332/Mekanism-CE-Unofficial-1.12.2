package mekanism.common.capabilities.fluid.item;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.capabilities.fluid.item.RateLimitFluidHandler.RateLimitFluidTank;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RateLimitMultiTankFluidHandler extends ItemStackMekanismFluidHandler {

    public static RateLimitMultiTankFluidHandler create(Collection<FluidTankSpec> fluidTanks) {
        return new RateLimitMultiTankFluidHandler(fluidTanks);
    }

    private final List<IExtendedFluidTank> tanks;

    private RateLimitMultiTankFluidHandler(Collection<FluidTankSpec> fluidTanks) {
        List<IExtendedFluidTank> tankProviders = new ArrayList<>();
        for (FluidTankSpec spec : fluidTanks) {
            tankProviders.add(new RateLimitFluidTank(spec.rate, spec.capacity, spec.canExtract,
                  (fluid, automationType) -> spec.canInsert.test(fluid, automationType, getStack()), spec.isValid, this));
        }
        tanks = Collections.unmodifiableList(tankProviders);
    }

    @Override
    protected List<IExtendedFluidTank> getInitialTanks() {
        return tanks;
    }
}
