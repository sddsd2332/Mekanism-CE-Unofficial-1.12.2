package mekanism.client.recipe_viewer.jei.machine;

import mekanism.client.jei.machine.other.NutritionalLiquifierRecipeWrapper;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.machines.NutritionalRecipe;
import mezz.jei.api.IGuiHelper;

public class ItemStackToFluidOptionalItemRecipeCategory<WRAPPER extends NutritionalLiquifierRecipeWrapper<NutritionalRecipe>>
      extends mekanism.client.jei.machine.ItemStackToFluidRecipeCategory<WRAPPER> {

    public ItemStackToFluidOptionalItemRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType, boolean isConversion) {
        super(helper, recipeType);
    }
}
