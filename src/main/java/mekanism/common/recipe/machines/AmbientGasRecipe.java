package mekanism.common.recipe.machines;

import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.recipe.inputs.IntegerInput;
import mekanism.common.recipe.outputs.ChanceGasOutput;

public class AmbientGasRecipe extends MachineRecipe<IntegerInput, ChanceGasOutput, AmbientGasRecipe> {

    public AmbientGasRecipe(IntegerInput input, ChanceGasOutput output) {
        super(input, output);
    }

    public AmbientGasRecipe(int input, GasStack output, double chance) {
        this(new IntegerInput(input), new ChanceGasOutput(output,chance));
    }

    public AmbientGasRecipe(int input, String output,double chance) {
        this(new IntegerInput(input), new ChanceGasOutput(new GasStack(GasRegistry.getGas(output), 1),chance));
    }

    public boolean canOperate(int cachedDimensionId, IExtendedGasTank outputTank) {
        return getInput().ingredient == cachedDimensionId && getOutput().applyOutputs(outputTank, false, 1);
    }

    @Override
    public AmbientGasRecipe copy() {
        return new AmbientGasRecipe(getInput().copy(), getOutput().copy());
    }

}
