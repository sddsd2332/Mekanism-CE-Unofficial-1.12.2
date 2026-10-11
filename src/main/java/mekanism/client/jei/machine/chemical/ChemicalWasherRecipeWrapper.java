package mekanism.client.jei.machine.chemical;

import mekanism.client.jei.MekanismJEI;
import mekanism.client.jei.machine.MekanismRecipeWrapper;
import mekanism.common.recipe.machines.WasherRecipe;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;

public class ChemicalWasherRecipeWrapper<RECIPE extends WasherRecipe> extends MekanismRecipeWrapper<RECIPE> {

    public ChemicalWasherRecipeWrapper(RECIPE recipe) {
        super(recipe);
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        ingredients.setInput(VanillaTypes.FLUID, recipe.recipeInput.ingredientFluid);
        ingredients.setInput(MekanismJEI.TYPE_GAS, recipe.recipeInput.ingredientGas);
        ingredients.setOutput(MekanismJEI.TYPE_GAS, recipe.recipeOutput.output);
    }
}
