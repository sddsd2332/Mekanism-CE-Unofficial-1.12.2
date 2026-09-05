package mekanism.common.capabilities.merged;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.IContentsListenerRegistry;
import mekanism.api.IContentsSnapshot;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTankInfo;
import mekanism.api.gas.IExtendedGasTank;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;

/**
 * Gas-only wrapper for merged tank behavior. Only one side of a merged fluid/gas tank can accept contents at a time.
 */
public class GasTankWrapper implements IExtendedGasTank, IContentsListenerRegistry, IContentsSnapshot {

    private final IExtendedGasTank internal;
    private final BooleanSupplier insertCheck;
    private final MergedTank mergedTank;

    public GasTankWrapper(MergedTank mergedTank, IExtendedGasTank internal, BooleanSupplier insertCheck) {
        this.mergedTank = mergedTank;
        this.internal = internal;
        this.insertCheck = insertCheck;
    }

    public MergedTank getMergedTank() {
        return mergedTank;
    }

    @Override
    @Nullable
    public GasStack getGas() {
        return internal.getGas();
    }

    @Override
    public void setStack(@Nullable GasStack stack) {
        internal.setStack(stack);
    }

    @Override
    public void setStackUnchecked(@Nullable GasStack stack) {
        internal.setStackUnchecked(stack);
    }

    @Override
    public void setStackUncheckedNoUpdate(@Nullable GasStack stack) {
        internal.setStackUncheckedNoUpdate(stack);
    }

    private boolean canInsert() {
        return insertCheck.getAsBoolean();
    }

    @Override
    @Nullable
    public GasStack insert(@Nullable GasStack stack, Action action, AutomationType automationType) {
        return canInsert() ? internal.insert(stack, action, automationType) : stack;
    }

    @Override
    @Nullable
    public GasStack extract(int amount, Action action, AutomationType automationType) {
        return internal.extract(amount, action, automationType);
    }

    @Override
    public boolean isValid(@Nullable GasStack stack) {
        return internal.isValid(stack);
    }

    @Override
    public boolean canReceive(@Nullable Gas gas) {
        return canInsert() && internal.canReceive(gas);
    }

    @Override
    public boolean canReceiveType(@Nullable Gas gas) {
        return canInsert() && internal.canReceiveType(gas);
    }

    @Override
    public boolean canDraw(@Nullable Gas gas) {
        return internal.canDraw(gas);
    }

    @Override
    public void onContentsChanged() {
        internal.onContentsChanged();
    }

    @Override
    public boolean addContentsListener(IContentsListener listener) {
        return listener != this && internal instanceof IContentsListenerRegistry registry && registry.addContentsListener(listener);
    }

    @Override
    public boolean removeContentsListener(IContentsListener listener) {
        return internal instanceof IContentsListenerRegistry registry && registry.removeContentsListener(listener);
    }

    @Override
    public int setStackSize(int amount, Action action) {
        return internal.setStackSize(amount, action);
    }

    @Override
    public int growStack(int amount, Action action) {
        return internal.growStack(amount, action);
    }

    @Override
    public int shrinkStack(int amount, Action action) {
        return internal.shrinkStack(amount, action);
    }

    @Override
    public boolean isEmpty() {
        return internal.isEmpty();
    }

    @Override
    public void setEmpty() {
        internal.setEmpty();
    }

    @Override
    public boolean isTypeEqual(@Nullable GasStack other) {
        return internal.isTypeEqual(other);
    }

    @Override
    public boolean isTypeEqual(@Nullable Gas other) {
        return internal.isTypeEqual(other);
    }

    @Override
    public int getNeeded() {
        return internal.getNeeded();
    }

    @Override
    public int getGasAmount() {
        return internal.getGasAmount();
    }

    @Override
    public int getStored() {
        return internal.getStored();
    }

    @Override
    public int getMaxGas() {
        return internal.getMaxGas();
    }

    @Override
    public int getCapacity() {
        return internal.getCapacity();
    }

    @Override
    public GasTankInfo getInfo() {
        return internal.getInfo();
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return internal.serializeNBT();
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        internal.deserializeNBT(nbt);
    }

    @Override
    public NBTTagCompound createContentsSnapshot() {
        return internal instanceof IContentsSnapshot snapshot ? snapshot.createContentsSnapshot() : internal.serializeNBT();
    }

    @Override
    public void restoreContentsSnapshot(NBTTagCompound snapshot) {
        if (internal instanceof IContentsSnapshot contentsSnapshot) {
            contentsSnapshot.restoreContentsSnapshot(snapshot);
        } else {
            internal.deserializeNBT(snapshot);
        }
    }
}
