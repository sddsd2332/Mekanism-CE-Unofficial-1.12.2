package mekanism.common.capabilities.gas;

import mekanism.api.Action;
import mekanism.api.IContentsListener;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.gas.IMekanismGasHandler;
import mekanism.common.capabilities.DynamicHandler;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class DynamicGasHandler extends DynamicHandler<IExtendedGasTank> implements IMekanismGasHandler {

    public DynamicGasHandler(Function<EnumFacing, List<IExtendedGasTank>> tankSupplier, Predicate<EnumFacing> canExtract, Predicate<EnumFacing> canInsert,
          @Nullable IContentsListener listener) {
        super(tankSupplier, canExtract, canInsert, listener);
    }

    public DynamicGasHandler(Function<EnumFacing, List<IExtendedGasTank>> tankSupplier, InteractPredicate canExtract, InteractPredicate canInsert,
          @Nullable IContentsListener listener) {
        super(tankSupplier, canExtract, canInsert, listener);
    }

    @Override
    public List<IExtendedGasTank> getGasTanks(@Nullable EnumFacing side) {
        return containerSupplier.apply(side);
    }

    @Override
    public boolean canInsertGas(@Nullable EnumFacing side) {
        List<IExtendedGasTank> gasTanks = getGasTanks(side);
        for (int tank = 0; tank < gasTanks.size(); tank++) {
            if (canInsert.test(tank, side)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canExtractGas(@Nullable EnumFacing side) {
        List<IExtendedGasTank> gasTanks = getGasTanks(side);
        for (int tank = 0; tank < gasTanks.size(); tank++) {
            if (canExtract.test(tank, side)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Nullable
    public GasStack insertGas(int tank, @Nullable GasStack stack, @Nullable EnumFacing side, Action action) {
        return canInsert.test(tank, side) ? IMekanismGasHandler.super.insertGas(tank, stack, side, action) : stack;
    }

    @Override
    @Nullable
    public GasStack extractGas(int tank, int amount, @Nullable EnumFacing side, Action action) {
        return canExtract.test(tank, side) ? IMekanismGasHandler.super.extractGas(tank, amount, side, action) : null;
    }

    @Override
    @Nullable
    public GasStack insertGas(@Nullable GasStack stack, @Nullable EnumFacing side, Action action) {
        return canInsertGas(side) ? IMekanismGasHandler.super.insertGas(stack, side, action) : stack;
    }

    @Override
    @Nullable
    public GasStack extractGas(int amount, @Nullable EnumFacing side, Action action) {
        return canExtractGas(side) ? IMekanismGasHandler.super.extractGas(amount, side, action) : null;
    }

    @Override
    @Nullable
    public GasStack extractGas(@Nullable GasStack stack, @Nullable EnumFacing side, Action action) {
        return canExtractGas(side) ? IMekanismGasHandler.super.extractGas(stack, side, action) : null;
    }
}
