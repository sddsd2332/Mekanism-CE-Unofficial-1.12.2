package mekanism.common.recipe.cache;

import mekanism.common.recipe.cache.CachedRecipe.OperationTracker;
import mekanism.common.recipe.cache.inputs.IInputHandler;
import mekanism.common.recipe.cache.outputs.IOutputHandler;

import java.util.function.*;

public final class CachedRecipeHelper {

    private CachedRecipeHelper() {
    }

    public static <INPUT, OUTPUT, INGREDIENT> void oneInputCalculateOperationsThisTick(OperationTracker tracker, IInputHandler<INPUT, INGREDIENT> inputHandler,
          Supplier<INGREDIENT> inputIngredient, Consumer<INPUT> inputSetter, IOutputHandler<OUTPUT> outputHandler, Function<INPUT, OUTPUT> outputGetter,
          Consumer<OUTPUT> outputSetter, Predicate<INPUT> emptyCheck) {
        if (tracker.shouldContinueChecking()) {
            INPUT input = inputHandler.getRecipeInput(inputIngredient.get());
            inputSetter.accept(input);
            if (emptyCheck.test(input)) {
                tracker.mismatchedRecipe();
            } else {
                inputHandler.calculateOperationsCanSupport(tracker, input);
                if (tracker.shouldContinueChecking()) {
                    OUTPUT output = outputGetter.apply(input);
                    outputSetter.accept(output);
                    outputHandler.calculateOperationsCanSupport(tracker, output);
                }
            }
        }
    }

    public static <INPUT_A, INPUT_B, OUTPUT, INGREDIENT_A, INGREDIENT_B> void twoInputCalculateOperationsThisTick(OperationTracker tracker,
          IInputHandler<INPUT_A, INGREDIENT_A> inputAHandler, Supplier<INGREDIENT_A> inputAIngredient, IInputHandler<INPUT_B, INGREDIENT_B> inputBHandler,
          Supplier<INGREDIENT_B> inputBIngredient, BiConsumer<INPUT_A, INPUT_B> inputsSetter, IOutputHandler<OUTPUT> outputHandler,
          BiFunction<INPUT_A, INPUT_B, OUTPUT> outputGetter, Consumer<OUTPUT> outputSetter, Predicate<INPUT_A> emptyCheckA, Predicate<INPUT_B> emptyCheckB) {
        if (tracker.shouldContinueChecking()) {
            INPUT_A inputA = inputAHandler.getRecipeInput(inputAIngredient.get());
            if (emptyCheckA.test(inputA)) {
                tracker.mismatchedRecipe();
            } else {
                INPUT_B inputB = inputBHandler.getRecipeInput(inputBIngredient.get());
                if (emptyCheckB.test(inputB)) {
                    tracker.mismatchedRecipe();
                } else {
                    inputsSetter.accept(inputA, inputB);
                    inputAHandler.calculateOperationsCanSupport(tracker, inputA);
                    if (tracker.shouldContinueChecking()) {
                        inputBHandler.calculateOperationsCanSupport(tracker, inputB);
                        if (tracker.shouldContinueChecking()) {
                            OUTPUT output = outputGetter.apply(inputA, inputB);
                            outputSetter.accept(output);
                            outputHandler.calculateOperationsCanSupport(tracker, output);
                        }
                    }
                }
            }
        }
    }

    public static <INPUT_A, INPUT_B, INPUT_C, OUTPUT, INGREDIENT_A, INGREDIENT_B, INGREDIENT_C> void threeInputCalculateOperationsThisTick(
          OperationTracker tracker, IInputHandler<INPUT_A, INGREDIENT_A> inputAHandler, Supplier<INGREDIENT_A> inputAIngredient,
          IInputHandler<INPUT_B, INGREDIENT_B> inputBHandler, Supplier<INGREDIENT_B> inputBIngredient, IInputHandler<INPUT_C, INGREDIENT_C> inputCHandler,
          Supplier<INGREDIENT_C> inputCIngredient, TriConsumer<INPUT_A, INPUT_B, INPUT_C> inputsSetter, IOutputHandler<OUTPUT> outputHandler,
          TriFunction<INPUT_A, INPUT_B, INPUT_C, OUTPUT> outputGetter, Consumer<OUTPUT> outputSetter, Predicate<INPUT_A> emptyCheckA,
          Predicate<INPUT_B> emptyCheckB, Predicate<INPUT_C> emptyCheckC) {
        if (tracker.shouldContinueChecking()) {
            INPUT_A inputA = inputAHandler.getRecipeInput(inputAIngredient.get());
            if (emptyCheckA.test(inputA)) {
                tracker.mismatchedRecipe();
            } else {
                INPUT_B inputB = inputBHandler.getRecipeInput(inputBIngredient.get());
                if (emptyCheckB.test(inputB)) {
                    tracker.mismatchedRecipe();
                } else {
                    INPUT_C inputC = inputCHandler.getRecipeInput(inputCIngredient.get());
                    if (emptyCheckC.test(inputC)) {
                        tracker.mismatchedRecipe();
                    } else {
                        inputsSetter.accept(inputA, inputB, inputC);
                        inputAHandler.calculateOperationsCanSupport(tracker, inputA);
                        if (tracker.shouldContinueChecking()) {
                            inputBHandler.calculateOperationsCanSupport(tracker, inputB);
                            if (tracker.shouldContinueChecking()) {
                                inputCHandler.calculateOperationsCanSupport(tracker, inputC);
                                if (tracker.shouldContinueChecking()) {
                                    OUTPUT output = outputGetter.apply(inputA, inputB, inputC);
                                    outputSetter.accept(output);
                                    outputHandler.calculateOperationsCanSupport(tracker, output);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @FunctionalInterface
    public interface TriConsumer<A, B, C> {

        void accept(A a, B b, C c);
    }

    @FunctionalInterface
    public interface TriFunction<A, B, C, R> {

        R apply(A a, B b, C c);
    }
}
