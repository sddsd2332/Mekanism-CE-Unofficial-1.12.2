package mekanism.api.fluid;

import mekanism.api.Action;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nullable;

public interface IExtendedFluidHandler extends IFluidHandler {

    int getTanks();

    @Nullable
    FluidStack getFluidInTank(int tank);

    void setFluidInTank(int tank, @Nullable FluidStack stack);

    int getTankCapacity(int tank);

    boolean isFluidValid(int tank, @Nullable FluidStack stack);

    @Nullable
    FluidStack insertFluid(int tank, @Nullable FluidStack stack, Action action);

    @Nullable
    FluidStack extractFluid(int tank, int amount, Action action);

    @Nullable
    default FluidStack insertFluid(@Nullable FluidStack stack, Action action) {
        return ExtendedFluidHandlerUtils.insert(stack, null, action, side -> getTanks(), (tank, side) -> getFluidInTank(tank),
              (tank, fluid, side, act) -> insertFluid(tank, fluid, act));
    }

    @Nullable
    default FluidStack extractFluid(int amount, Action action) {
        return ExtendedFluidHandlerUtils.extract(amount, null, action, side -> getTanks(), (tank, side) -> getFluidInTank(tank),
              (tank, amt, side, act) -> extractFluid(tank, amt, act));
    }

    @Nullable
    default FluidStack extractFluid(@Nullable FluidStack stack, Action action) {
        return ExtendedFluidHandlerUtils.extract(stack, null, action, side -> getTanks(), (tank, side) -> getFluidInTank(tank),
              (tank, amt, side, act) -> extractFluid(tank, amt, act));
    }

    @Override
    default int fill(FluidStack stack, boolean doFill) {
        if (ExtendedFluidHandlerUtils.isEmpty(stack)) {
            return 0;
        }
        FluidStack remainder = insertFluid(stack, Action.get(doFill));
        return stack.amount - (remainder == null ? 0 : remainder.amount);
    }

    @Override
    @Nullable
    default FluidStack drain(FluidStack stack, boolean doDrain) {
        return extractFluid(stack, Action.get(doDrain));
    }

    @Override
    @Nullable
    default FluidStack drain(int amount, boolean doDrain) {
        return extractFluid(amount, Action.get(doDrain));
    }

    @Override
    IFluidTankProperties[] getTankProperties();
}
