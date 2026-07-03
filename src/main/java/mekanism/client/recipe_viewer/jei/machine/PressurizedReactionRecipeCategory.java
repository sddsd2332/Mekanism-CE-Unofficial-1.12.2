package mekanism.client.recipe_viewer.jei.machine;

import mekanism.client.jei.machine.other.PRCRecipeWrapper;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.machines.PressurizedRecipe;
import mezz.jei.api.IGuiHelper;

public class PressurizedReactionRecipeCategory<WRAPPER extends PRCRecipeWrapper<PressurizedRecipe>>
      extends mekanism.client.jei.machine.PressurizedReactionRecipeCategory<WRAPPER> {

    public PressurizedReactionRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper, recipeType);
    }
}
