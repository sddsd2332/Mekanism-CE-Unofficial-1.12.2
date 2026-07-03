package mekanism.client.recipe_viewer.jei.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.machines.MetallurgicInfuserRecipe;
import mezz.jei.api.IGuiHelper;

public class MetallurgicInfuserRecipeCategory<WRAPPER extends mekanism.client.jei.machine.other.MetallurgicInfuserRecipeWrapper<MetallurgicInfuserRecipe>>
      extends mekanism.client.jei.machine.other.MetallurgicInfuserRecipeCategory<WRAPPER> {

    public MetallurgicInfuserRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper);
    }
}
