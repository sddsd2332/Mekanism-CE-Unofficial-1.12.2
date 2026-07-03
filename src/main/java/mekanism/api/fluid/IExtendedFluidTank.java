package mekanism.api.fluid;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.NBTConstants;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

import javax.annotation.Nullable;

public interface IExtendedFluidTank extends IFluidTank, INBTSerializable<NBTTagCompound>, IContentsListener {

    void setStack(@Nullable FluidStack stack);

    void setStackUnchecked(@Nullable FluidStack stack);

    boolean isFluidValid(@Nullable FluidStack stack);

    @Nullable
    default FluidStack insert(@Nullable FluidStack stack, Action action, AutomationType automationType) {
        if (ExtendedFluidHandlerUtils.isEmpty(stack) || !isFluidValid(stack)) {
            return stack;
        }
        int needed = getNeeded();
        if (needed <= 0) {
            return stack;
        }
        FluidStack stored = getFluid();
        boolean sameType = false;
        if (ExtendedFluidHandlerUtils.isEmpty(stored) || (sameType = stored.isFluidEqual(stack))) {
            int toAdd = Math.min(stack.amount, needed);
            if (action.execute()) {
                if (sameType) {
                    growStack(toAdd, action);
                } else {
                    setStack(new FluidStack(stack, toAdd));
                }
            }
            return stack.amount == toAdd ? null : new FluidStack(stack, stack.amount - toAdd);
        }
        return stack;
    }

    @Nullable
    default FluidStack extract(int amount, Action action, AutomationType automationType) {
        FluidStack stored = getFluid();
        if (ExtendedFluidHandlerUtils.isEmpty(stored) || amount < 1) {
            return null;
        }
        FluidStack ret = new FluidStack(stored, Math.min(getFluidAmount(), amount));
        if (!ExtendedFluidHandlerUtils.isEmpty(ret) && action.execute()) {
            shrinkStack(ret.amount, action);
        }
        return ExtendedFluidHandlerUtils.emptyToNull(ret);
    }

    default int setStackSize(int amount, Action action) {
        FluidStack stored = getFluid();
        if (ExtendedFluidHandlerUtils.isEmpty(stored)) {
            return 0;
        } else if (amount <= 0) {
            if (action.execute()) {
                setEmpty();
            }
            return 0;
        }
        int maxStackSize = getCapacity();
        if (amount > maxStackSize) {
            amount = maxStackSize;
        }
        if (getFluidAmount() == amount || action.simulate()) {
            return amount;
        }
        setStack(new FluidStack(stored, amount));
        return amount;
    }

    default int growStack(int amount, Action action) {
        int current = getFluidAmount();
        if (current == 0) {
            return 0;
        } else if (amount > 0) {
            amount = Math.min(amount, getNeeded());
        }
        int newSize = setStackSize(current + amount, action);
        return newSize - current;
    }

    default int shrinkStack(int amount, Action action) {
        return -growStack(-amount, action);
    }

    default boolean isEmpty() {
        return ExtendedFluidHandlerUtils.isEmpty(getFluid());
    }

    default void setEmpty() {
        setStack(null);
    }

    default boolean isFluidEqual(@Nullable FluidStack other) {
        FluidStack stored = getFluid();
        return stored != null && stored.isFluidEqual(other);
    }

    default int getNeeded() {
        return Math.max(0, getCapacity() - getFluidAmount());
    }

    @Override
    default int getFluidAmount() {
        FluidStack fluid = getFluid();
        return ExtendedFluidHandlerUtils.isEmpty(fluid) ? 0 : fluid.amount;
    }

    @Override
    default NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        if (!isEmpty()) {
            nbt.setTag(NBTConstants.STORED, getFluid().writeToNBT(new NBTTagCompound()));
        }
        return nbt;
    }

    @Override
    default void deserializeNBT(NBTTagCompound nbt) {
        if (nbt.hasKey(NBTConstants.STORED, NBT.TAG_COMPOUND)) {
            setStackUnchecked(FluidStack.loadFluidStackFromNBT(nbt.getCompoundTag(NBTConstants.STORED)));
        } else {
            setEmpty();
        }
    }

    @Override
    default FluidTankInfo getInfo() {
        return new FluidTankInfo(this);
    }

    @Override
    default int fill(FluidStack stack, boolean doFill) {
        if (ExtendedFluidHandlerUtils.isEmpty(stack)) {
            return 0;
        }
        FluidStack remainder = insert(stack, Action.get(doFill), AutomationType.EXTERNAL);
        return stack.amount - (remainder == null ? 0 : remainder.amount);
    }

    @Nullable
    default FluidStack drain(@Nullable FluidStack stack, boolean doDrain) {
        if (!isEmpty() && stack != null && getFluid().isFluidEqual(stack)) {
            return extract(stack.amount, Action.get(doDrain), AutomationType.EXTERNAL);
        }
        return null;
    }

    @Override
    @Nullable
    default FluidStack drain(int amount, boolean doDrain) {
        return extract(amount, Action.get(doDrain), AutomationType.EXTERNAL);
    }
}
