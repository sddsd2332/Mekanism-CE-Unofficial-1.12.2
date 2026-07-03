package mekanism.common.recipe.cache;

public class FactoryRecipeCacheLookupMonitor<RECIPE> extends RecipeCacheLookupMonitor<RECIPE> {

    private final Runnable setSortingNeeded;

    public FactoryRecipeCacheLookupMonitor(IRecipeLookupHandler<RECIPE> handler, int cacheIndex, Runnable setSortingNeeded) {
        super(handler, cacheIndex);
        this.setSortingNeeded = setSortingNeeded;
    }

    @Override
    public void onChange() {
        super.onChange();
        setSortingNeeded.run();
    }

    public void updateCachedRecipe(RECIPE recipe) {
        cachedRecipe = createNewCachedRecipe(recipe, cacheIndex);
        hasNoRecipe = false;
    }
}
