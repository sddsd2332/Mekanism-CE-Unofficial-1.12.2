package mekanism.client.recipe_viewer.jei.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.machines.WasherRecipe;
import mezz.jei.api.IGuiHelper;

public class FluidChemicalToChemicalRecipeCategory<WRAPPER extends mekanism.client.jei.machine.chemical.ChemicalWasherRecipeWrapper<WasherRecipe>>
      extends mekanism.client.jei.machine.FluidSlurryToSlurryRecipeCategory<WRAPPER> {

    public FluidChemicalToChemicalRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper, recipeType);
    }
}
