package mekanism.client.recipe_viewer.jei;

import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;

public final class MekanismJEI {

    private MekanismJEI() {
    }

    public static String genericRecipeType(IRecipeViewerRecipeType<?> recipeType) {
        return recipeType(recipeType);
    }

    public static <TYPE> String recipeType(IRecipeViewerRecipeType<TYPE> recipeType) {
        return RecipeViewerRecipeType.categoryUid(recipeType);
    }

    public static <TYPE> String holderRecipeType(IRecipeViewerRecipeType<TYPE> recipeType) {
        return recipeType(recipeType);
    }

    public static String[] recipeType(IRecipeViewerRecipeType<?>... recipeTypes) {
        String[] uids = new String[recipeTypes.length];
        for (int i = 0; i < recipeTypes.length; i++) {
            uids[i] = genericRecipeType(recipeTypes[i]);
        }
        return uids;
    }
}
