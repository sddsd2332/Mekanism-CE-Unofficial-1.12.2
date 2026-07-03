package mekanism.client.jei.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.machines.CrystallizerRecipe;
import mezz.jei.api.IGuiHelper;

public class ChemicalCrystallizerRecipeCategory<WRAPPER extends mekanism.client.jei.machine.chemical.ChemicalCrystallizerRecipeWrapper<CrystallizerRecipe>>
      extends mekanism.client.jei.machine.chemical.ChemicalCrystallizerRecipeCategory<WRAPPER> {

    public ChemicalCrystallizerRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper);
    }
}
