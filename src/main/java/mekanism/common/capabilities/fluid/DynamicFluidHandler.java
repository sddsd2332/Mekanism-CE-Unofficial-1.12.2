package mekanism.common.capabilities.fluid;

import mekanism.api.Action;
import mekanism.api.IContentsListener;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.common.capabilities.DynamicHandler;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class DynamicFluidHandler extends DynamicHandler<IExtendedFluidTank> implements IMekanismFluidHandler {

    public DynamicFluidHandler(Function<EnumFacing, List<IExtendedFluidTank>> tankSupplier, Predicate<EnumFacing> canExtract, Predicate<EnumFacing> canInsert,
          @Nullable IContentsListener listener) {
        super(tankSupplier, canExtract, canInsert, listener);
    }

    public DynamicFluidHandler(Function<EnumFacing, List<IExtendedFluidTank>> tankSupplier, InteractPredicate canExtract, InteractPredicate canInsert,
          @Nullable IContentsListener listener) {
        super(tankSupplier, canExtract, canInsert, listener);
    }

    @Override
    public List<IExtendedFluidTank> getFluidTanks(@Nullable EnumFacing side) {
        return containerSupplier.apply(side);
    }

    @Override
    public boolean canInsertFluid(@Nullable EnumFacing side) {
        List<IExtendedFluidTank> fluidTanks = getFluidTanks(side);
        for (int tank = 0; tank < fluidTanks.size(); tank++) {
            if (canInsert.test(tank, side)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canExtractFluid(@Nullable EnumFacing side) {
        List<IExtendedFluidTank> fluidTanks = getFluidTanks(side);
        for (int tank = 0; tank < fluidTanks.size(); tank++) {
            if (canExtract.test(tank, side)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Nullable
    public FluidStack insertFluid(int tank, @Nullable FluidStack stack, @Nullable EnumFacing side, Action action) {
        return canInsert.test(tank, side) ? IMekanismFluidHandler.super.insertFluid(tank, stack, side, action) : stack;
    }

    @Override
    @Nullable
    public FluidStack extractFluid(int tank, int amount, @Nullable EnumFacing side, Action action) {
        return canExtract.test(tank, side) ? IMekanismFluidHandler.super.extractFluid(tank, amount, side, action) : null;
    }

    @Override
    @Nullable
    public FluidStack insertFluid(@Nullable FluidStack stack, @Nullable EnumFacing side, Action action) {
        return canInsertFluid(side) ? IMekanismFluidHandler.super.insertFluid(stack, side, action) : stack;
    }

    @Override
    @Nullable
    public FluidStack extractFluid(int amount, @Nullable EnumFacing side, Action action) {
        return canExtractFluid(side) ? IMekanismFluidHandler.super.extractFluid(amount, side, action) : null;
    }

    @Override
    @Nullable
    public FluidStack extractFluid(@Nullable FluidStack stack, @Nullable EnumFacing side, Action action) {
        return canExtractFluid(side) ? IMekanismFluidHandler.super.extractFluid(stack, side, action) : null;
    }
}
