package mekanism.common.capabilities.proxy;

import mekanism.api.Action;
import mekanism.api.fluid.ExtendedFluidHandlerUtils;
import mekanism.api.fluid.IExtendedFluidHandler;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.ISidedFluidHandler;
import mekanism.common.capabilities.holder.IHolder;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nullable;

public class ProxyFluidHandler extends ProxyHandler implements IExtendedFluidHandler {

    private final ISidedFluidHandler fluidHandler;

    public ProxyFluidHandler(ISidedFluidHandler fluidHandler, @Nullable EnumFacing side, @Nullable IHolder holder) {
        super(side, holder);
        this.fluidHandler = fluidHandler;
    }

    public ISidedFluidHandler getInternalHandler() {
        return fluidHandler;
    }

    @Override
    public int getTanks() {
        return fluidHandler.getTanks(side);
    }

    @Override
    @Nullable
    public FluidStack getFluidInTank(int tank) {
        return fluidHandler.getFluidInTank(tank, side);
    }

    @Override
    public void setFluidInTank(int tank, @Nullable FluidStack stack) {
        if (!readOnly) {
            fluidHandler.setFluidInTank(tank, stack, side);
        }
    }

    @Override
    public int getTankCapacity(int tank) {
        return fluidHandler.getTankCapacity(tank, side);
    }

    @Override
    public boolean isFluidValid(int tank, @Nullable FluidStack stack) {
        return !readOnly || fluidHandler.isFluidValid(tank, stack, side);
    }

    @Override
    @Nullable
    public FluidStack insertFluid(int tank, @Nullable FluidStack stack, Action action) {
        return readOnlyInsert() ? stack : fluidHandler.insertFluid(tank, stack, side, action);
    }

    @Override
    @Nullable
    public FluidStack extractFluid(int tank, int amount, Action action) {
        return readOnlyExtract() ? null : fluidHandler.extractFluid(tank, amount, side, action);
    }

    @Override
    @Nullable
    public FluidStack insertFluid(@Nullable FluidStack stack, Action action) {
        return readOnlyInsert() ? stack : fluidHandler.insertFluid(stack, side, action);
    }

    @Override
    @Nullable
    public FluidStack extractFluid(int amount, Action action) {
        return readOnlyExtract() ? null : fluidHandler.extractFluid(amount, side, action);
    }

    @Override
    @Nullable
    public FluidStack extractFluid(@Nullable FluidStack stack, Action action) {
        return readOnlyExtract() ? null : fluidHandler.extractFluid(stack, side, action);
    }

    @Override
    public IFluidTankProperties[] getTankProperties() {
        int tanks = getTanks();
        if (tanks == 0) {
            return new IFluidTankProperties[0];
        }
        IFluidTankProperties[] properties = new IFluidTankProperties[tanks];
        for (int tank = 0; tank < tanks; tank++) {
            int tankIndex = tank;
            properties[tank] = new IFluidTankProperties() {
                @Override
                public FluidStack getContents() {
                    FluidStack stored = getFluidInTank(tankIndex);
                    return stored == null ? null : stored.copy();
                }

                @Override
                public int getCapacity() {
                    return getTankCapacity(tankIndex);
                }

                @Override
                public boolean canFill() {
                    return canFillTank(tankIndex);
                }

                @Override
                public boolean canDrain() {
                    return canDrainTank(tankIndex) && !ExtendedFluidHandlerUtils.isEmpty(getFluidInTank(tankIndex));
                }

                @Override
                public boolean canFillFluidType(FluidStack fluidStack) {
                    return canFillTank(tankIndex) && fluidHandler.isFluidValid(tankIndex, fluidStack, side);
                }

                @Override
                public boolean canDrainFluidType(FluidStack fluidStack) {
                    FluidStack stored = getFluidInTank(tankIndex);
                    return canDrainTank(tankIndex) && !ExtendedFluidHandlerUtils.isEmpty(stored) && (fluidStack == null || stored.isFluidEqual(fluidStack));
                }
            };
        }
        return properties;
    }

    private boolean canFillTank(int tank) {
        return !readOnlyInsert() && canInteractWithTank(tank, true);
    }

    private boolean canDrainTank(int tank) {
        return !readOnlyExtract() && canInteractWithTank(tank, false);
    }

    private boolean canInteractWithTank(int tank, boolean insert) {
        IHolder holder = getHolder();
        if (!(holder instanceof IFluidTankHolder fluidTankHolder)) {
            return true;
        }
        IExtendedFluidTank fluidTank = tank >= 0 && tank < fluidTankHolder.getTanks(side).size() ? fluidTankHolder.getTanks(side).get(tank) : null;
        return fluidTank != null && (insert ? fluidTankHolder.canInsert(side, fluidTank) : fluidTankHolder.canExtract(side, fluidTank));
    }
}
