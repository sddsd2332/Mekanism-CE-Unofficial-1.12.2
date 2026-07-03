package mekanism.client.jei.machine.other;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.client.jei.MekanismJEI;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeWrapper;

public class SPSRecipeWrapper implements IRecipeWrapper {

    private Gas inputGas;
    private Gas outputGas;

    public SPSRecipeWrapper(Gas inputGas, Gas outputGas) {
        this.inputGas = inputGas;
        this.outputGas = outputGas;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        ingredients.setInput(MekanismJEI.TYPE_GAS, new GasStack(inputGas, 1));
        ingredients.setInput(MekanismJEI.TYPE_GAS, new GasStack(outputGas, 1));
    }


    public Gas getInputGas() {
        return inputGas;
    }

    public Gas getOutputGas() {
        return outputGas;
    }
}
