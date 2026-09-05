package mekanism.common.recipe;

import mekanism.common.recipe.cache.ImmutableResourceSnapshot;

import java.util.Map;
import java.util.Set;

/** Compatibility facade for the recipe package; the implementation lives with cache APIs. */
@Deprecated
public class RecipeExecutionPlan extends mekanism.common.recipe.cache.RecipeExecutionPlan {

    public RecipeExecutionPlan(String recipeId, String recipeSignature, long globalRecipeGeneration,
          long categoryRecipeGeneration, int laneIndex, long machineStateVersion, int operations,
          double energy, int newOperatingTicks, boolean active, Map<String, Long> inputConsumption,
          Map<String, ImmutableResourceSnapshot> outputs, Set<String> errors, long randomSeed) {
        super(recipeId, recipeSignature, globalRecipeGeneration, categoryRecipeGeneration, laneIndex,
              machineStateVersion, operations, energy, newOperatingTicks, active, inputConsumption,
              outputs, errors, randomSeed);
    }

    public RecipeExecutionPlan(String recipeId, long globalRecipeGeneration, long machineStateVersion,
          int operations) {
        super(recipeId, globalRecipeGeneration, machineStateVersion, operations);
    }

    public RecipeExecutionPlan(String recipeId, String recipeSignature, long globalRecipeGeneration,
          long categoryRecipeGeneration, int laneIndex, long machineStateVersion, int operations,
          double energy, int newOperatingTicks, boolean active, Map<String, Long> inputConsumption,
          Map<String, ImmutableResourceSnapshot> outputs, Set<String> errors, long randomSeed,
          long configurationVersion, long qioLeaseVersion, long portOwnershipVersion, String mode) {
        super(recipeId, recipeSignature, globalRecipeGeneration, categoryRecipeGeneration, laneIndex,
              machineStateVersion, operations, energy, newOperatingTicks, active, inputConsumption,
              outputs, errors, randomSeed, configurationVersion, qioLeaseVersion,
              portOwnershipVersion, mode);
    }
}
