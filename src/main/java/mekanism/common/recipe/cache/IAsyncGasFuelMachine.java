package mekanism.common.recipe.cache;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IAsyncPlanCalculator;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.concurrent.TaskExecutor;
import mekanism.common.recipe.GasStackFuelToEnergyRecipe;
import mekanism.generators.common.tile.TileEntityGenerator;

/** Server bindings for fuel generators. Only GasFuelSnapshot and GasFuelPlan enter the worker. */
public interface IAsyncGasFuelMachine extends IAsyncRecipeMachine {
    IExtendedGasTank getAsyncFuelTank();

    GasFuelState getAsyncFuelState();

    /** Writes only primitive processing state, without listeners, networking or world access. */
    void applyAsyncFuelState(int burnTicks, int maxBurnTicks, double generationRate, double output, double clientUsed);

    /** Activity, comparator and save updates, after the complete resource/state transaction. */
    void afterAsyncFuelCommit(GasFuelPlan plan);

    default GasFuelState captureFuelState(int burnTicks, int maxBurnTicks, double generationRate, double clientUsed,
          long processes, boolean dynamicOutput, double resetOutput) {
        TileEntityGenerator tile = (TileEntityGenerator) getAsyncRecipeTile();
        GasStackFuelToEnergyRecipe recipe = (GasStackFuelToEnergyRecipe) getAsyncRecipeSnapshotSource();
        return new GasFuelState(ImmutableResourceSnapshot.of(getAsyncFuelTank().getGas()), getAsyncFuelTank().getCapacity(),
              burnTicks, maxBurnTicks, generationRate, tile.output, clientUsed, tile.getEnergy(), tile.getMaxEnergy(),
              recipe == null ? 0 : recipe.getInput().ingredient.amount, recipe == null ? 0 : recipe.getOutput().energyOutput,
              processes, recipe != null && tile.canOperate(),
              tile.getEnergyContainer().insert(generationRate, Action.SIMULATE, AutomationType.INTERNAL) == 0,
              dynamicOutput, resetOutput, dynamicOutput && processes > 1 ? processes : 0);
    }

    @Override
    default RecipeRunSnapshot captureSnapshot() {
        if (TaskExecutor.isWorkerThread()) throw new IllegalStateException("Fuel capture must run on the server thread");
        RecipeRunSnapshot recipe = IAsyncRecipeMachine.super.captureSnapshot();
        return new GasFuelSnapshot(recipe, getAsyncFuelState());
    }

    @Override
    default RecipeExecutionPlan calculatePlan(RecipeRunSnapshot snapshot) {
        return GasFuelPlanner.calculate((GasFuelSnapshot) snapshot);
    }

    @Override
    default void commitAsyncRecipeTick() {
        RecipeRunSnapshot snapshot = captureSnapshot();
        commitPlan(snapshot, calculatePlan(snapshot));
    }

    @Override
    default IAsyncPlanCalculator<RecipeRunSnapshot, RecipeExecutionPlan> getAsyncPlanCalculator() {
        return GasFuelPlanner.detachedCalculator();
    }

    @Override
    default boolean isPlanStillValid(RecipeRunSnapshot snapshot, RecipeExecutionPlan plan) {
        return snapshot instanceof GasFuelSnapshot && plan instanceof GasFuelPlan &&
              IAsyncRecipeMachine.super.isPlanStillValid(snapshot, plan) &&
              ((GasFuelSnapshot) snapshot).fuelState.equals(getAsyncFuelState());
    }

    @Override
    default void commitPlan(RecipeRunSnapshot snapshot, RecipeExecutionPlan plan) {
        if (TaskExecutor.isWorkerThread()) throw new IllegalStateException("Fuel commits must run on the server thread");
        if (!isPlanStillValid(snapshot, plan)) return;
        GasFuelSnapshot fuelSnapshot = (GasFuelSnapshot) snapshot;
        GasFuelPlan fuelPlan = (GasFuelPlan) plan;
        if (!GasFuelPlanner.calculate(fuelSnapshot).sameResult(fuelPlan)) return;
        GasFuelState before = fuelSnapshot.fuelState;
        TileEntityGenerator tile = (TileEntityGenerator) getAsyncRecipeTile();
        IExtendedGasTank fuel = getAsyncFuelTank();
        MachineEnergyContainer energy = tile.getEnergyContainer();
        int consumed = (int) (before.fuel.getAmount() - fuelPlan.fuelAfter.getAmount());
        double afterEnergy = before.storedEnergy + fuelPlan.generatedEnergy;
        if (consumed < 0 || fuelPlan.fuelAfter.getAmount() > fuel.getCapacity() || !Double.isFinite(afterEnergy)) return;
        boolean committed = AtomicPlanCommitter.commit(tile, () -> isPlanStillValid(snapshot, plan),
              new AtomicPlanCommitter.Operation(
                    () -> ImmutableResourceSnapshot.of(fuel.getGas()).equals(before.fuel) &&
                          (consumed == 0 || ImmutableResourceSnapshot.of(fuel.extract(consumed, Action.SIMULATE, AutomationType.INTERNAL))
                                .equals(before.fuel.withAmount(consumed))),
                    () -> {
                        if (consumed > 0) fuel.setStackUncheckedNoUpdate(fuelPlan.fuelAfter.getGasCopy());
                        return ImmutableResourceSnapshot.of(fuel.getGas()).equals(fuelPlan.fuelAfter);
                    }, () -> fuel.setStackUncheckedNoUpdate(before.fuel.getGasCopy())),
              new AtomicPlanCommitter.Operation(
                    () -> Double.compare(energy.getEnergy(), before.storedEnergy) == 0 && afterEnergy <= energy.getMaxEnergy() &&
                          energy.insert(fuelPlan.generatedEnergy, Action.SIMULATE, AutomationType.INTERNAL) == 0,
                    () -> {
                        tile.electricityStored.set(afterEnergy);
                        return Double.compare(energy.getEnergy(), afterEnergy) == 0;
                    }, () -> tile.electricityStored.set(before.storedEnergy)),
              new AtomicPlanCommitter.Operation(() -> true,
                    () -> {
                        applyAsyncFuelState(fuelPlan.burnTicks, fuelPlan.maxBurnTicks, fuelPlan.generationRate, fuelPlan.output, fuelPlan.clientUsed);
                        return true;
                    }, () -> applyAsyncFuelState(before.burnTicks, before.maxBurnTicks, before.generationRate, before.output, before.clientUsed)));
        if (!committed) return;
        tile.invalidateProcessingState();
        afterAsyncFuelCommit(fuelPlan);
        scheduleAsyncTileSyncTask();
        if (consumed > 0) fuel.onContentsChanged();
        if (fuelPlan.generatedEnergy > 0) energy.onContentsChanged();
    }
}
