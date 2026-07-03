package mekanism.client.jei.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.jei.machine.other.ItemStackToEnergyRecipeWrapper;
import mekanism.common.recipe.ItemStackToEnergyRecipe;
import mezz.jei.api.IGuiHelper;

public class ItemStackToEnergyRecipeCategory<WRAPPER extends ItemStackToEnergyRecipeWrapper<ItemStackToEnergyRecipe>>
      extends mekanism.client.jei.machine.other.ItemStackToEnergyRecipeCategory<WRAPPER> {

    public ItemStackToEnergyRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper);
    }
}
