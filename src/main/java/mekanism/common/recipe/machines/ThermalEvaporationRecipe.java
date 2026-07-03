package mekanism.common.recipe.machines;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.recipe.inputs.FluidInput;
import mekanism.common.recipe.outputs.FluidOutput;
import net.minecraftforge.fluids.FluidStack;

public class ThermalEvaporationRecipe extends MachineRecipe<FluidInput, FluidOutput, ThermalEvaporationRecipe> {

    public ThermalEvaporationRecipe(FluidStack input, FluidStack output) {
        super(new FluidInput(input), new FluidOutput(output));
    }

    public ThermalEvaporationRecipe(FluidInput input, FluidOutput output) {
        super(input, output);
    }

    @Override
    public ThermalEvaporationRecipe copy() {
        return new ThermalEvaporationRecipe(getInput(), getOutput());
    }

    public boolean canOperate(IExtendedFluidTank inputTank, IExtendedFluidTank outputTank) {
        return getInput().useFluid(inputTank, false, 1) && getOutput().applyOutputs(outputTank, false);
    }

}
