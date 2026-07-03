package mekanism.common.recipe.cache;

import mekanism.api.IContentsListener;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;

import javax.annotation.Nullable;

public interface IRecipeLookupHandler<RECIPE> extends IContentsListener {

    default int getSavedOperatingTicks(int cacheIndex) {
        return 0;
    }

    @Nullable
    RECIPE getRecipe(int cacheIndex);

    @Nullable
    default IRecipeViewerRecipeType<RECIPE> recipeViewerType() {
        return null;
    }

    CachedRecipe<RECIPE> createNewCachedRecipe(RECIPE recipe, int cacheIndex);

    default void onCachedRecipeChanged(@Nullable CachedRecipe<RECIPE> cachedRecipe, int cacheIndex) {
        clearRecipeErrors(cacheIndex);
    }

    default void clearRecipeErrors(int cacheIndex) {
    }

    default void onRecipeCacheInvalidated(int cacheIndex) {
    }

    interface ConstantUsageRecipeLookupHandler {

        default long getSavedUsedSoFar(int cacheIndex) {
            return 0;
        }
    }
}
