package mekanism.common.recipe.cache;

import mekanism.api.gas.GasStack;
import mekanism.common.recipe.cache.inputs.IInputHandler;
import mekanism.common.recipe.cache.outputs.IOutputHandler;
import mekanism.common.recipe.inputs.ChemicalPairInput;
import mekanism.common.recipe.outputs.GasOutput;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public class ChemicalPairCachedRecipe<RECIPE> extends CachedRecipe<RECIPE> {

    private final IInputHandler<GasStack, GasStack> leftInputHandler;
    private final IInputHandler<GasStack, GasStack> rightInputHandler;
    private final IOutputHandler<GasStack> outputHandler;
    private final ChemicalPairInput input;
    private final GasOutput recipeOutput;

    private GasStack leftRecipeInput;
    private GasStack rightRecipeInput;
    private GasStack output;

    public ChemicalPairCachedRecipe(RECIPE recipe, BooleanSupplier recheckAllErrors, IInputHandler<GasStack, GasStack> leftInputHandler,
          IInputHandler<GasStack, GasStack> rightInputHandler,
          IOutputHandler<GasStack> outputHandler, ChemicalPairInput input, GasOutput output) {
        super(recipe, recheckAllErrors);
        this.leftInputHandler = Objects.requireNonNull(leftInputHandler);
        this.rightInputHandler = Objects.requireNonNull(rightInputHandler);
        this.outputHandler = Objects.requireNonNull(outputHandler);
        this.input = Objects.requireNonNull(input);
        this.recipeOutput = Objects.requireNonNull(output);
    }

    @Override
    protected void calculateOperationsThisTick(OperationTracker tracker) {
        super.calculateOperationsThisTick(tracker);
        if (tracker.shouldContinueChecking()) {
            GasStack leftInput = leftInputHandler.getInput();
            if (isEmpty(leftInput)) {
                tracker.mismatchedRecipe();
                return;
            }
            GasStack rightInput = rightInputHandler.getInput();
            if (isEmpty(rightInput)) {
                tracker.mismatchedRecipe();
                return;
            }
            if (!matchesType(leftInput, input.leftGas) || !matchesType(rightInput, input.rightGas)) {
                CachedRecipeHelper.twoInputCalculateOperationsThisTick(tracker, leftInputHandler, () -> input.rightGas, rightInputHandler,
                      () -> input.leftGas, (left, right) -> {
                          leftRecipeInput = left;
                          rightRecipeInput = right;
                      }, outputHandler, (left, right) -> recipeOutput.output, output -> this.output = output, this::isEmpty, this::isEmpty);
            } else {
                CachedRecipeHelper.twoInputCalculateOperationsThisTick(tracker, leftInputHandler, () -> input.leftGas, rightInputHandler,
                      () -> input.rightGas, (left, right) -> {
                          leftRecipeInput = left;
                          rightRecipeInput = right;
                      }, outputHandler, (left, right) -> recipeOutput.output, output -> this.output = output, this::isEmpty, this::isEmpty);
            }
        }
    }

    @Override
    public boolean isInputValid() {
        GasStack leftInput = leftInputHandler.getInput();
        if (isEmpty(leftInput)) {
            return false;
        }
        GasStack rightInput = rightInputHandler.getInput();
        return !isEmpty(rightInput) && (matches(leftInput, input.leftGas) && matches(rightInput, input.rightGas) ||
                                        matches(leftInput, input.rightGas) && matches(rightInput, input.leftGas));
    }

    @Override
    protected void finishProcessing(int operations) {
        if (!isEmpty(leftRecipeInput) && !isEmpty(rightRecipeInput) && !isEmpty(output)) {
            leftInputHandler.use(leftRecipeInput, operations);
            rightInputHandler.use(rightRecipeInput, operations);
            outputHandler.handleOutput(output, operations);
        }
    }

    private boolean matches(GasStack stored, GasStack recipeInput) {
        return !isEmpty(stored) && !isEmpty(recipeInput) && stored.isGasEqual(recipeInput) && stored.amount >= recipeInput.amount;
    }

    private boolean matchesType(GasStack stored, GasStack recipeInput) {
        return !isEmpty(stored) && !isEmpty(recipeInput) && stored.isGasEqual(recipeInput);
    }

    private boolean isEmpty(GasStack stack) {
        return stack == null || stack.amount <= 0;
    }
}
