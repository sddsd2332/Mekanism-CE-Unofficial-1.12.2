package mekanism.common.recipe.machines;

import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.recipe.inputs.GasAndFluidInput;
import mekanism.common.recipe.outputs.GasOutput;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;


public class WasherRecipe extends MachineRecipe<GasAndFluidInput, GasOutput, WasherRecipe> {

    public WasherRecipe(GasAndFluidInput input, GasOutput output) {
        super(input, output);
    }

    public WasherRecipe(GasStack input, FluidStack stack, GasStack output) {
        this(new GasAndFluidInput(input, stack), new GasOutput(output));
    }

    public WasherRecipe(GasStack input, GasStack output) {
        this(new GasAndFluidInput(input, new FluidStack(FluidRegistry.WATER, 5)), new GasOutput(output));
    }


    @Override
    public WasherRecipe copy() {
        return new WasherRecipe(getInput().copy(), getOutput().copy());
    }

    public boolean canOperate(IExtendedGasTank inputTank, IExtendedFluidTank fluidTank, IExtendedGasTank outputTank) {
        return getInput().useGas(inputTank, false, 1) && getInput().useFluid(fluidTank, false, 1) && getOutput().applyOutputs(outputTank, false, 1);
    }

}
