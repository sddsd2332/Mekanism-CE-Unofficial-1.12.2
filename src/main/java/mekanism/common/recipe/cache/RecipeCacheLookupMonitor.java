package mekanism.common.recipe.cache;

import mekanism.api.IContentsListener;
import mekanism.api.energy.IEnergyContainer;
import mekanism.common.CommonWorldTickHandler;
import mekanism.common.recipe.RecipeHandler;

import javax.annotation.Nullable;

public class RecipeCacheLookupMonitor<RECIPE> implements ICachedRecipeHolder<RECIPE>, IContentsListener {

    private final IRecipeLookupHandler<RECIPE> handler;
    protected final int cacheIndex;
    protected CachedRecipe<RECIPE> cachedRecipe;
    protected boolean hasNoRecipe;
    protected boolean shouldUnpause;
    private int cachedRecipeVersion = RecipeHandler.getGlobalRecipeVersion();
    private boolean observedRecipeFlush;

    public RecipeCacheLookupMonitor(IRecipeLookupHandler<RECIPE> handler) {
        this(handler, 0);
    }

    public RecipeCacheLookupMonitor(IRecipeLookupHandler<RECIPE> handler, int cacheIndex) {
        this.handler = handler;
        this.cacheIndex = cacheIndex;
    }

    @Override
    public final void onContentsChanged() {
        handler.onContentsChanged();
        onChange();
    }

    public void onChange() {
        hasNoRecipe = false;
        unpause();
    }

    public void unpause() {
        shouldUnpause = true;
    }

    public double updateAndProcess(IEnergyContainer energyContainer) {
        double prev = energyContainer.getEnergy();
        if (updateAndProcess()) {
            return Math.max(0, prev - energyContainer.getEnergy());
        }
        return 0;
    }

    public boolean updateAndProcess() {
        CachedRecipe<RECIPE> oldCache = cachedRecipe;
        cachedRecipe = getUpdatedCache(cacheIndex);
        if (cachedRecipe != oldCache) {
            handler.onCachedRecipeChanged(cachedRecipe, cacheIndex);
        }
        if (cachedRecipe != null) {
            if (shouldUnpause) {
                shouldUnpause = false;
                cachedRecipe.unpauseErrors();
            }
            cachedRecipe.process();
            return true;
        }
        return false;
    }

    @Override
    public void loadSavedData(CachedRecipe<RECIPE> cached, int cacheIndex) {
        if (cachedIndexMatches(cacheIndex)) {
            ICachedRecipeHolder.super.loadSavedData(cached, cacheIndex);
            if (cached instanceof ItemStackConstantGasCachedRecipe<?, ?> constantGasCached &&
                handler instanceof IRecipeLookupHandler.ConstantUsageRecipeLookupHandler constantUsageHandler) {
                constantGasCached.loadSavedUsageSoFar(constantUsageHandler.getSavedUsedSoFar(cacheIndex));
            } else if (cached instanceof ItemStackConstantFarmCachedRecipe<?> constantFarmCached &&
                handler instanceof IRecipeLookupHandler.ConstantUsageRecipeLookupHandler constantUsageHandler) {
                constantFarmCached.loadSavedUsageSoFar(constantUsageHandler.getSavedUsedSoFar(cacheIndex));
            }
        }
    }

    public void clear() {
        if (cachedRecipe != null) {
            cachedRecipe = null;
            handler.onCachedRecipeChanged(null, cacheIndex);
        }
        hasNoRecipe = false;
    }

    protected boolean cachedIndexMatches(int cacheIndex) {
        return this.cacheIndex == cacheIndex;
    }

    @Override
    public int getSavedOperatingTicks(int cacheIndex) {
        return cachedIndexMatches(cacheIndex) ? handler.getSavedOperatingTicks(cacheIndex) : ICachedRecipeHolder.super.getSavedOperatingTicks(cacheIndex);
    }

    @Nullable
    @Override
    public CachedRecipe<RECIPE> getCachedRecipe(int cacheIndex) {
        return cachedIndexMatches(cacheIndex) ? cachedRecipe : null;
    }

    @Nullable
    @Override
    public RECIPE getRecipe(int cacheIndex) {
        return cachedIndexMatches(cacheIndex) ? handler.getRecipe(cacheIndex) : null;
    }

    @Nullable
    @Override
    public CachedRecipe<RECIPE> createNewCachedRecipe(RECIPE recipe, int cacheIndex) {
        return cachedIndexMatches(cacheIndex) ? handler.createNewCachedRecipe(recipe, cacheIndex) : null;
    }

    @Override
    public void setHasNoRecipe(int cacheIndex) {
        if (cachedIndexMatches(cacheIndex)) {
            hasNoRecipe = true;
        }
    }

    @Override
    public boolean hasNoRecipe(int cacheIndex) {
        return cachedIndexMatches(cacheIndex) ? hasNoRecipe : ICachedRecipeHolder.super.hasNoRecipe(cacheIndex);
    }

    @Override
    public boolean invalidateCache() {
        int recipeVersion = RecipeHandler.getGlobalRecipeVersion();
        boolean flush = CommonWorldTickHandler.flushTagAndRecipeCaches;
        if (cachedRecipeVersion != recipeVersion) {
            cachedRecipeVersion = recipeVersion;
            observedRecipeFlush = flush;
            handler.onRecipeCacheInvalidated(cacheIndex);
            return true;
        }
        if (flush) {
            if (!observedRecipeFlush) {
                observedRecipeFlush = true;
                handler.onRecipeCacheInvalidated(cacheIndex);
            }
            return true;
        }
        observedRecipeFlush = false;
        return false;
    }
}
