package mekanism.common.recipe.cache;

import mekanism.api.gas.GasStack;
import mekanism.common.recipe.cache.inputs.IInputHandler;
import mekanism.common.recipe.cache.outputs.IOutputHandler;
import mekanism.common.recipe.machines.RotaryRecipe;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public class RotaryCachedRecipe extends CachedRecipe<RotaryRecipe> {

    private final IOutputHandler<GasStack> gasOutputHandler;
    private final IOutputHandler<FluidStack> fluidOutputHandler;
    private final IInputHandler<FluidStack, FluidStack> fluidInputHandler;
    private final IInputHandler<GasStack, GasStack> gasInputHandler;
    private final BooleanSupplier modeSupplier;

    @Nullable
    private FluidStack recipeFluid;
    @Nullable
    private GasStack recipeGas;
    @Nullable
    private FluidStack fluidOutput;
    @Nullable
    private GasStack gasOutput;

    public RotaryCachedRecipe(RotaryRecipe recipe, BooleanSupplier recheckAllErrors, IInputHandler<FluidStack, FluidStack> fluidInputHandler,
          IInputHandler<GasStack, GasStack> gasInputHandler, IOutputHandler<GasStack> gasOutputHandler, IOutputHandler<FluidStack> fluidOutputHandler,
          BooleanSupplier modeSupplier) {
        super(recipe, recheckAllErrors);
        this.fluidInputHandler = Objects.requireNonNull(fluidInputHandler, "Fluid input handler cannot be null.");
        this.gasInputHandler = Objects.requireNonNull(gasInputHandler, "Gas input handler cannot be null.");
        this.gasOutputHandler = Objects.requireNonNull(gasOutputHandler, "Gas output handler cannot be null.");
        this.fluidOutputHandler = Objects.requireNonNull(fluidOutputHandler, "Fluid output handler cannot be null.");
        this.modeSupplier = Objects.requireNonNull(modeSupplier, "Mode supplier cannot be null.");
    }

    @Override
    protected void setupVariableValues() {
        recipeFluid = null;
        recipeGas = null;
        fluidOutput = null;
        gasOutput = null;
    }

    @Override
    protected void calculateOperationsThisTick(OperationTracker tracker) {
        super.calculateOperationsThisTick(tracker);
        if (tracker.shouldContinueChecking()) {
            if (modeSupplier.getAsBoolean()) {
                if (!recipe.hasFluidToGas()) {
                    tracker.mismatchedRecipe();
                } else {
                    CachedRecipeHelper.oneInputCalculateOperationsThisTick(tracker, fluidInputHandler, recipe::getFluidInput, input -> recipeFluid = input,
                          gasOutputHandler, recipe::getGasOutput, output -> gasOutput = output, RotaryCachedRecipe::isFluidEmpty);
                }
            } else if (!recipe.hasGasToFluid()) {
                tracker.mismatchedRecipe();
            } else {
                CachedRecipeHelper.oneInputCalculateOperationsThisTick(tracker, gasInputHandler, recipe::getGasInput, input -> recipeGas = input,
                      fluidOutputHandler, recipe::getFluidOutput, output -> fluidOutput = output, RotaryCachedRecipe::isGasEmpty);
            }
        }
    }

    @Override
    public boolean isInputValid() {
        if (modeSupplier.getAsBoolean()) {
            FluidStack fluidStack = fluidInputHandler.getInput();
            return recipe.hasFluidToGas() && !isFluidEmpty(fluidStack) && recipe.test(fluidStack);
        }
        GasStack gasStack = gasInputHandler.getInput();
        return recipe.hasGasToFluid() && !isGasEmpty(gasStack) && recipe.test(gasStack);
    }

    @Override
    protected void finishProcessing(int operations) {
        if (modeSupplier.getAsBoolean()) {
            if (recipe.hasFluidToGas() && !isFluidEmpty(recipeFluid) && !isGasEmpty(gasOutput)) {
                fluidInputHandler.use(recipeFluid, operations);
                gasOutputHandler.handleOutput(gasOutput, operations);
            }
        } else if (recipe.hasGasToFluid() && !isGasEmpty(recipeGas) && !isFluidEmpty(fluidOutput)) {
            gasInputHandler.use(recipeGas, operations);
            fluidOutputHandler.handleOutput(fluidOutput, operations);
        }
    }

    private static boolean isGasEmpty(@Nullable GasStack stack) {
        return stack == null || stack.amount <= 0;
    }

    private static boolean isFluidEmpty(@Nullable FluidStack stack) {
        return stack == null || stack.amount <= 0;
    }
}
