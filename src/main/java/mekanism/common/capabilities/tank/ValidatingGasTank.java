package mekanism.common.capabilities.tank;

import mekanism.api.Action;
import mekanism.api.gas.Gas;
import mekanism.common.capabilities.gas.BasicGasTank;

import java.util.function.Predicate;

public class ValidatingGasTank extends BasicGasTank {

    private int capacity;

    public ValidatingGasTank(int max, Predicate<Gas> validator) {
        super(max, alwaysTrueBi, alwaysTrueBi, validator, null);
        capacity = max;
    }

    @Override
    public int getCapacity() {
        return capacity;
    }

    @Override
    public void setMaxGas(int capacity) {
        this.capacity = Math.max(0, capacity);
        if (getGasAmount() > this.capacity) {
            setStackSize(this.capacity, Action.EXECUTE);
        }
    }
}
