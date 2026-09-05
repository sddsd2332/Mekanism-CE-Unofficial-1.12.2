package mekanism.common.recipe.cache;

import java.util.Collections;
import java.util.Objects;

/** Recipe identity/generation plus detached fuel arithmetic parameters. */
public final class GasFuelSnapshot extends RecipeRunSnapshot {
    public final GasFuelState fuelState;

    public GasFuelSnapshot(RecipeRunSnapshot recipe, GasFuelState state) {
        super(recipe.getRecipeId(), recipe.getRecipeSignature(), recipe.getGlobalRecipeGeneration(),
              recipe.getCategoryRecipeGeneration(), recipe.getLaneIndex(), recipe.getMachineStateVersion(),
              recipe.getRandomSeed(), 0, 1, state.storedEnergy, 0, recipe.isRedstonePowered(), recipe.isActive(),
              recipe.getDimension(), recipe.getWorldTime(), recipe.getInputs(), recipe.getOutputs(), recipe.getUpgrades(),
              recipe.getConfigurationVersion(), recipe.getQioLeaseVersion(), recipe.getPortOwnershipVersion(),
              recipe.getMode(), recipe.getRecipeSemantics(), Collections.emptyMap());
        fuelState = Objects.requireNonNull(state);
    }
}
