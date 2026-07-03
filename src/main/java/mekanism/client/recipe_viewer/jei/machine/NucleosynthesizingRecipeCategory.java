package mekanism.client.recipe_viewer.jei.machine;

import mekanism.client.jei.machine.other.AntiprotonicNucleosynthesizerRecipeWrapper;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.machines.NucleosynthesizerRecipe;
import mezz.jei.api.IGuiHelper;

public class NucleosynthesizingRecipeCategory<WRAPPER extends AntiprotonicNucleosynthesizerRecipeWrapper<NucleosynthesizerRecipe>>
      extends mekanism.client.jei.machine.NucleosynthesizingRecipeCategory<WRAPPER> {

    public NucleosynthesizingRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper, recipeType);
    }
}
