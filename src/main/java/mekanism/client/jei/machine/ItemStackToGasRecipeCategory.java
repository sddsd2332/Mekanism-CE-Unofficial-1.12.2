package mekanism.client.jei.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.machines.OxidationRecipe;
import mezz.jei.api.IGuiHelper;

public class ItemStackToGasRecipeCategory<WRAPPER extends mekanism.client.jei.machine.chemical.ChemicalOxidizerRecipeWrapper<OxidationRecipe>>
      extends mekanism.client.jei.machine.chemical.ChemicalOxidizerRecipeCategory<WRAPPER> {

    public ItemStackToGasRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper);
    }
}
