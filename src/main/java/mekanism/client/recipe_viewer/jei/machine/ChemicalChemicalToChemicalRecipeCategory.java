package mekanism.client.recipe_viewer.jei.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.machines.ChemicalInfuserRecipe;
import mezz.jei.api.IGuiHelper;

public class ChemicalChemicalToChemicalRecipeCategory<WRAPPER extends mekanism.client.jei.machine.chemical.ChemicalInfuserRecipeWrapper<ChemicalInfuserRecipe>>
      extends mekanism.client.jei.machine.ChemicalInfuserRecipeCategory<WRAPPER> {

    public ChemicalChemicalToChemicalRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper, recipeType);
    }
}
