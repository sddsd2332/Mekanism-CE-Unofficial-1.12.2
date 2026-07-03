package mekanism.common.recipe.machines;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.recipe.inputs.ChemicalPairInput;
import mekanism.common.recipe.outputs.GasOutput;

public class ChemicalInfuserRecipe extends MachineRecipe<ChemicalPairInput, GasOutput, ChemicalInfuserRecipe> {

    public ChemicalInfuserRecipe(ChemicalPairInput input, GasOutput output) {
        super(input, output);
    }

    public ChemicalInfuserRecipe(GasStack leftInput, GasStack rightInput, GasStack output) {
        this(new ChemicalPairInput(leftInput, rightInput), new GasOutput(output));
    }

    @Override
    public ChemicalInfuserRecipe copy() {
        return new ChemicalInfuserRecipe(getInput().copy(), getOutput().copy());
    }

    public boolean canOperate(IExtendedGasTank leftTank, IExtendedGasTank rightTank, IExtendedGasTank outputTank) {
        return getInput().useGas(leftTank, rightTank, false, 1) && getOutput().applyOutputs(outputTank, false, 1);
    }

}
