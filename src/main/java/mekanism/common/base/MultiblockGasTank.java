package mekanism.common.base;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTankInfo;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.tile.multiblock.TileEntityMultiblock;
import mekanism.common.util.MekanismUtils;

import javax.annotation.Nullable;

public abstract class MultiblockGasTank<MULTIBLOCK extends TileEntityMultiblock> implements IExtendedGasTank {

    protected final MULTIBLOCK multiblock;

    protected MultiblockGasTank(MULTIBLOCK multiblock) {
        this.multiblock = multiblock;
    }

    public abstract void setGas(@Nullable GasStack stack);

    @Override
    public void setStack(@Nullable GasStack stack) {
        if (stack != null && stack.amount > 0 && !isValid(stack)) {
            throw new RuntimeException("Invalid gas for tank: " + stack.getGas().getName() + " " + stack.amount);
        }
        setStackUnchecked(stack);
    }

    @Override
    public void setStackUnchecked(@Nullable GasStack stack) {
        setGas(stack == null ? null : stack.copy());
        onContentsChanged();
    }

    @Override
    public boolean isValid(@Nullable GasStack stack) {
        return stack != null && stack.getGas() != null && isValid(stack.getGas());
    }

    public boolean isValid(@Nullable Gas gas) {
        return gas != null;
    }

    @Override
    public boolean canReceive(@Nullable Gas gas) {
        return isValid(gas) && getNeeded() > 0 && canReceiveType(gas);
    }

    @Override
    public boolean canReceiveType(@Nullable Gas gas) {
        GasStack stored = getGas();
        return isValid(gas) && (stored == null || gas == stored.getGas());
    }

    @Override
    public boolean canDraw(@Nullable Gas gas) {
        GasStack stored = getGas();
        return stored != null && (gas == null || gas == stored.getGas());
    }

    protected abstract void updateValveData();

    protected void onContentsInserted() {
        MekanismUtils.saveChunk(multiblock);
        updateValveData();
    }

    protected void onContentsExtracted() {
        MekanismUtils.saveChunk(multiblock);
        multiblock.sendPacketToRenderer();
    }

    @Override
    public int setStackSize(int amount, Action action) {
        GasStack stored = getGas();
        if (stored == null || stored.amount <= 0) {
            return 0;
        } else if (amount <= 0) {
            if (action.execute()) {
                setGas(null);
                onContentsChanged();
            }
            return 0;
        }
        int capacity = getMaxGas();
        if (amount > capacity) {
            amount = capacity;
        }
        if (amount <= 0) {
            if (action.execute()) {
                setGas(null);
                onContentsChanged();
            }
            return 0;
        } else if (stored.amount == amount || action.simulate()) {
            return amount;
        }
        setGas(stored.copy().withAmount(amount));
        onContentsChanged();
        return amount;
    }

    protected int growStackForInsert(int amount, Action action) {
        int current = getGasAmount();
        if (current == 0) {
            return 0;
        } else if (amount > 0) {
            amount = Math.min(amount, getNeeded());
        }
        int newSize = setStackSizeForInsert(current + amount, action);
        return newSize - current;
    }

    protected int shrinkStackForExtract(int amount, Action action) {
        int current = getGasAmount();
        if (current == 0 || amount <= 0) {
            return 0;
        }
        int newSize = setStackSizeForExtract(current - amount, action);
        return current - newSize;
    }

    protected int setStackSizeForInsert(int amount, Action action) {
        return setStackSize(amount, action, true);
    }

    protected int setStackSizeForExtract(int amount, Action action) {
        return setStackSize(amount, action, false);
    }

    private int setStackSize(int amount, Action action, boolean insert) {
        GasStack stored = getGas();
        if (stored == null || stored.amount <= 0) {
            return 0;
        } else if (amount <= 0) {
            if (action.execute()) {
                setGas(null);
                if (insert) {
                    onContentsInserted();
                } else {
                    onContentsExtracted();
                }
            }
            return 0;
        }
        int capacity = getMaxGas();
        if (amount > capacity) {
            amount = capacity;
        }
        if (amount <= 0) {
            if (action.execute()) {
                setGas(null);
                if (insert) {
                    onContentsInserted();
                } else {
                    onContentsExtracted();
                }
            }
            return 0;
        } else if (stored.amount == amount || action.simulate()) {
            return amount;
        }
        setGas(stored.copy().withAmount(amount));
        if (insert) {
            onContentsInserted();
        } else {
            onContentsExtracted();
        }
        return amount;
    }

    @Override
    @Nullable
    public GasStack insert(@Nullable GasStack stack, Action action, AutomationType automationType) {
        if (multiblock.structure == null || multiblock.getWorld().isRemote || stack == null || stack.amount <= 0 || !isValid(stack)) {
            return stack;
        }
        int needed = getNeeded();
        if (needed <= 0) {
            return stack;
        }
        GasStack stored = getGas();
        boolean sameType = false;
        if (stored == null || (sameType = stored.isGasEqual(stack))) {
            int toAdd = Math.min(stack.amount, needed);
            if (action.execute()) {
                if (sameType) {
                    growStackForInsert(toAdd, Action.EXECUTE);
                } else {
                    setGas(stack.copy().withAmount(toAdd));
                    onContentsInserted();
                }
            }
            return stack.amount == toAdd ? null : stack.copy().withAmount(stack.amount - toAdd);
        }
        return stack;
    }

    @Override
    @Nullable
    public GasStack extract(int amount, Action action, AutomationType automationType) {
        if (multiblock.structure == null || multiblock.getWorld().isRemote || amount <= 0) {
            return null;
        }
        GasStack stored = getGas();
        if (stored == null || stored.amount <= 0) {
            return null;
        }
        GasStack extracted = stored.copy().withAmount(Math.min(stored.amount, amount));
        if (extracted.amount > 0 && action.execute()) {
            shrinkStackForExtract(extracted.amount, Action.EXECUTE);
        }
        return extracted;
    }

    @Override
    public int getGasAmount() {
        if (multiblock.structure != null) {
            GasStack gas = getGas();
            return gas == null ? 0 : gas.amount;
        }
        return 0;
    }

    @Override
    public GasTankInfo getInfo() {
        return new GasTankInfo() {
            @org.jetbrains.annotations.Nullable
            @Override
            public GasStack getGas() {
                return MultiblockGasTank.this.getGas();
            }

            @Override
            public int getStored() {
                return MultiblockGasTank.this.getGasAmount();
            }

            @Override
            public int getMaxGas() {
                return MultiblockGasTank.this.getMaxGas();
            }
        };
    }

    @Override
    public void onContentsChanged() {
        MekanismUtils.saveChunk(multiblock);
        updateValveData();
    }
}
