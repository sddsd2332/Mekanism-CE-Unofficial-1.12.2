package mekanism.generators.common.content.turbine;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.common.base.MultiblockFluidTank;
import mekanism.generators.common.tile.turbine.TileEntityTurbineCasing;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

public class TurbineVentFluidTank extends MultiblockFluidTank<TileEntityTurbineCasing> {

    public TurbineVentFluidTank(TileEntityTurbineCasing tileEntity) {
        super(tileEntity);
    }

    @Override
    @Nullable
    public FluidStack getFluid() {
        return multiblock.structure != null && multiblock.structure.getVentWaterAmount() > 0 ?
              new FluidStack(FluidRegistry.WATER, multiblock.structure.getVentWaterAmount()) : null;
    }

    @Override
    public void setFluid(@Nullable FluidStack stack) {
        if (multiblock.structure != null) {
            multiblock.structure.setVentWaterStackSize(stack != null && isFluidValid(stack) ? stack.amount : 0);
        }
    }

    @Override
    public int getCapacity() {
        return multiblock.structure == null ? 0 : multiblock.structure.getVentWaterCapacity();
    }

    @Override
    public boolean isFluidValid(@Nullable FluidStack stack) {
        return stack != null && stack.getFluid() == FluidRegistry.WATER;
    }

    @Override
    @Nullable
    public FluidStack insert(@Nullable FluidStack stack, Action action, AutomationType automationType) {
        return stack;
    }

    @Override
    public int fill(@Nullable FluidStack resource, boolean doFill) {
        return 0;
    }

    @Override
    @Nullable
    public FluidStack extract(int amount, Action action, AutomationType automationType) {
        return drain(amount, action.execute());
    }

    @Override
    @Nullable
    public FluidStack drain(int maxDrain, boolean doDrain) {
        if (multiblock.structure == null || maxDrain <= 0 || multiblock.structure.getVentWaterAmount() <= 0) {
            return null;
        }
        int amount = Math.min(maxDrain, multiblock.structure.getVentWaterAmount());
        if (doDrain) {
            multiblock.structure.shrinkVentWaterStack(amount);
        }
        return amount <= 0 ? null : new FluidStack(FluidRegistry.WATER, amount);
    }

    @Override
    public int getFluidAmount() {
        return multiblock.structure == null ? 0 : multiblock.structure.getVentWaterAmount();
    }

    @Override
    protected void updateValveData() {
    }
}
