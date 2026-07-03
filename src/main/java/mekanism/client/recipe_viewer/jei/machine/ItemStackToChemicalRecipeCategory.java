package mekanism.client.recipe_viewer.jei.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.machines.OxidationRecipe;
import mezz.jei.api.IGuiHelper;

public class ItemStackToChemicalRecipeCategory<WRAPPER extends mekanism.client.jei.machine.chemical.ChemicalOxidizerRecipeWrapper<OxidationRecipe>>
      extends mekanism.client.jei.machine.ItemStackToGasRecipeCategory<WRAPPER> {

    public ItemStackToChemicalRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType, boolean isConversion) {
        super(helper, recipeType);
    }
}
