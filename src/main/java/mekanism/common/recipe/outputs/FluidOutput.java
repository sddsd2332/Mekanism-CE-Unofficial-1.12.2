package mekanism.common.recipe.outputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.ExtendedFluidHandlerUtils;
import mekanism.api.fluid.IExtendedFluidTank;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

public class FluidOutput extends MachineOutput<FluidOutput> {

    public FluidStack output;

    public FluidOutput(FluidStack stack) {
        output = stack;
    }

    public FluidOutput() {
    }

    @Override
    public void load(NBTTagCompound nbtTags) {
        output = FluidStack.loadFluidStackFromNBT(nbtTags.getCompoundTag("output"));
    }

    @Override
    public FluidOutput copy() {
        return new FluidOutput(output.copy());
    }

    public boolean applyOutputs(IExtendedFluidTank fluidTank, boolean doEmit) {
        if (output == null || output.amount <= 0) {
            return false;
        }
        FluidStack remainder = fluidTank.insert(output, Action.SIMULATE, AutomationType.INTERNAL);
        if (output.amount - (ExtendedFluidHandlerUtils.isEmpty(remainder) ? 0 : remainder.amount) > 0) {
            fluidTank.insert(output, Action.get(doEmit), AutomationType.INTERNAL);
            return true;
        }
        return false;
    }
}
