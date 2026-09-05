package mekanism.common.recipe.cache;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.energy.IEnergyContainer;
import mekanism.common.concurrent.TaskExecutor;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.function.*;

public abstract class CachedRecipe<RECIPE> {

    protected final RECIPE recipe;
    private final BooleanSupplier recheckAllErrors;
    private Set<OperationTracker.RecipeError> errors = Collections.emptySet();
    private boolean pausedForErrors;
    private Set<OperationTracker.RecipeError> simulatedPlanErrors;

    private BooleanSupplier canHolderFunction = () -> true;
    private Consumer<Boolean> setActive = active -> {
    };
    private IntSupplier requiredTicks = () -> 1;
    private Runnable onFinish = () -> {
    };
    private DoubleSupplier perTickEnergy = () -> 0;
    private DoubleSupplier storedEnergy = () -> 0;
    private Consumer<Double> useEnergy = energy -> {
    };
    private IntSupplier baselineMaxOperations = () -> 1;
    private Consumer<OperationTracker> postProcessOperations = tracker -> {
    };
    private Consumer<Set<OperationTracker.RecipeError>> onErrorsChange = errors -> {
    };
    private IntConsumer operatingTicksChanged = ticks -> {
    };
    private int operatingTicks;

    protected CachedRecipe(RECIPE recipe, BooleanSupplier recheckAllErrors) {
        this.recipe = Objects.requireNonNull(recipe, "Recipe cannot be null.");
        this.recheckAllErrors = Objects.requireNonNull(recheckAllErrors, "Recheck all errors supplier cannot be null.");
    }

    public CachedRecipe<RECIPE> setCanHolderFunction(BooleanSupplier canHolderFunction) {
        this.canHolderFunction = Objects.requireNonNull(canHolderFunction);
        return this;
    }

    public CachedRecipe<RECIPE> setActive(Consumer<Boolean> setActive) {
        this.setActive = Objects.requireNonNull(setActive);
        return this;
    }

    public CachedRecipe<RECIPE> setEnergyRequirements(DoubleSupplier perTickEnergy, IEnergyContainer energyContainer) {
        this.perTickEnergy = Objects.requireNonNull(perTickEnergy);
        Objects.requireNonNull(energyContainer);
        this.storedEnergy = energyContainer::getEnergy;
        this.useEnergy = energy -> energyContainer.extract(energy, Action.EXECUTE, AutomationType.INTERNAL);
        return this;
    }

    public CachedRecipe<RECIPE> setRequiredTicks(IntSupplier requiredTicks) {
        this.requiredTicks = Objects.requireNonNull(requiredTicks);
        return this;
    }

    public CachedRecipe<RECIPE> setOperatingTicksChanged(IntConsumer operatingTicksChanged) {
        this.operatingTicksChanged = Objects.requireNonNull(operatingTicksChanged);
        return this;
    }

    public CachedRecipe<RECIPE> setOnFinish(Runnable onFinish) {
        this.onFinish = Objects.requireNonNull(onFinish);
        return this;
    }

    public CachedRecipe<RECIPE> setBaselineMaxOperations(IntSupplier baselineMaxOperations) {
        this.baselineMaxOperations = Objects.requireNonNull(baselineMaxOperations);
        return this;
    }

    public CachedRecipe<RECIPE> setPostProcessOperations(Consumer<OperationTracker> postProcessOperations) {
        this.postProcessOperations = Objects.requireNonNull(postProcessOperations);
        return this;
    }

    public CachedRecipe<RECIPE> setErrorsChanged(Consumer<Set<OperationTracker.RecipeError>> onErrorsChange) {
        this.onErrorsChange = Objects.requireNonNull(onErrorsChange);
        return this;
    }

    public void unpauseErrors() {
        pausedForErrors = false;
    }

    public void loadSavedOperatingTicks(int operatingTicks) {
        if (operatingTicks > 0 && operatingTicks < getRequiredTicks()) {
            this.operatingTicks = operatingTicks;
        }
    }

