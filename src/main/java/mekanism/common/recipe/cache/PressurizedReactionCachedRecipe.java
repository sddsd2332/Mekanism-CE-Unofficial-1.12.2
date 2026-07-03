package mekanism.common.recipe.cache;

import mekanism.api.gas.GasStack;
import mekanism.common.recipe.cache.inputs.IInputHandler;
import mekanism.common.recipe.cache.outputs.IOutputHandler;
import mekanism.common.recipe.machines.PressurizedRecipe;
import mekanism.common.recipe.outputs.PressurizedOutput;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public class PressurizedReactionCachedRecipe extends CachedRecipe<PressurizedRecipe> {

    private final IInputHandler<ItemStack, ItemStack> itemInputHandler;
    private final IInputHandler<FluidStack, FluidStack> fluidInputHandler;
    private final IInputHandler<GasStack, GasStack> gasInputHandler;
    private final IOutputHandler<PressurizedOutput> outputHandler;

    private ItemStack recipeItem = ItemStack.EMPTY;
    @Nullable
    private FluidStack recipeFluid;
    @Nullable
    private GasStack recipeGas;
    @Nullable
    private PressurizedOutput output;

    public PressurizedReactionCachedRecipe(PressurizedRecipe recipe, BooleanSupplier recheckAllErrors, IInputHandler<ItemStack, ItemStack> itemInputHandler,
          IInputHandler<FluidStack, FluidStack> fluidInputHandler, IInputHandler<GasStack, GasStack> gasInputHandler,
          IOutputHandler<PressurizedOutput> outputHandler) {
        super(recipe, recheckAllErrors);
        this.itemInputHandler = Objects.requireNonNull(itemInputHandler);
        this.fluidInputHandler = Objects.requireNonNull(fluidInputHandler);
        this.gasInputHandler = Objects.requireNonNull(gasInputHandler);
        this.outputHandler = Objects.requireNonNull(outputHandler);
    }

    @Override
    protected void calculateOperationsThisTick(OperationTracker tracker) {
        super.calculateOperationsThisTick(tracker);
        if (tracker.shouldContinueChecking()) {
            recipeItem = itemInputHandler.getRecipeInput(recipe.getInput().getSolid());
            if (recipeItem.isEmpty()) {
                tracker.mismatchedRecipe();
                return;
            }
            recipeFluid = fluidInputHandler.getRecipeInput(recipe.getInput().getFluid());
            if (recipeFluid == null || recipeFluid.amount <= 0) {
                tracker.mismatchedRecipe();
                return;
            }
            recipeGas = gasInputHandler.getRecipeInput(recipe.getInput().getGas());
            if (recipeGas == null || recipeGas.amount <= 0) {
                tracker.mismatchedRecipe();
                return;
            }
            itemInputHandler.calculateOperationsCanSupport(tracker, recipeItem);
            if (tracker.shouldContinueChecking()) {
                fluidInputHandler.calculateOperationsCanSupport(tracker, recipeFluid);
                if (tracker.shouldContinueChecking()) {
                    gasInputHandler.calculateOperationsCanSupport(tracker, recipeGas);
                    if (tracker.shouldContinueChecking()) {
                        output = recipe.getOutput(recipeItem, recipeFluid, recipeGas);
                        outputHandler.calculateOperationsCanSupport(tracker, output);
                    }
                }
            }
        }
    }

    @Override
    public boolean isInputValid() {
        ItemStack item = itemInputHandler.getInput();
        if (item.isEmpty()) {
            return false;
        }
        GasStack gas = gasInputHandler.getInput();
        if (gas == null || gas.amount <= 0) {
            return false;
        }
        FluidStack fluid = fluidInputHandler.getInput();
        return fluid != null && fluid.amount > 0 && recipe.test(item, fluid, gas);
    }

    @Override
    protected void finishProcessing(int operations) {
        if (!recipeItem.isEmpty() && recipeFluid != null && recipeFluid.amount > 0 && recipeGas != null && recipeGas.amount > 0 && output != null) {
            itemInputHandler.use(recipeItem, operations);
            fluidInputHandler.use(recipeFluid, operations);
            gasInputHandler.use(recipeGas, operations);
            outputHandler.handleOutput(output, operations);
        }
    }
}
