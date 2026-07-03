package mekanism.client.recipe_viewer.jei.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.machines.SeparatorRecipe;
import mezz.jei.api.IGuiHelper;

public class ElectrolysisRecipeCategory<WRAPPER extends mekanism.client.jei.machine.other.ElectrolyticSeparatorRecipeWrapper<SeparatorRecipe>>
      extends mekanism.client.jei.machine.ElectrolysisRecipeCategory<WRAPPER> {

    public ElectrolysisRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper, recipeType);
    }
}