    public void process() {
        if (TaskExecutor.isWorkerThread()) {
            throw new IllegalStateException("CachedRecipe.process must run on the server thread");
        }
        if (pausedForErrors) {
            setActive.accept(false);
            return;
        }
        int operations;
        if (canHolderFunction.getAsBoolean()) {
            setupVariableValues();
            OperationTracker tracker = new OperationTracker(errors, recheckAllErrors.getAsBoolean(), Math.max(1, baselineMaxOperations.getAsInt()));
            calculateOperationsThisTick(tracker);
            if (tracker.shouldContinueChecking()) {
                postProcessOperations.accept(tracker);
                if (tracker.shouldContinueChecking() && tracker.capAtMaxForEnergy()) {
                    tracker.addError(OperationTracker.RecipeError.NOT_ENOUGH_ENERGY_REDUCED_RATE);
                }
            }
            operations = tracker.currentMax;
            if (tracker.hasErrorsToCopy()) {
                updateErrors(tracker.errors);
            }
        } else {
            operations = 0;
            if (!errors.isEmpty()) {
                updateErrors(Collections.emptySet());
            }
        }
        if (operations > 0) {
            setActive.accept(true);
            useEnergy(operations);
            operatingTicks++;
            int ticksRequired = getRequiredTicks();
            if (operatingTicks >= ticksRequired) {
                operatingTicks = 0;
                finishProcessing(operations);
                onFinish.run();
                resetCache();
            } else {
                useResources(operations);
            }
            if (ticksRequired > 1) {
                operatingTicksChanged.accept(operatingTicks);
            }
        } else {
            setActive.accept(false);
            if (operations < 0) {
                operatingTicks = 0;
                operatingTicksChanged.accept(operatingTicks);
                resetCache();
            }
        }
    }

    /** Explicit server-thread name for migrated machines. */
    public final void processOnServer() {
        process();
    }

    /** Captures variable recipe parameters, including secondary-use randomness, exactly once on the server. */
    public final void preparePlanValues() {
        if (TaskExecutor.isWorkerThread()) throw new IllegalStateException("Recipe capture must run on the server thread");
        if (canHolderFunction.getAsBoolean() && !pausedForErrors) setupVariableValues();
    }

    RecipeLaneSnapshot.Builder laneSnapshotBuilder(int lane) {
        return RecipeLaneSnapshot.builder(lane).operatingTicks(operatingTicks).requiredTicks(getRequiredTicks())
              .baselineMaxOperations(canHolderFunction.getAsBoolean() && !pausedForErrors ? Math.max(1, baselineMaxOperations.getAsInt()) : 0)
              .energyPerTick(Math.max(0, perTickEnergy.getAsDouble()))
              .pausedForErrors(pausedForErrors)
              .perTickInputMultipliers(getPlanInputMultipliers()).errors(AsyncMachinePlanSupport.captureErrors(this));
    }

    public Map<String, Long> getPlanInputMultipliers() {
        return Collections.emptyMap();
    }

    public final boolean isPausedForErrors() { return pausedForErrors; }

