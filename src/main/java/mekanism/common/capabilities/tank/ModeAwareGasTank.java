package mekanism.common.capabilities.tank;

import mekanism.api.gas.Gas;
import mekanism.common.capabilities.gas.BasicGasTank;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public class ModeAwareGasTank extends BasicGasTank {

    public ModeAwareGasTank(int max, Predicate<Gas> validator, BooleanSupplier canReceive, BooleanSupplier canDraw) {
        super(max, (gas, automationType) -> canDraw.getAsBoolean(), (gas, automationType) -> canReceive.getAsBoolean(), validator, null);
    }
}
