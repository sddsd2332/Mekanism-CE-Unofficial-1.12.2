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
    private long cachedRecipeGeneration = RecipeHandler.getGlobalRecipeGeneration();
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
        prepareCache();
        if (cachedRecipe != null) {
            cachedRecipe.process();
            return true;
        }
        return false;
    }

    /** Resolves and unpauses the live cache without consuming resources or advancing progress. */
    @Nullable
    public CachedRecipe<RECIPE> prepareCache() {
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
        }
        return cachedRecipe;
    }

    /**
     * Resolves the live cache on the server thread and performs only pure planning
     * against the supplied immutable snapshot. No handler is read or written by the
     * calculation itself.
     */
    @Nullable
    public RecipeExecutionPlan calculatePlan(RecipeRunSnapshot snapshot) {
        CachedRecipe<RECIPE> current = cachedRecipe;
        return current == null ? null : current.calculatePlan(snapshot);
    }

    /** Returns whether a worker plan still matches the captured immutable values. */
    public boolean isPlanValid(RecipeRunSnapshot snapshot, RecipeExecutionPlan plan) {
        CachedRecipe<RECIPE> current = cachedRecipe;
        return current != null && current.isPlanValid(snapshot, plan);
    }

    /**
     * Re-resolves a cache after an atomic plan only when its live inputs no
     * longer match. Unlike {@link #prepareCache()}, this deliberately ignores
     * the world-wide flush flag because the plan already captured and checked
     * the current recipe generation.
     */
    public void refreshAfterPlanCommit() {
        CachedRecipe<RECIPE> current = cachedRecipe;
        if (current == null || current.isInputValid()) return;
        RECIPE recipe = getRecipe(cacheIndex);
        CachedRecipe<RECIPE> replacement = recipe == null ? null : createNewCachedRecipe(recipe, cacheIndex);
        cachedRecipe = replacement;
        hasNoRecipe = replacement == null;
        if (replacement != current) handler.onCachedRecipeChanged(replacement, cacheIndex);
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

    /** Generation captured by this monitor's current lookup cache. */
    public long getRecipeGeneration() {
        return cachedRecipeGeneration;
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
        long recipeGeneration = RecipeHandler.getGlobalRecipeGeneration();
        boolean flush = CommonWorldTickHandler.flushTagAndRecipeCaches;
        if (cachedRecipeGeneration != recipeGeneration) {
            cachedRecipeGeneration = recipeGeneration;
            cachedRecipeVersion = RecipeHandler.getGlobalRecipeVersion();
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
