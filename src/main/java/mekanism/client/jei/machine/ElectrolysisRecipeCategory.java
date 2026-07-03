package mekanism.client.jei.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.jei.machine.other.ElectrolyticSeparatorRecipeWrapper;
import mekanism.common.recipe.machines.SeparatorRecipe;
import mezz.jei.api.IGuiHelper;

public class ElectrolysisRecipeCategory<WRAPPER extends ElectrolyticSeparatorRecipeWrapper<SeparatorRecipe>>
      extends mekanism.client.jei.machine.other.ElectrolyticSeparatorRecipeCategory<WRAPPER> {

    public ElectrolysisRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper);
    }
}