    /** Final server-thread simulation. No progress, resources, or observable callbacks are changed. */
    public final boolean simulatePlan(RecipeLaneSnapshot snapshot, RecipeLanePlan plan) {
        if (TaskExecutor.isWorkerThread()) throw new IllegalStateException("Recipe simulation must run on the server thread");
        simulatedPlanErrors = null;
        if (!snapshot.isRecipePresent()) {
            simulatedPlanErrors = Collections.emptySet();
            return plan.getOperations() == 0;
        }
        if (snapshot.getOperatingTicks() != operatingTicks || snapshot.getRequiredTicks() != getRequiredTicks()) return false;
        if (pausedForErrors) {
            simulatedPlanErrors = errors;
            return plan.getOperations() == 0;
        }
        if (!canHolderFunction.getAsBoolean()) {
            simulatedPlanErrors = Collections.emptySet();
            return plan.getOperations() == 0;
        }
        OperationTracker tracker = new OperationTracker(errors, true, Math.max(1, baselineMaxOperations.getAsInt()));
        calculateOperationsThisTick(tracker);
        if (tracker.shouldContinueChecking()) postProcessOperations.accept(tracker);
        if (tracker.capAtMaxForEnergy() && tracker.currentMax > 0) tracker.addError(OperationTracker.RecipeError.NOT_ENOUGH_ENERGY_REDUCED_RATE);
        if (snapshot.getMaxProcessingPasses() == 1 && (tracker.currentMax <= 0 || tracker.currentMax == plan.getOperations())) {
            simulatedPlanErrors = tracker.errors;
        }
        double currentRate = Math.max(0, perTickEnergy.getAsDouble());
        boolean sameEnergy = snapshot.getEnergyPerTick() >= 0 ? Double.compare(currentRate, snapshot.getEnergyPerTick()) == 0 :
              Double.compare(currentRate * plan.getOperations(), plan.getEnergyAsDouble()) == 0;
        return (plan.getOperations() == 0 || tracker.currentMax >= plan.getFirstPassOperations()) && sameEnergy;
    }

    /** Applies only cache state after all lane resource mutations have committed successfully. */
    public final void applyPlanState(RecipeLaneSnapshot snapshot, RecipeLanePlan plan) {
        stagePlanState(snapshot, plan).run();
    }

    final Runnable stagePlanState(RecipeLaneSnapshot snapshot, RecipeLanePlan plan) {
        if (TaskExecutor.isWorkerThread()) throw new IllegalStateException("Recipe state must run on the server thread");
        operatingTicks = plan.getNewOperatingTicks();
        Set<OperationTracker.RecipeError> plannedErrors = simulatedPlanErrors == null ?
              AsyncMachinePlanSupport.resolveErrors(plan.getErrors()) : simulatedPlanErrors;
        updateErrors(plannedErrors);
        if (plan.getOperations() > 0) {
            if (plan.getCompletedPasses() > 0) resetCache();
            if (operatingTicks > 0) applyPlannedResourceState(snapshot, plan);
        } else if (snapshot.getOperatingTicks() > 0 && operatingTicks == 0) {
            resetCache();
        }
        operatingTicksChanged.accept(operatingTicks);
        return () -> {
            setActive.accept(plan.isActive());
            for (int completed = 0; completed < plan.getCompletedPasses(); completed++) onFinish.run();
        };
    }

    protected void applyPlannedResourceState(RecipeLaneSnapshot snapshot, RecipeLanePlan plan) {
    }

    /** Returns a defensive view of the currently tracked recipe errors. */
    public final Set<OperationTracker.RecipeError> getErrors() {
        return Collections.unmodifiableSet(new ObjectOpenHashSet<>(errors));
    }

