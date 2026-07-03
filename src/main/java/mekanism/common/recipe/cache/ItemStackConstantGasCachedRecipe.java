package mekanism.common.recipe.cache;

import mekanism.api.gas.GasStack;
import mekanism.api.math.MathUtils;
import mekanism.common.recipe.cache.inputs.IInputHandler;
import mekanism.common.recipe.cache.outputs.IOutputHandler;
import net.minecraft.item.ItemStack;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

public class ItemStackConstantGasCachedRecipe<OUTPUT, RECIPE extends IConstantGasRecipe<OUTPUT>> extends CachedRecipe<RECIPE> {

    private final IInputHandler<ItemStack, ItemStack> itemInputHandler;
    private final IInputHandler<GasStack, GasStack> gasInputHandler;
    private final IOutputHandler<OUTPUT> outputHandler;
    private final GasUsageMultiplier gasUsage;
    private final LongConsumer gasUsedSoFarChanged;

    private long gasUsageMultiplier;
    private long gasUsedSoFar;

    private ItemStack recipeItem = ItemStack.EMPTY;
    private GasStack recipeGas;
    private OUTPUT output;

    public ItemStackConstantGasCachedRecipe(RECIPE recipe, BooleanSupplier recheckAllErrors, IInputHandler<ItemStack, ItemStack> itemInputHandler,
          IInputHandler<GasStack, GasStack> gasInputHandler, IOutputHandler<OUTPUT> outputHandler, GasUsageMultiplier gasUsage,
          LongConsumer gasUsedSoFarChanged) {
        super(recipe, recheckAllErrors);
        this.itemInputHandler = Objects.requireNonNull(itemInputHandler);
        this.gasInputHandler = Objects.requireNonNull(gasInputHandler);
        this.outputHandler = Objects.requireNonNull(outputHandler);
        this.gasUsage = Objects.requireNonNull(gasUsage);
        this.gasUsedSoFarChanged = Objects.requireNonNull(gasUsedSoFarChanged);
    }

    public void loadSavedUsageSoFar(long gasUsedSoFar) {
        if (gasUsedSoFar > 0) {
            this.gasUsedSoFar = gasUsedSoFar;
        }
    }

    @Override
    protected void setupVariableValues() {
        gasUsageMultiplier = Math.max(gasUsage.getToUse(gasUsedSoFar, getOperatingTicks()), 0);
    }

    @Override
    protected void calculateOperationsThisTick(OperationTracker tracker) {
        super.calculateOperationsThisTick(tracker);
        if (tracker.shouldContinueChecking()) {
            recipeItem = itemInputHandler.getRecipeInput(recipe.getItemInput());
            if (recipeItem.isEmpty()) {
                tracker.mismatchedRecipe();
                return;
            }
            recipeGas = gasInputHandler.getRecipeInput(recipe.getGasInput());
            if (recipeGas == null || recipeGas.amount <= 0) {
                tracker.updateOperations(0);
                if (!tracker.shouldContinueChecking()) {
                    return;
                }
            }
            itemInputHandler.calculateOperationsCanSupport(tracker, recipeItem);
            if (tracker.shouldContinueChecking() && recipeGas != null && recipeGas.amount > 0) {
                gasInputHandler.calculateOperationsCanSupport(tracker, recipeGas, MathUtils.clampToInt(gasUsageMultiplier));
                if (tracker.shouldContinueChecking()) {
                    output = recipe.getOutput(recipeItem, recipeGas);
                    outputHandler.calculateOperationsCanSupport(tracker, output);
                }
            }
        }
    }

    @Override
    protected void useResources(int operations) {
        super.useResources(operations);
        if (gasUsageMultiplier <= 0 || recipeGas == null || recipeGas.amount <= 0) {
            return;
        }
        long toUse = operations * gasUsageMultiplier;
        gasInputHandler.use(recipeGas, MathUtils.clampToInt(toUse));
        gasUsedSoFar += toUse;
        gasUsedSoFarChanged.accept(gasUsedSoFar);
    }

    @Override
    protected void resetCache() {
        super.resetCache();
        gasUsedSoFar = 0;
        gasUsedSoFarChanged.accept(gasUsedSoFar);
    }

    @Override
    public boolean isInputValid() {
        ItemStack itemInput = itemInputHandler.getInput();
        GasStack gasInput = gasInputHandler.getInput();
        if (!itemInput.isEmpty() && gasInput != null && gasInput.amount > 0 && recipe.test(itemInput, gasInput)) {
            GasStack recipeGas = gasInputHandler.getRecipeInput(recipe.getGasInput());
            return recipeGas != null && recipeGas.amount > 0 && gasInput.amount >= recipeGas.amount;
        }
        return false;
    }

    @Override
    protected void finishProcessing(int operations) {
        if (!recipeItem.isEmpty() && recipeGas != null && recipeGas.amount > 0 && output != null && !recipe.isOutputEmpty(output)) {
            itemInputHandler.use(recipeItem, operations);
            if (gasUsageMultiplier > 0) {
                gasInputHandler.use(recipeGas, MathUtils.clampToInt(operations * gasUsageMultiplier));
            }
            outputHandler.handleOutput(output, operations);
        }
    }

    @FunctionalInterface
    public interface GasUsageMultiplier {

        long getToUse(long usedSoFar, int operatingTicks);
    }
}
