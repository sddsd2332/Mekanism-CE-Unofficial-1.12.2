package mekanism.client.jei.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.jei.machine.other.NutritionalLiquifierRecipeWrapper;
import mekanism.common.recipe.machines.NutritionalRecipe;
import mezz.jei.api.IGuiHelper;

public class ItemStackToFluidRecipeCategory<WRAPPER extends NutritionalLiquifierRecipeWrapper<NutritionalRecipe>>
      extends mekanism.client.jei.machine.other.NutritionalLiquifierRecipeCategory<WRAPPER> {

    public ItemStackToFluidRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper);
    }
}
