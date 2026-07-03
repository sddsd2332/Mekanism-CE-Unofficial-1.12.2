package mekanism.client.jei.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.machines.DissolutionRecipe;
import mezz.jei.api.IGuiHelper;

public class ChemicalDissolutionRecipeCategory<WRAPPER extends mekanism.client.jei.machine.chemical.ChemicalDissolutionChamberRecipeWrapper<DissolutionRecipe>>
      extends mekanism.client.jei.machine.chemical.ChemicalDissolutionChamberRecipeCategory<WRAPPER> {

    public ChemicalDissolutionRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper);
    }
}
