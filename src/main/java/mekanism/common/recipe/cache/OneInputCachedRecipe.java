package mekanism.common.recipe.cache;

import mekanism.common.recipe.cache.inputs.IInputHandler;
import mekanism.common.recipe.cache.outputs.IOutputHandler;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class OneInputCachedRecipe<INPUT, OUTPUT, INGREDIENT, RECIPE> extends CachedRecipe<RECIPE> {

    private final IInputHandler<INPUT, INGREDIENT> inputHandler;
    private final IOutputHandler<OUTPUT> outputHandler;
    private final Predicate<INPUT> inputEmptyCheck;
    private final Supplier<INGREDIENT> inputSupplier;
    private final Predicate<INPUT> recipeTest;
    private final Function<INPUT, OUTPUT> outputGetter;
    private final Predicate<OUTPUT> outputEmptyCheck;

    @Nullable
    private INPUT input;
    @Nullable
    private OUTPUT output;

    public OneInputCachedRecipe(RECIPE recipe, BooleanSupplier recheckAllErrors, IInputHandler<INPUT, INGREDIENT> inputHandler,
          IOutputHandler<OUTPUT> outputHandler, Supplier<INGREDIENT> inputSupplier, Predicate<INPUT> recipeTest,
          Function<INPUT, OUTPUT> outputGetter, Predicate<INPUT> inputEmptyCheck, Predicate<OUTPUT> outputEmptyCheck) {
        super(recipe, recheckAllErrors);
        this.inputHandler = Objects.requireNonNull(inputHandler);
        this.outputHandler = Objects.requireNonNull(outputHandler);
        this.inputSupplier = Objects.requireNonNull(inputSupplier);
        this.recipeTest = Objects.requireNonNull(recipeTest);
        this.outputGetter = Objects.requireNonNull(outputGetter);
        this.inputEmptyCheck = Objects.requireNonNull(inputEmptyCheck);
        this.outputEmptyCheck = Objects.requireNonNull(outputEmptyCheck);
    }

    @Override
    protected void calculateOperationsThisTick(OperationTracker tracker) {
        super.calculateOperationsThisTick(tracker);
        CachedRecipeHelper.oneInputCalculateOperationsThisTick(tracker, inputHandler, inputSupplier, input -> this.input = input, outputHandler, outputGetter,
              output -> this.output = output, inputEmptyCheck);
    }

    @Override
    public boolean isInputValid() {
        INPUT input = inputHandler.getInput();
        return !inputEmptyCheck.test(input) && recipeTest.test(input);
    }

    @Override
    protected void finishProcessing(int operations) {
        if (input != null && output != null && !inputEmptyCheck.test(input) && !outputEmptyCheck.test(output)) {
            inputHandler.use(input, operations);
            outputHandler.handleOutput(output, operations);
        }
    }
}
