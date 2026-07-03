package mekanism.common.recipe.cache;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.energy.IEnergyContainer;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.function.*;

public abstract class CachedRecipe<RECIPE> {

    protected final RECIPE recipe;
    private final BooleanSupplier recheckAllErrors;
    private Set<OperationTracker.RecipeError> errors = Collections.emptySet();
    private boolean pausedForErrors;

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
                pausedForErrors = !this.errors.contains(OperationTracker.RecipeError.NOT_ENOUGH_ENERGY_REDUCED_RATE);
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
