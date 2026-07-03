package mekanism.common.recipe.machines;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.recipe.inputs.GasInput;
import mekanism.common.recipe.outputs.GasOutput;

public class SolarNeutronRecipe extends MachineRecipe<GasInput, GasOutput, SolarNeutronRecipe> {

    public SolarNeutronRecipe(GasStack input, GasStack output) {
        super(new GasInput(input), new GasOutput(output));
    }

    public SolarNeutronRecipe(GasInput input, GasOutput output) {
        super(input, output);
    }

    @Override
    public SolarNeutronRecipe copy() {
        return new SolarNeutronRecipe(getInput(), getOutput());
    }

    public boolean canOperate(IExtendedGasTank inputTank, IExtendedGasTank outputTank) {
        return getInput().useGas(inputTank, false, 1) && getOutput().applyOutputs(outputTank, false, 1);
    }

}
