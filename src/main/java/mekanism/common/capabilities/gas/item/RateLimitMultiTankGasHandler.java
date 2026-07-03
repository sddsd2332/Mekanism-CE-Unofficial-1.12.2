package mekanism.common.capabilities.gas.item;

import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.capabilities.gas.item.RateLimitGasHandler.RateLimitGasTank;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RateLimitMultiTankGasHandler extends ItemStackMekanismGasHandler {

    public static RateLimitMultiTankGasHandler create(Collection<GasTankSpec> gasTanks) {
        return new RateLimitMultiTankGasHandler(gasTanks);
    }

    private final List<IExtendedGasTank> tanks;

    private RateLimitMultiTankGasHandler(Collection<GasTankSpec> gasTanks) {
        List<IExtendedGasTank> tankProviders = new ArrayList<>();
        for (GasTankSpec spec : gasTanks) {
            tankProviders.add(new RateLimitGasTank(spec.rate, spec.capacity, spec.canExtract,
                  (gas, automationType) -> spec.canInsert.test(gas, automationType, getStack()), spec.isValid, this));
        }
        tanks = Collections.unmodifiableList(tankProviders);
    }

    @Override
    protected List<IExtendedGasTank> getInitialTanks() {
        return tanks;
    }
}
