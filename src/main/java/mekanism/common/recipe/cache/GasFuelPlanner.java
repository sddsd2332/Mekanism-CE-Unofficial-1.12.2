package mekanism.common.recipe.cache;

import mekanism.api.IAsyncPlanCalculator;

/** Pure gas-fuel calculation shared by core, large and tiered generators. */
public final class GasFuelPlanner {
    private static final IAsyncPlanCalculator<RecipeRunSnapshot, RecipeExecutionPlan> CALCULATOR =
          snapshot -> calculate((GasFuelSnapshot) snapshot);

    private GasFuelPlanner() {
    }

    public static IAsyncPlanCalculator<RecipeRunSnapshot, RecipeExecutionPlan> detachedCalculator() {
        return CALCULATOR;
    }

    public static GasFuelPlan calculate(GasFuelSnapshot snapshot) {
        GasFuelState state = snapshot.fuelState;
        if (!state.canOperate) {
            return new GasFuelPlan(snapshot, 0, false, state.fuel, 0, 0, 0, state.resetOutput);
        }
        if (!state.canInsertCurrentRate) {
            return new GasFuelPlan(snapshot, 0, false, state.fuel, state.burnTicks, state.maxBurnTicks, state.generationRate, state.output);
        }
        int maxBurnTicks = state.fuel.isEmpty() ? state.maxBurnTicks : Math.max(1, state.recipeBurnTicks);
        double rate = state.fuel.isEmpty() ? state.generationRate : state.recipeEnergy;
        if (maxBurnTicks <= 0 || state.burnTicks < 0 || !Double.isFinite(rate) || rate < 0) {
            throw new IllegalArgumentException("Invalid gas fuel parameters");
        }
        // Large tanks exceed the range of an int when expressed in fuel burn ticks.
        long total = state.burnTicks + state.fuel.getAmount() * maxBurnTicks;
        int used = 0;
        if (rate > 0 && !state.fuel.isEmpty()) {
            int fullness = (int) Math.ceil(((float) state.fuel.getAmount() / (float) state.capacity) * 256F);
            long limit = (long) Math.min(Integer.MAX_VALUE, fullness * (double) Math.max(0, state.processes));
            used = (int) Math.max(0, Math.min(Math.min(total, limit), (state.maxEnergy - state.storedEnergy) / rate));
        }
        total -= used;
        ImmutableResourceSnapshot remaining = state.fuel.isEmpty() ? state.fuel : state.fuel.withAmount(total / maxBurnTicks);
        double output = state.dynamicOutput ? Math.max(state.resetOutput,
              rate * (state.outputProcesses > 0 ? state.outputProcesses : used) * 2) : state.output;
        return new GasFuelPlan(snapshot, used, true, remaining, (int) (total % maxBurnTicks), maxBurnTicks, rate, output);
    }
}