    /**
     * Pure fallback planner for migrations. Specialized machines may override this
     * method and use their own immutable snapshot data; the default never touches an
     * input/output handler or the live recipe object.
     */
    public RecipeExecutionPlan calculatePlan(RecipeRunSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "Recipe snapshot cannot be null");
        return RecipeExecutionPlanner.calculate(snapshot, 1, snapshot.getEnergyPerTick(),
              Math.max(1, snapshot.getRequiredTicks()));
    }

    /** Validates a plan before a migrated machine applies it on the server thread. */
    public boolean isPlanValid(RecipeRunSnapshot snapshot, RecipeExecutionPlan plan) {
        return plan != null && plan.isValidFor(snapshot);
    }

    protected void setupVariableValues() {
    }

    protected int getOperatingTicks() {
        return operatingTicks;
    }

    protected int getRequiredTicks() {
        return Math.max(1, requiredTicks.getAsInt());
    }

    protected void useResources(int operations) {
    }

    protected void resetCache() {
    }

    protected void useEnergy(int operations) {
        double energy = perTickEnergy.getAsDouble();
        if (energy > 0) {
            useEnergy.accept(energy * operations);
        }
    }

    protected void calculateOperationsThisTick(OperationTracker tracker) {
        if (tracker.shouldContinueChecking()) {
            double energyPerTick = perTickEnergy.getAsDouble();
            if (energyPerTick > 0) {
                int operations = (int) (storedEnergy.getAsDouble() / energyPerTick);
                tracker.maxForEnergy = operations;
                if (operations == 0) {
                    tracker.updateOperations(0);
                    tracker.addError(OperationTracker.RecipeError.NOT_ENOUGH_ENERGY);
                }
            }
        }
    }

    private void updateErrors(Set<OperationTracker.RecipeError> errors) {
        if (!this.errors.equals(errors)) {
            this.errors = errors;
            if (this.errors.size() > 1) {
                pausedForErrors = true;
            } else {
                pausedForErrors = !this.errors.isEmpty() && !this.errors.contains(OperationTracker.RecipeError.NOT_ENOUGH_ENERGY_REDUCED_RATE);
            }
            onErrorsChange.accept(errors);
        }
    }

    public abstract boolean isInputValid();

    protected abstract void finishProcessing(int operations);

    public RECIPE getRecipe() {
        return recipe;
    }

    public static final class OperationTracker {

        private static final int RESET_PROGRESS = -1;
        private static final int MISMATCHED_RECIPE = -2;

        private final Set<RecipeError> lastErrors;
        private Set<RecipeError> errors = Collections.emptySet();
        private boolean checkAll;
        private boolean checkedErrors = true;
        private int currentMax;
        private int maxForEnergy;

        private OperationTracker(Set<RecipeError> lastErrors, boolean checkAll, int startingMax) {
            this.lastErrors = lastErrors;
            this.checkAll = checkAll;
            currentMax = startingMax;
            maxForEnergy = currentMax;
        }

        private boolean hasErrorsToCopy() {
            if (currentMax == MISMATCHED_RECIPE) {
                errors = Collections.emptySet();
                return true;
            } else if (checkAll || currentMax > 0) {
                return true;
            }
            return !checkedErrors && !lastErrors.containsAll(errors);
        }

        public boolean shouldContinueChecking() {
            if (currentMax > 0) {
                return true;
            } else if (currentMax == 0) {
                if (checkAll) {
                    return true;
                } else if (!checkedErrors) {
                    if (!lastErrors.containsAll(errors)) {
                        checkAll = true;
                        return true;
                    }
                    checkedErrors = true;
                }
            }
            return false;
        }

        public int getCurrentMaxOperations() {
            return currentMax;
        }

        public boolean updateOperations(int max) {
            if (max < currentMax) {
                currentMax = max;
                return true;
            }
            return false;
        }

        private boolean capAtMaxForEnergy() {
            return updateOperations(maxForEnergy);
        }

        public void mismatchedRecipe() {
            updateOperations(MISMATCHED_RECIPE);
        }

        public void resetProgress(RecipeError error) {
            updateOperations(RESET_PROGRESS);
            addError(error);
        }

        public void addError(RecipeError error) {
            Objects.requireNonNull(error, "Error cannot be null.");
            if (errors.isEmpty()) {
                errors = new ObjectOpenHashSet<>();
            }
            if (errors.add(error)) {
                checkedErrors = false;
            }
        }

        public static final class RecipeError {

            public static final RecipeError INPUT_DOESNT_PRODUCE_OUTPUT = create();
            public static final RecipeError NOT_ENOUGH_ENERGY = create();
            public static final RecipeError NOT_ENOUGH_ENERGY_REDUCED_RATE = create();
            public static final RecipeError NOT_ENOUGH_INPUT = create();
            public static final RecipeError NOT_ENOUGH_SECONDARY_INPUT = create();
            public static final RecipeError NOT_ENOUGH_LEFT_INPUT = create();
            public static final RecipeError NOT_ENOUGH_RIGHT_INPUT = create();
            public static final RecipeError NOT_ENOUGH_OUTPUT_SPACE = create();

            public static RecipeError create() {
                return new RecipeError();
            }

            private RecipeError() {
            }
        }
    }
}
