package mekanism.generators.common.content.turbine;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.ExtendedFluidHandlerUtils;
import mekanism.common.base.MultiblockFluidTank;
import mekanism.common.tile.TileEntityGasTank.GasMode;
import mekanism.generators.common.tile.turbine.TileEntityTurbineCasing;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

public class TurbineFluidTank extends MultiblockFluidTank<TileEntityTurbineCasing> {

    public TurbineFluidTank(TileEntityTurbineCasing tileEntity) {
        super(tileEntity);
    }

    @Override
    @Nullable
    public FluidStack getFluid() {
        return multiblock.structure != null ? multiblock.structure.fluidStored : null;
    }

    @Override
    public void setFluid(FluidStack stack) {
        if (multiblock.structure != null) {
            multiblock.structure.fluidStored = stack;
        }
    }

    @Override
    public int getCapacity() {
        return multiblock.structure != null ? multiblock.structure.getFluidCapacity() : 0;
    }

    @Override
    public boolean isFluidValid(@Nullable FluidStack stack) {
        return stack != null && stack.getFluid() == FluidRegistry.getFluid("steam");
    }

    @Override
    @Nullable
    public FluidStack insert(@Nullable FluidStack stack, Action action, AutomationType automationType) {
        if (ExtendedFluidHandlerUtils.isEmpty(stack) || !isFluidValid(stack)) {
            return stack;
        }
        int filled = insertSteam(stack, action);
        return filled >= stack.amount ? null : new FluidStack(stack, stack.amount - filled);
    }

    @Override
    public int fill(@Nullable FluidStack resource, boolean doFill) {
        return insertSteam(resource, Action.get(doFill));
    }

    private int insertSteam(@Nullable FluidStack resource, Action action) {
        if (multiblock.structure == null || ExtendedFluidHandlerUtils.isEmpty(resource) || !isFluidValid(resource)) {
            return 0;
        }
        FluidStack remainder = super.insert(resource, action, AutomationType.EXTERNAL);
        int filled = resource.amount - (remainder == null ? 0 : remainder.amount);
        if (action.execute() && filled > 0) {
            multiblock.structure.newSteamInput += filled;
        }
        if (filled < multiblock.structure.getFluidCapacity() && multiblock.structure.dumpMode != GasMode.IDLE) {
            filled = Math.min(multiblock.structure.getFluidCapacity(), resource.amount);
        }
        return filled;
    }

    @Override
    protected void updateValveData() {
    }
}
