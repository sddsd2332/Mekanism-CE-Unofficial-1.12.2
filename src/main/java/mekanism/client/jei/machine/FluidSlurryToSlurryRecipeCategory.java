package mekanism.client.jei.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.machines.WasherRecipe;
import mezz.jei.api.IGuiHelper;

public class FluidSlurryToSlurryRecipeCategory<WRAPPER extends mekanism.client.jei.machine.chemical.ChemicalWasherRecipeWrapper<WasherRecipe>>
      extends mekanism.client.jei.machine.chemical.ChemicalWasherRecipeCategory<WRAPPER> {

    public FluidSlurryToSlurryRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper);
    }
}
