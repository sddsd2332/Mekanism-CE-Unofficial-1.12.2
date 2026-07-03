package mekanism.common.capabilities.proxy;

import mekanism.api.Action;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasHandler;
import mekanism.api.gas.ISidedGasHandler;
import mekanism.common.capabilities.holder.IHolder;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;

public class ProxyGasHandler extends ProxyHandler implements IExtendedGasHandler {

    private final ISidedGasHandler gasHandler;

    public ProxyGasHandler(ISidedGasHandler gasHandler, @Nullable EnumFacing side, @Nullable IHolder holder) {
        super(side, holder);
        this.gasHandler = gasHandler;
    }

    @Override
    public int getCountGasTanks() {
        return gasHandler.getCountGasTanks(side);
    }

    @Nullable
    @Override
    public GasStack getGasInTank(int tank) {
        return gasHandler.getGasInTank(tank, side);
    }

    @Override
    public void setGasInTank(int tank, @Nullable GasStack stack) {
        if (!readOnly) {
            gasHandler.setGasInTank(tank, stack, side);
        }
    }

    @Override
    public int getGasTankCapacity(int tank) {
        return gasHandler.getGasTankCapacity(tank, side);
    }

    @Override
    public boolean isGasValid(int tank, @Nullable GasStack stack) {
        return !readOnly || gasHandler.isGasValid(tank, stack, side);
    }

    @Nullable
    @Override
    public GasStack insertGas(int tank, @Nullable GasStack stack, Action action) {
        return readOnlyInsert() ? stack : gasHandler.insertGas(tank, stack, side, action);
    }

    @Nullable
    @Override
    public GasStack extractGas(int tank, int amount, Action action) {
        return readOnlyExtract() ? null : gasHandler.extractGas(tank, amount, side, action);
    }

    @Nullable
    @Override
    public GasStack insertGas(@Nullable GasStack stack, Action action) {
        return readOnlyInsert() ? stack : gasHandler.insertGas(stack, side, action);
    }

    @Nullable
    @Override
    public GasStack extractGas(int amount, Action action) {
        return readOnlyExtract() ? null : gasHandler.extractGas(amount, side, action);
    }

    @Nullable
    @Override
    public GasStack extractGas(@Nullable GasStack stack, Action action) {
        return readOnlyExtract() ? null : gasHandler.extractGas(stack, side, action);
    }

    @Override
    public int receiveGas(EnumFacing ignored, GasStack stack, boolean doTransfer) {
        if (stack == null || readOnlyInsert()) {
            return 0;
        }
        GasStack remainder = insertGas(stack, Action.get(doTransfer));
        return stack.amount - (remainder == null ? 0 : remainder.amount);
    }

    @Nullable
    @Override
    public GasStack drawGas(EnumFacing ignored, int amount, boolean doTransfer) {
        return readOnlyExtract() ? null : extractGas(amount, Action.get(doTransfer));
    }

    @Override
    public boolean canReceiveGas(EnumFacing ignored, Gas type) {
        if (readOnlyInsert() || type == null) {
            return false;
        }
        GasStack remainder = insertGas(new GasStack(type, 1), Action.SIMULATE);
        return remainder == null || remainder.amount < 1;
    }

    @Override
    public boolean canDrawGas(EnumFacing ignored, Gas type) {
        if (readOnlyExtract()) {
            return false;
        }
        GasStack extracted = type == null ? extractGas(1, Action.SIMULATE) : extractGas(new GasStack(type, 1), Action.SIMULATE);
        return extracted != null && extracted.amount > 0;
    }
}
