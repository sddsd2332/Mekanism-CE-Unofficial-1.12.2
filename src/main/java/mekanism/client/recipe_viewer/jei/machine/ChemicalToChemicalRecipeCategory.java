package mekanism.client.recipe_viewer.jei.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.recipe.IRecipeWrapper;

public class ChemicalToChemicalRecipeCategory<WRAPPER extends IRecipeWrapper> extends mekanism.client.jei.machine.GasToGasRecipeCategory<WRAPPER> {

    public ChemicalToChemicalRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper, recipeType, RecipeCategoryBridge.chemicalToChemicalDelegate(helper, recipeType));
    }
}
