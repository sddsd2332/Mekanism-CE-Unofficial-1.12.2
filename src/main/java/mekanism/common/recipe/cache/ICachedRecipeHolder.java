package mekanism.common.recipe.cache;

import javax.annotation.Nullable;

public interface ICachedRecipeHolder<RECIPE> {

    @Nullable
    default CachedRecipe<RECIPE> getUpdatedCache(int cacheIndex) {
        boolean cacheInvalid = invalidateCache();
        CachedRecipe<RECIPE> currentCache = cacheInvalid ? null : getCachedRecipe(cacheIndex);
        if (currentCache == null || !currentCache.isInputValid()) {
            if (cacheInvalid || !hasNoRecipe(cacheIndex)) {
                RECIPE recipe = getRecipe(cacheIndex);
                if (recipe == null) {
                    setHasNoRecipe(cacheIndex);
                } else {
                    CachedRecipe<RECIPE> cached = createNewCachedRecipe(recipe, cacheIndex);
                    if (currentCache == null || cached != null) {
                        if (currentCache == null && cached != null) {
                            loadSavedData(cached, cacheIndex);
                        }
                        return cached;
                    }
                }
            }
        }
        return currentCache;
    }

    default void loadSavedData(CachedRecipe<RECIPE> cached, int cacheIndex) {
        cached.loadSavedOperatingTicks(getSavedOperatingTicks(cacheIndex));
    }

    default int getSavedOperatingTicks(int cacheIndex) {
        return 0;
    }

    @Nullable
    CachedRecipe<RECIPE> getCachedRecipe(int cacheIndex);

    @Nullable
    RECIPE getRecipe(int cacheIndex);

    @Nullable
    CachedRecipe<RECIPE> createNewCachedRecipe(RECIPE recipe, int cacheIndex);

    default boolean invalidateCache() {
        return false;
    }

    default void setHasNoRecipe(int cacheIndex) {
    }

    default boolean hasNoRecipe(int cacheIndex) {
        return false;
    }
}
