package mekanism.client.recipe_viewer.jei.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.machines.DoubleMachineRecipe;
import mezz.jei.api.IGuiHelper;

public class CombinerRecipeCategory<RECIPE extends DoubleMachineRecipe<RECIPE>, WRAPPER extends mekanism.client.jei.machine.DoubleMachineRecipeWrapper<RECIPE>>
      extends mekanism.client.jei.machine.CombinerRecipeCategory<RECIPE, WRAPPER> {

    public CombinerRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper, recipeType);
    }
}
