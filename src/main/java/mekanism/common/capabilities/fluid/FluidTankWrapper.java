package mekanism.common.capabilities.fluid;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.capabilities.merged.MergedTank;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;

public class FluidTankWrapper implements IExtendedFluidTank {

    private final IExtendedFluidTank internal;
    private final BooleanSupplier insertCheck;
    private final MergedTank mergedTank;

    public FluidTankWrapper(MergedTank mergedTank, IExtendedFluidTank internal, BooleanSupplier insertCheck) {
        this.mergedTank = mergedTank;
        this.internal = internal;
        this.insertCheck = insertCheck;
    }

    public MergedTank getMergedTank() {
        return mergedTank;
    }

    @Override
    public void setStack(@Nullable FluidStack stack) {
        internal.setStack(stack);
    }

    @Override
    public void setStackUnchecked(@Nullable FluidStack stack) {
        internal.setStackUnchecked(stack);
    }

    @Override
    @Nullable
    public FluidStack insert(@Nullable FluidStack stack, Action action, AutomationType automationType) {
        return canInsert() ? internal.insert(stack, action, automationType) : stack;
    }

    private boolean canInsert() {
        return insertCheck.getAsBoolean();
    }

    @Override
    @Nullable
    public FluidStack extract(int amount, Action action, AutomationType automationType) {
        return internal.extract(amount, action, automationType);
    }

    @Override
    public void onContentsChanged() {
        internal.onContentsChanged();
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
    public boolean isFluidEqual(@Nullable FluidStack other) {
        return internal.isFluidEqual(other);
    }

    @Override
    public int getNeeded() {
        return internal.getNeeded();
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
    @Nullable
    public FluidStack getFluid() {
        return internal.getFluid();
    }

    @Override
    public int getFluidAmount() {
        return internal.getFluidAmount();
    }

    @Override
    public int getCapacity() {
        return internal.getCapacity();
    }

    @Override
    public boolean isFluidValid(@Nullable FluidStack stack) {
        return internal.isFluidValid(stack);
    }
}
