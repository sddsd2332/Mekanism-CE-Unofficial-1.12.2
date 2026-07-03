package mekanism.common.recipe.cache;

import mekanism.common.recipe.cache.inputs.IInputHandler;
import mekanism.common.recipe.cache.outputs.IOutputHandler;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.*;

public class TwoInputCachedRecipe<INPUT_A, INPUT_B, OUTPUT, INGREDIENT_A, INGREDIENT_B, RECIPE> extends CachedRecipe<RECIPE> {

    private final IInputHandler<INPUT_A, INGREDIENT_A> inputHandler;
    private final IInputHandler<INPUT_B, INGREDIENT_B> secondaryInputHandler;
    private final IOutputHandler<OUTPUT> outputHandler;
    private final Predicate<INPUT_A> inputEmptyCheck;
    private final Predicate<INPUT_B> secondaryInputEmptyCheck;
    private final Supplier<INGREDIENT_A> inputSupplier;
    private final Supplier<INGREDIENT_B> secondaryInputSupplier;
    private final BiPredicate<INPUT_A, INPUT_B> recipeTest;
    private final BiFunction<INPUT_A, INPUT_B, OUTPUT> outputGetter;
    private final Predicate<OUTPUT> outputEmptyCheck;

    @Nullable
    private INPUT_A input;
    @Nullable
    private INPUT_B secondaryInput;
    @Nullable
    private OUTPUT output;

    public TwoInputCachedRecipe(RECIPE recipe, BooleanSupplier recheckAllErrors, IInputHandler<INPUT_A, INGREDIENT_A> inputHandler,
          IInputHandler<INPUT_B, INGREDIENT_B> secondaryInputHandler, IOutputHandler<OUTPUT> outputHandler, Supplier<INGREDIENT_A> inputSupplier,
          Supplier<INGREDIENT_B> secondaryInputSupplier, BiPredicate<INPUT_A, INPUT_B> recipeTest, BiFunction<INPUT_A, INPUT_B, OUTPUT> outputGetter,
          Predicate<INPUT_A> inputEmptyCheck, Predicate<INPUT_B> secondaryInputEmptyCheck, Predicate<OUTPUT> outputEmptyCheck) {
        super(recipe, recheckAllErrors);
        this.inputHandler = Objects.requireNonNull(inputHandler);
        this.secondaryInputHandler = Objects.requireNonNull(secondaryInputHandler);
        this.outputHandler = Objects.requireNonNull(outputHandler);
        this.inputSupplier = Objects.requireNonNull(inputSupplier);
        this.secondaryInputSupplier = Objects.requireNonNull(secondaryInputSupplier);
        this.recipeTest = Objects.requireNonNull(recipeTest);
        this.outputGetter = Objects.requireNonNull(outputGetter);
        this.inputEmptyCheck = Objects.requireNonNull(inputEmptyCheck);
        this.secondaryInputEmptyCheck = Objects.requireNonNull(secondaryInputEmptyCheck);
        this.outputEmptyCheck = Objects.requireNonNull(outputEmptyCheck);
    }

    @Override
    protected void calculateOperationsThisTick(OperationTracker tracker) {
        super.calculateOperationsThisTick(tracker);
        CachedRecipeHelper.twoInputCalculateOperationsThisTick(tracker, inputHandler, inputSupplier, secondaryInputHandler, secondaryInputSupplier, (input, secondary) -> {
            this.input = input;
            secondaryInput = secondary;
        }, outputHandler, outputGetter, output -> this.output = output, inputEmptyCheck, secondaryInputEmptyCheck);
    }

    @Override
    public boolean isInputValid() {
        INPUT_A input = inputHandler.getInput();
        if (inputEmptyCheck.test(input)) {
            return false;
        }
        INPUT_B secondaryInput = secondaryInputHandler.getInput();
        return !secondaryInputEmptyCheck.test(secondaryInput) && recipeTest.test(input, secondaryInput);
    }

    @Override
    protected void finishProcessing(int operations) {
        if (input != null && secondaryInput != null && output != null && !inputEmptyCheck.test(input) && !secondaryInputEmptyCheck.test(secondaryInput)
              && !outputEmptyCheck.test(output)) {
            inputHandler.use(input, operations);
            secondaryInputHandler.use(secondaryInput, operations);
            outputHandler.handleOutput(output, operations);
        }
    }
}
