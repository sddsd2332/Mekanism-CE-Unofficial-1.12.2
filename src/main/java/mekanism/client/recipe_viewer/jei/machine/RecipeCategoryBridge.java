package mekanism.client.recipe_viewer.jei.machine;

import mekanism.client.jei.BaseRecipeCategory;
import mekanism.client.jei.machine.other.IsotopicRecipeCategory;
import mekanism.client.jei.machine.other.SolarNeutronRecipeCategory;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.recipe.IRecipeWrapper;

final class RecipeCategoryBridge {

    private RecipeCategoryBridge() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <WRAPPER extends IRecipeWrapper> BaseRecipeCategory<WRAPPER> chemicalToChemicalDelegate(IGuiHelper helper, IRecipeViewerRecipeType<?> recipeType) {
        if (RecipeViewerRecipeType.ACTIVATING.id().equals(recipeType.id())) {
            return (BaseRecipeCategory<WRAPPER>) (BaseRecipeCategory) new SolarNeutronRecipeCategory(helper);
        } else if (RecipeViewerRecipeType.CENTRIFUGING.id().equals(recipeType.id())) {
            return (BaseRecipeCategory<WRAPPER>) (BaseRecipeCategory) new IsotopicRecipeCategory(helper);
        }
        throw new IllegalArgumentException("No 1.12 chemical-to-chemical delegate for recipe type " + recipeType.id());
    }
}
