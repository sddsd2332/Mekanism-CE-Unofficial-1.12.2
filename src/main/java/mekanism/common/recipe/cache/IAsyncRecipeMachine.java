package mekanism.common.recipe.cache;

import mekanism.api.IAsyncMachinePlanner;
import mekanism.api.IAsyncPlanCalculator;
import mekanism.common.Mekanism;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.tile.prefab.TileEntityElectricBlock;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.LinkedHashMap;
import mekanism.common.base.IActiveState;

/**
 * Explicit opt-in for specialized recipe machines which do not inherit
 * TileEntityBasicMachine. Implementations provide only a main-thread recipe source
 * and a main-thread commit callback; all worker behavior is value-only.
 */
public interface IAsyncRecipeMachine extends IAsyncMachinePlanner<RecipeRunSnapshot, RecipeExecutionPlan> {

    /** Returns the current recipe or a deterministic collection of lane recipes. Main thread only. */
    @Nullable
    Object getAsyncRecipeSnapshotSource();

    /** Re-simulates and commits one machine tick. Main thread only. */
    default void commitAsyncRecipeTick() {
        if (mekanism.common.concurrent.TaskExecutor.isWorkerThread()) {
            throw new IllegalStateException("Recipe commits must run on the server thread");
        }
        RecipeRunSnapshot snapshot = captureSnapshot();
        RecipeExecutionPlan plan = calculatePlan(snapshot);
        if (plan != null && isPlanStillValid(snapshot, plan)) {
            RecipeRandomContext.run(snapshot.getRandomSeed(), () -> commitPlan(snapshot, plan));
        } else {
            onPlanDiscarded(snapshot, plan, null);
        }
    }

    /** Container loading, sorting and world observations before recipe matching. Server thread only. */
    default void prepareAsyncRecipeTick() {
    }

    default TileEntityBasicBlock getAsyncRecipeTile() {
        if (!(this instanceof TileEntityBasicBlock)) {
            throw new IllegalStateException("IAsyncRecipeMachine must be implemented by TileEntityBasicBlock");
        }
        return (TileEntityBasicBlock) this;
    }

    default long getAsyncRecipeCategoryGeneration() {
        return RecipeHandler.getGlobalRecipeGeneration();
    }

    /** Version of machine configuration/mode captured with the recipe snapshot. */
    default long getAsyncConfigurationVersion() {
        return getAsyncRecipeTile().getProcessingStateVersion();
    }

    /** QIO lease revision, when the machine participates in an automation lease. */
    default long getAsyncQioLeaseVersion() {
        return getAsyncRecipeTile().getAsyncLeaseVersion();
    }

    /** QIO port ownership revision, when the machine exposes leased ports. */
    default long getAsyncPortOwnershipVersion() {
        return getAsyncRecipeTile().getAsyncPortOwnershipVersion();
    }

    /** Stable mode discriminator for multi-mode machines. */
    default String getAsyncMode() {
        return "";
    }

    /** Main-thread value snapshots for machines with independently progressing lanes. */
    default Map<Integer, RecipeLaneSnapshot> getAsyncRecipeLaneSnapshots() {
        Map<Integer, RecipeLaneSnapshot> lanes = new LinkedHashMap<>();
        boolean active = getAsyncRecipeTile() instanceof IActiveState && ((IActiveState) getAsyncRecipeTile()).getActive();
        getAsyncRecipeCommitTargets().forEach((lane, target) -> lanes.put(lane, target.captureSnapshot(lane, getAsyncMode(), active)));
        return lanes;
    }

    /** Live handler bindings and resolved caches; this map stays on the server thread. */
    default Map<Integer, RecipeLaneCommitTarget> getAsyncRecipeCommitTargets() {
        return Collections.emptyMap();
    }

    /** Whole-machine activity, save and packet updates after every lane has committed. */
    default void afterAsyncRecipeCommit(RecipeRunSnapshot snapshot, RecipeExecutionPlan plan) {
    }

    /**
     * Replaces the synchronization work historically queued by
     * TileEntityElectricBlock.onAsyncUpdateServer(). It is deliberately queued
     * after all plan commits so a comparator or neighboring machine observes the
     * atomically committed state of this tick.
     */
    default void scheduleAsyncTileSyncTask() {
        // Optional integrations can be absent in headless addon tests. A missing
        // integration must not invalidate an already committed recipe plan.
        try {
            if (getAsyncRecipeTile() instanceof TileEntityElectricBlock) {
                TileEntityElectricBlock electric = (TileEntityElectricBlock) getAsyncRecipeTile();
                Mekanism.EXECUTE_MANAGER.addSyncTask(electric::addTileSyncTask);
            }
        } catch (LinkageError ignored) {
            // The normal game distribution provides all optional linkage.
        }
    }

    @Override
    default RecipeRunSnapshot captureSnapshot() {
        prepareAsyncRecipeTick();
        getAsyncRecipeCommitTargets().values().forEach(RecipeLaneCommitTarget::prepareForPlan);
        return AsyncMachinePlanSupport.capture(getAsyncRecipeTile(), getAsyncRecipeSnapshotSource(),
              getAsyncRecipeCategoryGeneration(), getAsyncConfigurationVersion(),
              getAsyncQioLeaseVersion(), getAsyncPortOwnershipVersion(), getAsyncMode(),
              getAsyncRecipeLaneSnapshots());
    }

    @Override
    default RecipeExecutionPlan calculatePlan(RecipeRunSnapshot snapshot) {
        return RecipeExecutionPlanner.calculate(snapshot);
    }

    @Override
    default IAsyncPlanCalculator<RecipeRunSnapshot, RecipeExecutionPlan> getAsyncPlanCalculator() {
        return RecipeExecutionPlanner.detachedCalculator();
    }

    @Override
    default void commitPlan(RecipeRunSnapshot snapshot, RecipeExecutionPlan plan) {
        Map<Integer, RecipeLaneCommitTarget> targets = getAsyncRecipeCommitTargets();
        if (targets.isEmpty()) {
            throw new IllegalStateException("Async recipe machine has no atomic commit targets: " +
                  getAsyncRecipeTile().getClass().getName());
        } else if (RecipePlanCommitter.commit(getAsyncRecipeTile(), snapshot, plan, targets,
              getAsyncRecipeTile() instanceof TileEntityElectricBlock ?
                    ((TileEntityElectricBlock) getAsyncRecipeTile()).getMainEnergyContainer() : null,
              () -> isPlanStillValid(snapshot, plan))) {
            afterAsyncRecipeCommit(snapshot, plan);
            scheduleAsyncTileSyncTask();
        }
    }

    @Override
    default boolean isPlanStillValid(RecipeRunSnapshot snapshot, RecipeExecutionPlan plan) {
        return snapshot.getConfigurationVersion() == getAsyncConfigurationVersion() &&
              snapshot.getQioLeaseVersion() == getAsyncQioLeaseVersion() &&
              snapshot.getPortOwnershipVersion() == getAsyncPortOwnershipVersion() &&
              snapshot.getMode().equals(getAsyncMode()) &&
              AsyncMachinePlanSupport.isStillValid(getAsyncRecipeTile(), snapshot, plan,
                    getAsyncRecipeSnapshotSource(), getAsyncRecipeCategoryGeneration());
    }
}
