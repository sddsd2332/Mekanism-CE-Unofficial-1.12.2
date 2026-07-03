package mekanism.api.gas;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface IMekanismGasHandler extends ISidedGasHandler, IContentsListener {

    default boolean canHandleGas() {
        return true;
    }

    @Nonnull
    List<IExtendedGasTank> getGasTanks(@Nullable EnumFacing side);

    default boolean canInsertGas(@Nullable EnumFacing side) {
        return side != null;
    }

    default boolean canExtractGas(@Nullable EnumFacing side) {
        return side != null;
    }

    @Nullable
    default IExtendedGasTank getGasTank(int tank, @Nullable EnumFacing side) {
        List<IExtendedGasTank> tanks = getGasTanks(side);
        return tank >= 0 && tank < tanks.size() ? tanks.get(tank) : null;
    }

    default int getCountGasTanks(@Nullable EnumFacing side) {
        return getGasTanks(side).size();
    }

    @Nullable
    default GasStack getGasInTank(int tank, @Nullable EnumFacing side) {
        IExtendedGasTank gasTank = getGasTank(tank, side);
        return gasTank == null ? null : gasTank.getGas();
    }

    default void setGasInTank(int tank, @Nullable GasStack stack, @Nullable EnumFacing side) {
        IExtendedGasTank gasTank = getGasTank(tank, side);
        if (gasTank != null) {
            gasTank.setStack(stack);
        }
    }

    default int getGasTankCapacity(int tank, @Nullable EnumFacing side) {
        IExtendedGasTank gasTank = getGasTank(tank, side);
        return gasTank == null ? 0 : gasTank.getCapacity();
    }

    default boolean isGasValid(int tank, @Nullable GasStack stack, @Nullable EnumFacing side) {
        IExtendedGasTank gasTank = getGasTank(tank, side);
        return gasTank != null && gasTank.isValid(stack);
    }

    @Nullable
    default GasStack insertGas(int tank, @Nullable GasStack stack, @Nullable EnumFacing side, Action action) {
        IExtendedGasTank gasTank = getGasTank(tank, side);
        return gasTank == null ? stack : gasTank.insert(stack, action, AutomationType.handler(side));
    }

    @Nullable
    default GasStack extractGas(int tank, int amount, @Nullable EnumFacing side, Action action) {
        IExtendedGasTank gasTank = getGasTank(tank, side);
        return gasTank == null ? null : gasTank.extract(amount, action, AutomationType.handler(side));
    }

    @Nullable
    default GasStack insertGas(@Nullable GasStack stack, @Nullable EnumFacing side, Action action) {
        return ExtendedGasHandlerUtils.insert(stack, side, this::getGasTanks, action, AutomationType.handler(side));
    }

    @Nullable
    default GasStack extractGas(int amount, @Nullable EnumFacing side, Action action) {
        return ExtendedGasHandlerUtils.extract(amount, side, this::getGasTanks, action, AutomationType.handler(side));
    }

    @Nullable
    default GasStack extractGas(@Nullable GasStack stack, @Nullable EnumFacing side, Action action) {
        return ExtendedGasHandlerUtils.extract(stack, side, this::getGasTanks, action, AutomationType.handler(side));
    }

    @Override
    default int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer) {
        if (side == null || ExtendedGasHandlerUtils.isEmpty(stack)) {
            return 0;
        }
        GasStack remainder = insertGas(stack, side, Action.get(doTransfer));
        return stack.amount - (remainder == null ? 0 : remainder.amount);
    }

    @Override
    @Nullable
    default GasStack drawGas(EnumFacing side, int amount, boolean doTransfer) {
        if (side == null || amount <= 0) {
            return null;
        }
        return extractGas(amount, side, Action.get(doTransfer));
    }

    @Override
    default boolean canReceiveGas(EnumFacing side, Gas type) {
        if (side == null || type == null || !canInsertGas(side)) {
            return false;
        }
        GasStack remainder = insertGas(new GasStack(type, 1), side, Action.SIMULATE);
        return remainder == null || remainder.amount < 1;
    }

    @Override
    default boolean canDrawGas(EnumFacing side, Gas type) {
        if (side == null || !canExtractGas(side)) {
            return false;
        }
        GasStack extracted = type == null ? extractGas(1, side, Action.SIMULATE) : extractGas(new GasStack(type, 1), side, Action.SIMULATE);
        return extracted != null && extracted.amount > 0;
    }

    /**
     * Legacy compat bridge for older callers that still expect {@link GasTankInfo} snapshots.
     * Prefer direct tank iteration through this handler instead.
     */
    @Deprecated
    @Nonnull
    @Override
    default GasTankInfo[] getTankInfo() {
        List<IExtendedGasTank> tanks = getGasTanks(null);
        return tanks.isEmpty() ? IGasHandler.NONE : tanks.stream().map(IExtendedGasTank::getInfo).toArray(GasTankInfo[]::new);
    }
}
