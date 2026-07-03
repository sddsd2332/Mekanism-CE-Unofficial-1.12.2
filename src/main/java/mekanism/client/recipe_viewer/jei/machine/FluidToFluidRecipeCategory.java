package mekanism.client.recipe_viewer.jei.machine;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.machines.ThermalEvaporationRecipe;
import mezz.jei.api.IGuiHelper;

public class FluidToFluidRecipeCategory<WRAPPER extends mekanism.client.jei.machine.other.ThermalEvaporationRecipeWrapper<ThermalEvaporationRecipe>>
      extends mekanism.client.jei.machine.other.ThermalEvaporationRecipeCategory<WRAPPER> {

    public FluidToFluidRecipeCategory(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        super(helper);
    }
}
