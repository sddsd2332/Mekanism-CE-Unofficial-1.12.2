package mekanism.common.recipe.cache;

import java.util.Collections;

/** Fuel remainder and generated energy to apply together in one server transaction. */
public final class GasFuelPlan extends RecipeExecutionPlan {
    public final ImmutableResourceSnapshot fuelAfter;
    public final int burnTicks;
    public final int maxBurnTicks;
    public final double generationRate;
    public final double output;
    public final double clientUsed;
    public final double generatedEnergy;

    GasFuelPlan(GasFuelSnapshot snapshot, int usedTicks, boolean active, ImmutableResourceSnapshot fuelAfter,
          int burnTicks, int maxBurnTicks, double generationRate, double output) {
        super(RecipeExecutionPlan.builder(snapshot.getRecipeId()).recipeSignature(snapshot.getRecipeSignature())
              .globalRecipeGeneration(snapshot.getGlobalRecipeGeneration()).categoryRecipeGeneration(snapshot.getCategoryRecipeGeneration())
              .machineStateVersion(snapshot.getMachineStateVersion()).configurationVersion(snapshot.getConfigurationVersion())
              .qioLeaseVersion(snapshot.getQioLeaseVersion()).portOwnershipVersion(snapshot.getPortOwnershipVersion())
              .mode(snapshot.getMode()).randomSeed(snapshot.getRandomSeed()).operations(usedTicks).active(active)
              .inputConsumption(Collections.singletonMap("gas.0", snapshot.fuelState.fuel.getAmount() - fuelAfter.getAmount())));
        this.fuelAfter = fuelAfter;
        this.burnTicks = burnTicks;
        this.maxBurnTicks = maxBurnTicks;
        this.generationRate = generationRate;
        this.output = output;
        this.clientUsed = active && maxBurnTicks > 0 ? usedTicks / (double) maxBurnTicks : 0;
        this.generatedEnergy = generationRate * usedTicks;
    }

    boolean sameResult(GasFuelPlan other) {
        return other != null && getOperations() == other.getOperations() && isActive() == other.isActive() &&
              fuelAfter.equals(other.fuelAfter) && burnTicks == other.burnTicks && maxBurnTicks == other.maxBurnTicks &&
              Double.compare(generationRate, other.generationRate) == 0 && Double.compare(output, other.output) == 0 &&
              Double.compare(clientUsed, other.clientUsed) == 0 && Double.compare(generatedEnergy, other.generatedEnergy) == 0;
    }
}
