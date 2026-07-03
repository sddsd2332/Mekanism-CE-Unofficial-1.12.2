package mekanism.client.recipe_viewer.jei.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.machines.BasicMachineRecipe;
import mezz.jei.api.IGuiHelper;

public class ItemStackToItemStackRecipeCategory<RECIPE extends BasicMachineRecipe<RECIPE>, WRAPPER extends mekanism.client.jei.machine.MachineRecipeWrapper<RECIPE>>
      extends mekanism.client.jei.machine.ItemStackToItemStackRecipeCategory<RECIPE, WRAPPER> {

    public ItemStackToItemStackRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper, recipeType);
    }
}
