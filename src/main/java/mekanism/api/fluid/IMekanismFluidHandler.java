package mekanism.api.fluid;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface IMekanismFluidHandler extends ISidedFluidHandler, IContentsListener {

    default boolean canHandleFluid() {
        return true;
    }

    @Nonnull
    List<IExtendedFluidTank> getFluidTanks(@Nullable EnumFacing side);

    default boolean canInsertFluid(@Nullable EnumFacing side) {
        return side != null;
    }

    default boolean canExtractFluid(@Nullable EnumFacing side) {
        return side != null;
    }

    @Nullable
    default IExtendedFluidTank getFluidTank(int tank, @Nullable EnumFacing side) {
        List<IExtendedFluidTank> tanks = getFluidTanks(side);
        return tank >= 0 && tank < tanks.size() ? tanks.get(tank) : null;
    }

    @Override
    default int getTanks(@Nullable EnumFacing side) {
        return getFluidTanks(side).size();
    }

    @Override
    @Nullable
    default FluidStack getFluidInTank(int tank, @Nullable EnumFacing side) {
        IExtendedFluidTank fluidTank = getFluidTank(tank, side);
        return fluidTank == null ? null : fluidTank.getFluid();
    }

    @Override
    default void setFluidInTank(int tank, @Nullable FluidStack stack, @Nullable EnumFacing side) {
        IExtendedFluidTank fluidTank = getFluidTank(tank, side);
        if (fluidTank != null) {
            fluidTank.setStack(stack);
        }
    }

    @Override
    default int getTankCapacity(int tank, @Nullable EnumFacing side) {
        IExtendedFluidTank fluidTank = getFluidTank(tank, side);
        return fluidTank == null ? 0 : fluidTank.getCapacity();
    }

    @Override
    default boolean isFluidValid(int tank, @Nullable FluidStack stack, @Nullable EnumFacing side) {
        IExtendedFluidTank fluidTank = getFluidTank(tank, side);
        return fluidTank != null && fluidTank.isFluidValid(stack);
    }

    @Override
    @Nullable
    default FluidStack insertFluid(int tank, @Nullable FluidStack stack, @Nullable EnumFacing side, Action action) {
        IExtendedFluidTank fluidTank = getFluidTank(tank, side);
        return fluidTank == null ? stack : fluidTank.insert(stack, action, AutomationType.handler(side));
    }

    @Override
    @Nullable
    default FluidStack extractFluid(int tank, int amount, @Nullable EnumFacing side, Action action) {
        IExtendedFluidTank fluidTank = getFluidTank(tank, side);
        return fluidTank == null ? null : fluidTank.extract(amount, action, AutomationType.handler(side));
    }

    @Override
    @Nullable
    default FluidStack insertFluid(@Nullable FluidStack stack, @Nullable EnumFacing side, Action action) {
        return ExtendedFluidHandlerUtils.insert(stack, side, this::getFluidTanks, action, AutomationType.handler(side));
    }

    @Override
    @Nullable
    default FluidStack extractFluid(int amount, @Nullable EnumFacing side, Action action) {
        return ExtendedFluidHandlerUtils.extract(amount, side, this::getFluidTanks, action, AutomationType.handler(side));
    }

    @Override
    @Nullable
    default FluidStack extractFluid(@Nullable FluidStack stack, @Nullable EnumFacing side, Action action) {
        return ExtendedFluidHandlerUtils.extract(stack, side, this::getFluidTanks, action, AutomationType.handler(side));
    }

    @Override
    default IFluidTankProperties[] getTankProperties() {
        EnumFacing side = getFluidSideFor();
        List<IExtendedFluidTank> tanks = getFluidTanks(side);
        IFluidTankProperties[] properties = new IFluidTankProperties[tanks.size()];
        for (int i = 0; i < tanks.size(); i++) {
            int tankIndex = i;
            IExtendedFluidTank tank = tanks.get(tankIndex);
            properties[tankIndex] = new IFluidTankProperties() {
                @Override
                @Nullable
                public FluidStack getContents() {
                    FluidStack stored = tank.getFluid();
                    return stored == null ? null : stored.copy();
                }

                @Override
                public int getCapacity() {
                    return tank.getCapacity();
                }

                @Override
                public boolean canFill() {
                    return side == null || canInsertFluid(side);
                }

                @Override
                public boolean canDrain() {
                    return side == null || canExtractFluid(side);
                }

                @Override
                public boolean canFillFluidType(FluidStack fluidStack) {
                    return canFill() && isFluidValid(tankIndex, fluidStack, side);
                }

                @Override
                public boolean canDrainFluidType(FluidStack fluidStack) {
                    FluidStack stored = tank.getFluid();
                    return canDrain() && !ExtendedFluidHandlerUtils.isEmpty(stored) && (fluidStack == null || stored.isFluidEqual(fluidStack));
                }
            };
        }
        return properties;
    }
}
