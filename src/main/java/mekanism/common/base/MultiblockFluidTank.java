package mekanism.common.base;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.ExtendedFluidHandlerUtils;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.tile.multiblock.TileEntityMultiblock;
import mekanism.common.util.MekanismUtils;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import javax.annotation.Nullable;

public abstract class MultiblockFluidTank<MULTIBLOCK extends TileEntityMultiblock> implements IExtendedFluidTank {

    protected final MULTIBLOCK multiblock;

    protected MultiblockFluidTank(MULTIBLOCK multiblock) {
        this.multiblock = multiblock;
    }

    public abstract void setFluid(@Nullable FluidStack stack);

    @Override
    public void setStack(@Nullable FluidStack stack) {
        if (!ExtendedFluidHandlerUtils.isEmpty(stack) && !isFluidValid(stack)) {
            throw new RuntimeException("Invalid fluid for tank: " + stack.getFluid().getName() + " " + stack.amount);
        }
        setStackUnchecked(stack);
    }

    @Override
    public void setStackUnchecked(@Nullable FluidStack stack) {
        setFluid(stack == null ? null : stack.copy());
        onContentsChanged();
    }

    @Override
    public boolean isFluidValid(@Nullable FluidStack stack) {
        return stack != null;
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
        FluidStack fluidStack = getFluid();
        if (fluidStack == null || fluidStack.amount <= 0) {
            return 0;
        } else if (amount <= 0) {
            if (action.execute()) {
                setFluid(null);
                onContentsChanged();
            }
            return 0;
        }
        int capacity = getCapacity();
        if (amount > capacity) {
            amount = capacity;
        }
        if (amount <= 0) {
            if (action.execute()) {
                setFluid(null);
                onContentsChanged();
            }
            return 0;
        } else if (fluidStack.amount == amount || action.simulate()) {
            return amount;
        }
        setFluid(new FluidStack(fluidStack, amount));
        onContentsChanged();
        return amount;
    }

    protected int growStackForInsert(int amount, Action action) {
        int current = getFluidAmount();
        if (current == 0) {
            return 0;
        } else if (amount > 0) {
            amount = Math.min(amount, getNeeded());
        }
        int newSize = setStackSizeForInsert(current + amount, action);
        return newSize - current;
    }

    protected int shrinkStackForExtract(int amount, Action action) {
        int current = getFluidAmount();
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
        FluidStack fluidStack = getFluid();
        if (fluidStack == null || fluidStack.amount <= 0) {
            return 0;
        } else if (amount <= 0) {
            if (action.execute()) {
                setFluid(null);
                if (insert) {
                    onContentsInserted();
                } else {
                    onContentsExtracted();
                }
            }
            return 0;
        }
        int capacity = getCapacity();
        if (amount > capacity) {
            amount = capacity;
        }
        if (amount <= 0) {
            if (action.execute()) {
                setFluid(null);
                if (insert) {
                    onContentsInserted();
                } else {
                    onContentsExtracted();
                }
            }
            return 0;
        } else if (fluidStack.amount == amount || action.simulate()) {
            return amount;
        }
        setFluid(new FluidStack(fluidStack, amount));
        if (insert) {
            onContentsInserted();
        } else {
            onContentsExtracted();
        }
        return amount;
    }

    @Override
    @Nullable
    public FluidStack insert(@Nullable FluidStack stack, Action action, AutomationType automationType) {
        if (multiblock.structure != null && !multiblock.getWorld().isRemote) {
            if (ExtendedFluidHandlerUtils.isEmpty(stack) || !isFluidValid(stack)) {
                return stack;
            }
            FluidStack fluidStack = getFluid();
            if (fluidStack != null && !fluidStack.isFluidEqual(stack)) {
                return stack;
            }
            int needed = getCapacity() - getFluidAmount();
            if (needed <= 0) {
                return stack;
            }
            int toAdd = Math.min(stack.amount, needed);
            if (action.execute()) {
                if (fluidStack == null) {
                    setFluid(new FluidStack(stack, toAdd));
                    onContentsInserted();
                } else {
                    growStackForInsert(toAdd, Action.EXECUTE);
                }
            }
            return stack.amount == toAdd ? null : new FluidStack(stack, stack.amount - toAdd);
        }
        return stack;
    }

    @Override
    public int fill(@Nullable FluidStack resource, boolean doFill) {
        if (ExtendedFluidHandlerUtils.isEmpty(resource)) {
            return 0;
        }
        FluidStack remainder = insert(resource, Action.get(doFill), AutomationType.EXTERNAL);
        return resource.amount - (remainder == null ? 0 : remainder.amount);
    }

    @Override
    @Nullable
    public FluidStack drain(int maxDrain, boolean doDrain) {
        return extract(maxDrain, Action.get(doDrain), AutomationType.EXTERNAL);
    }

    @Override
    @Nullable
    public FluidStack extract(int amount, Action action, AutomationType automationType) {
        if (multiblock.structure != null && !multiblock.getWorld().isRemote) {
            FluidStack fluidStack = getFluid();
            if (fluidStack == null || fluidStack.amount <= 0 || amount <= 0) {
                return null;
            }
            int used = Math.min(fluidStack.amount, amount);
            FluidStack drained = new FluidStack(fluidStack, used);
            if (action.execute()) {
                shrinkStackForExtract(used, Action.EXECUTE);
            }
            return drained;
        }
        return null;
    }

    @Override
    public int getFluidAmount() {
        if (multiblock.structure != null) {
            FluidStack fluid = getFluid();
            return fluid == null ? 0 : fluid.amount;
        }
        return 0;
    }

    @Override
    public FluidTankInfo getInfo() {
        return new FluidTankInfo(this);
    }

    @Override
    public void onContentsChanged() {
        MekanismUtils.saveChunk(multiblock);
        updateValveData();
    }
}
