package mekanism.api.fluid;

import mekanism.api.Action;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

public interface ISidedFluidHandler extends IExtendedFluidHandler {

    @Nullable
    default EnumFacing getFluidSideFor() {
        return null;
    }

    int getTanks(@Nullable EnumFacing side);

    @Override
    default int getTanks() {
        return getTanks(getFluidSideFor());
    }

    @Nullable
    FluidStack getFluidInTank(int tank, @Nullable EnumFacing side);

    @Override
    @Nullable
    default FluidStack getFluidInTank(int tank) {
        return getFluidInTank(tank, getFluidSideFor());
    }

    void setFluidInTank(int tank, @Nullable FluidStack stack, @Nullable EnumFacing side);

    @Override
    default void setFluidInTank(int tank, @Nullable FluidStack stack) {
        setFluidInTank(tank, stack, getFluidSideFor());
    }

    int getTankCapacity(int tank, @Nullable EnumFacing side);

    @Override
    default int getTankCapacity(int tank) {
        return getTankCapacity(tank, getFluidSideFor());
    }

    boolean isFluidValid(int tank, @Nullable FluidStack stack, @Nullable EnumFacing side);

    @Override
    default boolean isFluidValid(int tank, @Nullable FluidStack stack) {
        return isFluidValid(tank, stack, getFluidSideFor());
    }

    @Nullable
    FluidStack insertFluid(int tank, @Nullable FluidStack stack, @Nullable EnumFacing side, Action action);

    @Override
    @Nullable
    default FluidStack insertFluid(int tank, @Nullable FluidStack stack, Action action) {
        return insertFluid(tank, stack, getFluidSideFor(), action);
    }

    @Nullable
    FluidStack extractFluid(int tank, int amount, @Nullable EnumFacing side, Action action);

    @Override
    @Nullable
    default FluidStack extractFluid(int tank, int amount, Action action) {
        return extractFluid(tank, amount, getFluidSideFor(), action);
    }

    @Nullable
    default FluidStack insertFluid(@Nullable FluidStack stack, @Nullable EnumFacing side, Action action) {
        return ExtendedFluidHandlerUtils.insert(stack, side, action, this::getTanks, this::getFluidInTank, this::insertFluid);
    }

    @Nullable
    default FluidStack extractFluid(int amount, @Nullable EnumFacing side, Action action) {
        return ExtendedFluidHandlerUtils.extract(amount, side, action, this::getTanks, this::getFluidInTank, this::extractFluid);
    }

    @Nullable
    default FluidStack extractFluid(@Nullable FluidStack stack, @Nullable EnumFacing side, Action action) {
        return ExtendedFluidHandlerUtils.extract(stack, side, action, this::getTanks, this::getFluidInTank, this::extractFluid);
    }
}
