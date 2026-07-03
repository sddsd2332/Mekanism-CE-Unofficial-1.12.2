package mekanism.common.recipe.cache.outputs;

import mekanism.common.recipe.cache.CachedRecipe.OperationTracker;

public interface IOutputHandler<OUTPUT> {

    void handleOutput(OUTPUT toOutput, int operations);

    void calculateOperationsCanSupport(OperationTracker tracker, OUTPUT toOutput);
}
