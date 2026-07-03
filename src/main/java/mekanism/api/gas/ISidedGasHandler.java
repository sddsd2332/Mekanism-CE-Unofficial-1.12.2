package mekanism.api.gas;

import mekanism.api.Action;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;

public interface ISidedGasHandler extends IExtendedGasHandler {

    @Nullable
    default EnumFacing getGasSideFor() {
        return null;
    }

    int getCountGasTanks(@Nullable EnumFacing side);

    @Override
    default int getCountGasTanks() {
        return getCountGasTanks(getGasSideFor());
    }

    @Nullable
    GasStack getGasInTank(int tank, @Nullable EnumFacing side);

    @Override
    @Nullable
    default GasStack getGasInTank(int tank) {
        return getGasInTank(tank, getGasSideFor());
    }

    void setGasInTank(int tank, @Nullable GasStack stack, @Nullable EnumFacing side);

    @Override
    default void setGasInTank(int tank, @Nullable GasStack stack) {
        setGasInTank(tank, stack, getGasSideFor());
    }

    int getGasTankCapacity(int tank, @Nullable EnumFacing side);

    @Override
    default int getGasTankCapacity(int tank) {
        return getGasTankCapacity(tank, getGasSideFor());
    }

    boolean isGasValid(int tank, @Nullable GasStack stack, @Nullable EnumFacing side);

    @Override
    default boolean isGasValid(int tank, @Nullable GasStack stack) {
        return isGasValid(tank, stack, getGasSideFor());
    }

    @Nullable
    GasStack insertGas(int tank, @Nullable GasStack stack, @Nullable EnumFacing side, Action action);

    @Override
    @Nullable
    default GasStack insertGas(int tank, @Nullable GasStack stack, Action action) {
        return insertGas(tank, stack, getGasSideFor(), action);
    }

    @Nullable
    GasStack extractGas(int tank, int amount, @Nullable EnumFacing side, Action action);

    @Override
    @Nullable
    default GasStack extractGas(int tank, int amount, Action action) {
        return extractGas(tank, amount, getGasSideFor(), action);
    }

    @Nullable
    default GasStack insertGas(@Nullable GasStack stack, @Nullable EnumFacing side, Action action) {
        return ExtendedGasHandlerUtils.insert(stack, action, () -> getCountGasTanks(side), tank -> getGasInTank(tank, side),
              (tank, s, a) -> insertGas(tank, s, side, a));
    }

    @Nullable
    default GasStack extractGas(int amount, @Nullable EnumFacing side, Action action) {
        return ExtendedGasHandlerUtils.extract(amount, action, () -> getCountGasTanks(side), tank -> getGasInTank(tank, side),
              (tank, a, act) -> extractGas(tank, a, side, act));
    }

    @Nullable
    default GasStack extractGas(@Nullable GasStack stack, @Nullable EnumFacing side, Action action) {
        return ExtendedGasHandlerUtils.extract(stack, action, () -> getCountGasTanks(side), tank -> getGasInTank(tank, side),
              (tank, a, act) -> extractGas(tank, a, side, act));
    }
}
