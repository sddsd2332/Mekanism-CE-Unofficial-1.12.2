package mekanism.client.jei.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.machines.ChemicalInfuserRecipe;
import mezz.jei.api.IGuiHelper;

public class ChemicalInfuserRecipeCategory<WRAPPER extends mekanism.client.jei.machine.chemical.ChemicalInfuserRecipeWrapper<ChemicalInfuserRecipe>>
      extends mekanism.client.jei.machine.chemical.ChemicalInfuserRecipeCategory<WRAPPER> {

    public ChemicalInfuserRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper);
    }
}
