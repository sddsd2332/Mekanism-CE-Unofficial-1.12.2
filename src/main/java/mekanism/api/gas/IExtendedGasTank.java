package mekanism.api.gas;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.NBTConstants;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.Nullable;

public interface IExtendedGasTank extends IGasTank, GasTankInfo, INBTSerializable<NBTTagCompound>, IContentsListener {

    void setStack(@Nullable GasStack stack);

    void setStackUnchecked(@Nullable GasStack stack);

    /** Listener-free server transaction write. The caller must validate first and notify after commit. */
    default void setStackUncheckedNoUpdate(@Nullable GasStack stack) {
        throw new UnsupportedOperationException("This tank does not support atomic recipe writes");
    }

    boolean isValid(@Nullable GasStack stack);

    @Nullable
    default GasStack getStack() {
        return getGas();
    }

    @Nullable
    default GasStack insert(@Nullable GasStack stack, Action action, AutomationType automationType) {
        if (stack == null || stack.amount <= 0 || !isValid(stack)) {
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
                    growStack(toAdd, action);
                } else {
                    setStack(new GasStack(stack.getGas(), toAdd));
                }
            }
            return stack.amount == toAdd ? null : new GasStack(stack.getGas(), stack.amount - toAdd);
        }
        return stack;
    }

    @Nullable
    default GasStack extract(int amount, Action action, AutomationType automationType) {
        if (isEmpty() || amount < 1) {
            return null;
        }
        GasStack ret = new GasStack(getGas().getGas(), Math.min(getGasAmount(), amount));
        if (ret.amount > 0 && action.execute()) {
            shrinkStack(ret.amount, action);
        }
        return ret.amount <= 0 ? null : ret;
    }

    default int setStackSize(int amount, Action action) {
        if (isEmpty()) {
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
        if (getGasAmount() == amount || action.simulate()) {
            return amount;
        }
        setStack(new GasStack(getGas().getGas(), amount));
        return amount;
    }

    default int growStack(int amount, Action action) {
        int current = getGasAmount();
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
        GasStack stored = getGas();
        return stored == null || stored.amount <= 0;
    }

    default void setEmpty() {
        setStack(null);
    }

    default Gas getType() {
        GasStack stack = getGas();
        return stack == null ? null : stack.getGas();
    }

    default boolean isTypeEqual(@Nullable GasStack other) {
        GasStack stored = getGas();
        return stored != null && stored.isGasEqual(other);
    }

    default boolean isTypeEqual(@Nullable Gas other) {
        GasStack stored = getGas();
        return stored != null && stored.isGasEqual(other);
    }

    default int getCapacity() {
        return getMaxGas();
    }

    @Override
    default int getStored() {
        return getGasAmount();
    }

    @Override
    default GasTankInfo getInfo() {
        return this;
    }

    @Override
    default NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        if (!isEmpty()) {
            nbt.setTag(NBTConstants.STORED, getGas().write(new NBTTagCompound()));
        }
        return nbt;
    }

    @Override
    default void deserializeNBT(NBTTagCompound nbt) {
        if (nbt.hasKey(NBTConstants.STORED)) {
            setStackUnchecked(GasStack.readFromNBT(nbt.getCompoundTag(NBTConstants.STORED)));
        } else {
            setEmpty();
        }
    }

    @Override
    default int input(@Nullable GasStack resource, boolean input) {
        if (resource == null || resource.amount <= 0) {
            return 0;
        }
        GasStack remainder = insert(resource, Action.get(input), AutomationType.EXTERNAL);
        return resource.amount - (remainder == null ? 0 : remainder.amount);
    }

    @Override
    @Nullable
    default GasStack output(int maxOutput, boolean output) {
        return extract(maxOutput, Action.get(output), AutomationType.EXTERNAL);
    }
}
