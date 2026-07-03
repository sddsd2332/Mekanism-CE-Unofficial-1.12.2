package mekanism.api.gas;

import mekanism.api.Action;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface IExtendedGasHandler extends IGasHandler {

    int getCountGasTanks();

    @Nullable
    GasStack getGasInTank(int tank);

    void setGasInTank(int tank, @Nullable GasStack stack);

    int getGasTankCapacity(int tank);

    boolean isGasValid(int tank, @Nullable GasStack stack);

    @Nullable
    GasStack insertGas(int tank, @Nullable GasStack stack, Action action);

    @Nullable
    GasStack extractGas(int tank, int amount, Action action);

    @Nullable
    default GasStack insertGas(@Nullable GasStack stack, Action action) {
        return ExtendedGasHandlerUtils.insert(stack, action, this::getCountGasTanks, this::getGasInTank, this::insertGas);
    }

    @Nullable
    default GasStack extractGas(int amount, Action action) {
        return ExtendedGasHandlerUtils.extract(amount, action, this::getCountGasTanks, this::getGasInTank, this::extractGas);
    }

    @Nullable
    default GasStack extractGas(@Nullable GasStack stack, Action action) {
        return ExtendedGasHandlerUtils.extract(stack, action, this::getCountGasTanks, this::getGasInTank, this::extractGas);
    }

    @Override
    default int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer) {
        if (ExtendedGasHandlerUtils.isEmpty(stack)) {
            return 0;
        }
        GasStack remainder = insertGas(stack, Action.get(doTransfer));
        return stack.amount - (remainder == null ? 0 : remainder.amount);
    }

    @Override
    @Nullable
    default GasStack drawGas(EnumFacing side, int amount, boolean doTransfer) {
        if (amount <= 0) {
            return null;
        }
        return extractGas(amount, Action.get(doTransfer));
    }

    @Override
    default boolean canReceiveGas(EnumFacing side, Gas type) {
        if (type == null) {
            return false;
        }
        GasStack remainder = insertGas(new GasStack(type, 1), Action.SIMULATE);
        return remainder == null || remainder.amount < 1;
    }

    @Override
    default boolean canDrawGas(EnumFacing side, Gas type) {
        GasStack extracted = type == null ? extractGas(1, Action.SIMULATE) : extractGas(new GasStack(type, 1), Action.SIMULATE);
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
        int tanks = getCountGasTanks();
        if (tanks == 0) {
            return NONE;
        }
        GasTankInfo[] tankInfo = new GasTankInfo[tanks];
        for (int tank = 0; tank < tanks; tank++) {
            GasStack stored = getGasInTank(tank);
            int capacity = getGasTankCapacity(tank);
            tankInfo[tank] = new GasTankInfo() {
                @Nullable
                @Override
                public GasStack getGas() {
                    return stored;
                }

                @Override
                public int getStored() {
                    return stored == null ? 0 : stored.amount;
                }

                @Override
                public int getMaxGas() {
                    return capacity;
                }
            };
        }
        return tankInfo;
    }
}
