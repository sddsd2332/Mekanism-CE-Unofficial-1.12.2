package mekanism.common.recipe.cache;

import mekanism.api.gas.GasStack;
import mekanism.api.math.MathUtils;
import mekanism.common.recipe.cache.ItemStackConstantGasCachedRecipe.GasUsageMultiplier;
import mekanism.common.recipe.cache.inputs.IInputHandler;
import mekanism.common.recipe.cache.outputs.IOutputHandler;
import mekanism.common.recipe.machines.FarmMachineRecipe;
import mekanism.common.recipe.outputs.FarmOutput;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/**
 * Constant secondary-use cache for Organic Farm recipes backed by either gas or fluid.
 */
public class ItemStackConstantFarmCachedRecipe<RECIPE extends FarmMachineRecipe<RECIPE>> extends CachedRecipe<RECIPE> {

    private final IInputHandler<ItemStack, ItemStack> itemInputHandler;
    private final IInputHandler<GasStack, GasStack> gasInputHandler;
    private final IInputHandler<FluidStack, FluidStack> fluidInputHandler;
    private final IOutputHandler<FarmOutput> outputHandler;
    private final GasUsageMultiplier secondaryUsage;
    private final LongConsumer secondaryUsedSoFarChanged;
    private final boolean gasRecipe;

    private long secondaryUsageMultiplier;
    private long secondaryUsedSoFar;
    private ItemStack recipeItem = ItemStack.EMPTY;
    private GasStack recipeGas;
    private FluidStack recipeFluid;
    private FarmOutput output;

    public ItemStackConstantFarmCachedRecipe(RECIPE recipe, BooleanSupplier recheckAllErrors,
          IInputHandler<ItemStack, ItemStack> itemInputHandler, IInputHandler<GasStack, GasStack> gasInputHandler,
          IInputHandler<FluidStack, FluidStack> fluidInputHandler, IOutputHandler<FarmOutput> outputHandler,
          GasUsageMultiplier secondaryUsage, LongConsumer secondaryUsedSoFarChanged) {
        super(recipe, recheckAllErrors);
        this.itemInputHandler = Objects.requireNonNull(itemInputHandler);
        this.gasInputHandler = Objects.requireNonNull(gasInputHandler);
        this.fluidInputHandler = Objects.requireNonNull(fluidInputHandler);
        this.outputHandler = Objects.requireNonNull(outputHandler);
        this.secondaryUsage = Objects.requireNonNull(secondaryUsage);
        this.secondaryUsedSoFarChanged = Objects.requireNonNull(secondaryUsedSoFarChanged);
        gasRecipe = recipe.getInput().isGasInput();
    }

    public void loadSavedUsageSoFar(long secondaryUsedSoFar) {
        if (secondaryUsedSoFar > 0) {
            this.secondaryUsedSoFar = secondaryUsedSoFar;
        }
    }

    @Override
    protected void setupVariableValues() {
        secondaryUsageMultiplier = Math.max(secondaryUsage.getToUse(secondaryUsedSoFar, getOperatingTicks()), 0);
    }

    @Override
    public java.util.Map<String, Long> getPlanInputMultipliers() {
        return java.util.Collections.singletonMap(gasRecipe ? "gas.1" : "fluid.1", secondaryUsageMultiplier);
    }

    @Override
    protected void calculateOperationsThisTick(OperationTracker tracker) {
        super.calculateOperationsThisTick(tracker);
        if (!tracker.shouldContinueChecking()) {
            return;
        }
        recipeItem = itemInputHandler.getRecipeInput(recipe.getItemInput());
        if (recipeItem.isEmpty()) {
            tracker.mismatchedRecipe();
            return;
        }
        if (gasRecipe) {
            recipeGas = gasInputHandler.getRecipeInput(recipe.getGasInput());
            if (recipeGas == null || recipeGas.amount <= 0) {
                tracker.updateOperations(0);
                return;
            }
        } else {
            recipeFluid = fluidInputHandler.getRecipeInput(recipe.getFluidInput());
            if (recipeFluid == null || recipeFluid.amount <= 0) {
                tracker.updateOperations(0);
                return;
            }
        }
        itemInputHandler.calculateOperationsCanSupport(tracker, recipeItem);
        if (!tracker.shouldContinueChecking()) {
            return;
        }
        int usage = MathUtils.clampToInt(secondaryUsageMultiplier);
        if (gasRecipe) {
            gasInputHandler.calculateOperationsCanSupport(tracker, recipeGas, usage);
            if (tracker.shouldContinueChecking()) {
                output = recipe.getOutput(recipeItem, recipeGas);
            }
        } else {
            fluidInputHandler.calculateOperationsCanSupport(tracker, recipeFluid, usage);
            if (tracker.shouldContinueChecking()) {
                output = recipe.getOutput(recipeItem, recipeFluid);
            }
        }
        if (tracker.shouldContinueChecking()) {
            outputHandler.calculateOperationsCanSupport(tracker, output);
        }
    }

    @Override
    protected void useResources(int operations) {
        super.useResources(operations);
        if (secondaryUsageMultiplier <= 0) {
            return;
        }
        int toUse = MathUtils.clampToInt(operations * secondaryUsageMultiplier);
        useSecondary(toUse);
        secondaryUsedSoFar += toUse;
        secondaryUsedSoFarChanged.accept(secondaryUsedSoFar);
    }

    private void useSecondary(int operations) {
        if (gasRecipe) {
            gasInputHandler.use(recipeGas, operations);
        } else {
            fluidInputHandler.use(recipeFluid, operations);
        }
    }

    @Override
    protected void resetCache() {
        super.resetCache();
        secondaryUsedSoFar = 0;
        secondaryUsedSoFarChanged.accept(0);
    }

    @Override
    protected void applyPlannedResourceState(RecipeLaneSnapshot snapshot, RecipeLanePlan plan) {
        secondaryUsedSoFar += plan.getOperations() * snapshot.getPerTickInputMultiplier(gasRecipe ? "gas.1" : "fluid.1");
        secondaryUsedSoFarChanged.accept(secondaryUsedSoFar);
    }

    @Override
    public boolean isInputValid() {
        ItemStack itemInput = itemInputHandler.getInput();
        if (itemInput.isEmpty()) {
            return false;
        }
        if (gasRecipe) {
            GasStack gasInput = gasInputHandler.getInput();
            return gasInput != null && gasInput.amount > 0 && recipe.test(itemInput, gasInput);
        }
        FluidStack fluidInput = fluidInputHandler.getInput();
        return fluidInput != null && fluidInput.amount > 0 && recipe.test(itemInput, fluidInput);
    }

    @Override
    protected void finishProcessing(int operations) {
        if (recipeItem.isEmpty() || output == null || recipe.isOutputEmpty(output) || gasRecipe && recipeGas == null || !gasRecipe && recipeFluid == null) {
            return;
        }
        itemInputHandler.use(recipeItem, operations);
        if (secondaryUsageMultiplier > 0) {
            useSecondary(MathUtils.clampToInt(operations * secondaryUsageMultiplier));
        }
        outputHandler.handleOutput(output, operations);
    }
}
