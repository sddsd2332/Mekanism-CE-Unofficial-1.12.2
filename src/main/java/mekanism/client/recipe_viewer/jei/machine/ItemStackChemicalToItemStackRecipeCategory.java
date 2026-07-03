package mekanism.client.recipe_viewer.jei.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.machines.AdvancedMachineRecipe;
import mezz.jei.api.IGuiHelper;

public class ItemStackChemicalToItemStackRecipeCategory<RECIPE extends AdvancedMachineRecipe<RECIPE>, WRAPPER extends mekanism.client.jei.machine.AdvancedMachineRecipeWrapper<RECIPE>>
      extends mekanism.client.jei.machine.ItemStackGasToItemStackRecipeCategory<RECIPE, WRAPPER> {

    public ItemStackChemicalToItemStackRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper, recipeType);
    }
}
