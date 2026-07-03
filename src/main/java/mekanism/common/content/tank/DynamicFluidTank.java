package mekanism.common.content.tank;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.Coord4D;
import mekanism.api.fluid.ExtendedFluidHandlerUtils;
import mekanism.common.base.MultiblockFluidTank;
import mekanism.common.content.tank.SynchronizedTankData.ValveData;
import mekanism.common.tile.multiblock.TileEntityDynamicTank;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

public class DynamicFluidTank extends MultiblockFluidTank<TileEntityDynamicTank> {

    public DynamicFluidTank(TileEntityDynamicTank tileEntity) {
        super(tileEntity);
    }

    boolean canMutate(SynchronizedTankData data) {
        return multiblock.structure == data && multiblock.getWorld() != null && !multiblock.getWorld().isRemote;
    }

    @Override
    @Nullable
    public FluidStack getFluid() {
        return multiblock.structure != null ? multiblock.structure.fluidStored : null;
    }

    @Override
    @Nullable
    public FluidStack insert(@Nullable FluidStack stack, Action action, AutomationType automationType) {
        if (multiblock.structure != null && multiblock.structure.hasGas()) {
            return stack;
        }
        return super.insert(stack, action, automationType);
    }

    @Override
    public int fill(@Nullable FluidStack resource, boolean doFill) {
        if (ExtendedFluidHandlerUtils.isEmpty(resource)) {
            return 0;
        }
        FluidStack remainder = insert(resource, Action.get(doFill), AutomationType.EXTERNAL);
        return resource.amount - (remainder == null ? 0 : remainder.amount);
    }

    @Override
    public void setFluid(FluidStack stack) {
        if (multiblock.structure != null) {
            multiblock.structure.fluidStored = stack;
        }
    }

    @Override
    public int getCapacity() {
        return multiblock.structure != null ? multiblock.structure.volume * TankUpdateProtocol.FLUID_PER_TANK : 0;
    }

    @Override
    protected void updateValveData() {
        if (multiblock.structure != null) {
            Coord4D coord4D = Coord4D.get(multiblock);
            for (ValveData data : multiblock.structure.valves) {
                if (coord4D.equals(data.location)) {
                    data.onTransfer();
                }
            }
        }
    }
}
