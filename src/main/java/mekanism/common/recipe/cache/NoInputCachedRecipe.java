package mekanism.common.recipe.cache;

import mekanism.common.recipe.cache.outputs.IOutputHandler;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class NoInputCachedRecipe<OUTPUT, RECIPE> extends CachedRecipe<RECIPE> {

    private final BooleanSupplier inputValid;
    private final IOutputHandler<OUTPUT> outputHandler;
    private final Supplier<OUTPUT> outputGetter;
    private final Predicate<OUTPUT> outputEmptyCheck;

    @Nullable
    private OUTPUT output;

    public NoInputCachedRecipe(RECIPE recipe, BooleanSupplier recheckAllErrors, BooleanSupplier inputValid, IOutputHandler<OUTPUT> outputHandler,
          Supplier<OUTPUT> outputGetter, Predicate<OUTPUT> outputEmptyCheck) {
        super(recipe, recheckAllErrors);
        this.inputValid = Objects.requireNonNull(inputValid);
        this.outputHandler = Objects.requireNonNull(outputHandler);
        this.outputGetter = Objects.requireNonNull(outputGetter);
        this.outputEmptyCheck = Objects.requireNonNull(outputEmptyCheck);
    }

    @Override
    protected void calculateOperationsThisTick(OperationTracker tracker) {
        super.calculateOperationsThisTick(tracker);
        if (tracker.shouldContinueChecking()) {
            if (!inputValid.getAsBoolean()) {
                tracker.mismatchedRecipe();
                return;
            }
            OUTPUT output = outputGetter.get();
            this.output = output;
            if (outputEmptyCheck.test(output)) {
                tracker.mismatchedRecipe();
                return;
            }
            outputHandler.calculateOperationsCanSupport(tracker, output);
        }
    }

    @Override
    public boolean isInputValid() {
        return inputValid.getAsBoolean();
    }

    @Override
    protected void finishProcessing(int operations) {
        if (output != null && !outputEmptyCheck.test(output)) {
            outputHandler.handleOutput(output, operations);
        }
    }
}
