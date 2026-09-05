package mekanism.common.recipe;

import mekanism.common.recipe.cache.ImmutableResourceSnapshot;

import java.util.Map;

/** Compatibility facade for the recipe package; the implementation lives with cache APIs. */
@Deprecated
public class RecipeRunSnapshot extends mekanism.common.recipe.cache.RecipeRunSnapshot {

    public RecipeRunSnapshot(String recipeId, String recipeSignature, long globalRecipeGeneration,
          long categoryRecipeGeneration, int laneIndex, long machineStateVersion, long randomSeed,
          int operatingTicks, int requiredTicks, double storedEnergy, double energyPerTick,
          boolean redstonePowered, boolean active, int dimension, long worldTime,
          Map<String, ImmutableResourceSnapshot> inputs, Map<String, ImmutableResourceSnapshot> outputs,
          Map<String, Integer> upgrades) {
        super(recipeId, recipeSignature, globalRecipeGeneration, categoryRecipeGeneration, laneIndex,
              machineStateVersion, randomSeed, operatingTicks, requiredTicks, storedEnergy, energyPerTick,
              redstonePowered, active, dimension, worldTime, inputs, outputs, upgrades);
    }

    public RecipeRunSnapshot(String recipeId, long globalRecipeGeneration, long machineStateVersion,
          long randomSeed) {
        super(recipeId, globalRecipeGeneration, machineStateVersion, randomSeed);
    }

    public RecipeRunSnapshot(String recipeId, String recipeSignature, long globalRecipeGeneration,
          long categoryRecipeGeneration, int laneIndex, long machineStateVersion, long randomSeed,
          int operatingTicks, int requiredTicks, double storedEnergy, double energyPerTick,
          boolean redstonePowered, boolean active, int dimension, long worldTime,
          Map<String, ImmutableResourceSnapshot> inputs, Map<String, ImmutableResourceSnapshot> outputs,
          Map<String, Integer> upgrades, long configurationVersion, long qioLeaseVersion,
          long portOwnershipVersion, String mode) {
        super(recipeId, recipeSignature, globalRecipeGeneration, categoryRecipeGeneration, laneIndex,
              machineStateVersion, randomSeed, operatingTicks, requiredTicks, storedEnergy, energyPerTick,
              redstonePowered, active, dimension, worldTime, inputs, outputs, upgrades,
              configurationVersion, qioLeaseVersion, portOwnershipVersion, mode);
    }
}
