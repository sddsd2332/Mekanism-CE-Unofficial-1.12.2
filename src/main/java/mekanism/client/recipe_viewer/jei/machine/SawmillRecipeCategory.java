package mekanism.client.recipe_viewer.jei.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.machines.ChanceMachineRecipe;
import mezz.jei.api.IGuiHelper;

public class SawmillRecipeCategory<RECIPE extends ChanceMachineRecipe<RECIPE>, WRAPPER extends mekanism.client.jei.machine.ChanceMachineRecipeWrapper<RECIPE>>
      extends mekanism.client.jei.machine.SawmillRecipeCategory<RECIPE, WRAPPER> {

    public SawmillRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper, recipeType);
    }
}
