package mekanism.common.recipe.cache.inputs;

import mekanism.common.recipe.cache.CachedRecipe.OperationTracker;

public interface IInputHandler<INPUT, INGREDIENT> {

    INPUT getInput();

    INPUT getRecipeInput(INGREDIENT recipeIngredient);

    void use(INPUT recipeInput, int operations);

    default void calculateOperationsCanSupport(OperationTracker tracker, INPUT recipeInput) {
        calculateOperationsCanSupport(tracker, recipeInput, 1);
    }

    void calculateOperationsCanSupport(OperationTracker tracker, INPUT recipeInput, int usageMultiplier);
}
