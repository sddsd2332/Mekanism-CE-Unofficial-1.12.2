package mekanism.common.recipe.cache.inputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.InfuseStorage;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.inputs.MachineInput;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public final class InputHelper {

    private InputHelper() {
    }

    public static IInputHandler<ItemStack, ItemStack> getInputHandler(IInventorySlot slot, RecipeError notEnoughError) {
        return getInputHandler(slot, notEnoughError, () -> true);
    }

    public static IInputHandler<ItemStack, ItemStack> getInputHandler(IInventorySlot slot, RecipeError notEnoughError, BooleanSupplier deplete) {
        return new IInputHandler<>() {

            @Override
            public ItemStack getInput() {
                return slot.getStack();
            }

            @Override
            public ItemStack getRecipeInput(ItemStack recipeIngredient) {
                ItemStack input = getInput();
                return !input.isEmpty() && MachineInput.inputContains(input, recipeIngredient) ? recipeIngredient.copy() : ItemStack.EMPTY;
            }

            @Override
            public void use(ItemStack recipeInput, int operations) {
                if (deplete.getAsBoolean() && !recipeInput.isEmpty() && operations > 0) {
                    slot.shrinkStack(recipeInput.getCount() * operations, Action.EXECUTE);
                }
            }

            @Override
            public void calculateOperationsCanSupport(OperationTracker tracker, ItemStack recipeInput, int usageMultiplier) {
                if (usageMultiplier <= 0) {
                    return;
                }
                if (!recipeInput.isEmpty()) {
                    int operations = getInput().getCount() / (recipeInput.getCount() * usageMultiplier);
                    if (operations > 0) {
                        tracker.updateOperations(operations);
                        return;
                    }
                }
                tracker.resetProgress(notEnoughError);
            }
        };
    }

    public static IInputHandler<GasStack, GasStack> getGasInputHandler(IExtendedGasTank tank, RecipeError notEnoughError) {
        return getGasInputHandler(tank, notEnoughError, () -> true);
    }

    public static IInputHandler<GasStack, GasStack> getGasInputHandler(IExtendedGasTank tank, RecipeError notEnoughError, BooleanSupplier deplete) {
        return new IInputHandler<>() {

            @Override
            public GasStack getInput() {
                GasStack stack = tank.getGas();
                return stack == null ? null : stack.copy();
            }

            @Override
            public GasStack getRecipeInput(GasStack recipeIngredient) {
                GasStack stack = tank.getGas();
                if (stack == null || recipeIngredient == null || !stack.isGasEqual(recipeIngredient)) {
                    return null;
                }
                return recipeIngredient.copy();
            }

            @Override
            public void use(GasStack recipeInput, int operations) {
                if (deplete.getAsBoolean() && recipeInput != null && recipeInput.amount > 0 && operations > 0) {
                    tank.extract(recipeInput.amount * operations, Action.EXECUTE, AutomationType.INTERNAL);
                }
            }

            @Override
            public void calculateOperationsCanSupport(OperationTracker tracker, GasStack recipeInput, int usageMultiplier) {
                if (recipeInput != null && recipeInput.amount > 0) {
                    int amount = recipeInput.amount * usageMultiplier;
                    if (tank.getStored() >= amount) {
                        tracker.updateOperations(tank.getStored() / amount);
                        return;
                    }
                }
                tracker.resetProgress(notEnoughError);
            }
        };
    }

    public static IInputHandler<GasStack, GasStack> getConstantGasInputHandler(IExtendedGasTank tank, RecipeError notEnoughError,
          boolean resetOnNotEnough) {
        return getConstantGasInputHandler(tank, notEnoughError, resetOnNotEnough, () -> true);
    }

    public static IInputHandler<GasStack, GasStack> getConstantGasInputHandler(IExtendedGasTank tank, RecipeError notEnoughError,
          boolean resetOnNotEnough, BooleanSupplier deplete) {
        return new IInputHandler<>() {

            @Override
            public GasStack getInput() {
                GasStack stack = tank.getGas();
                return stack == null ? null : stack.copy();
            }

            @Override
            public GasStack getRecipeInput(GasStack recipeIngredient) {
                GasStack stack = tank.getGas();
                if (stack == null || recipeIngredient == null || !stack.isGasEqual(recipeIngredient)) {
                    return null;
                }
                return recipeIngredient.copy();
            }

            @Override
            public void use(GasStack recipeInput, int operations) {
                if (deplete.getAsBoolean() && recipeInput != null && recipeInput.amount > 0 && operations > 0) {
                    tank.extract(recipeInput.amount * operations, Action.EXECUTE, AutomationType.INTERNAL);
                }
            }

            @Override
            public void calculateOperationsCanSupport(OperationTracker tracker, GasStack recipeInput, int usageMultiplier) {
                if (usageMultiplier <= 0) {
                    return;
                }
                if (recipeInput != null && recipeInput.amount > 0) {
                    int amount = recipeInput.amount * usageMultiplier;
                    if (tank.getStored() >= amount) {
                        tracker.updateOperations(tank.getStored() / amount);
                        return;
                    }
                }
                if (resetOnNotEnough) {
                    tracker.resetProgress(notEnoughError);
                } else {
                    // Constant secondary usage pauses without resetting progress; warning UI reads the tank state directly.
                    tracker.updateOperations(0);
                }
            }
        };
    }

    public static IInputHandler<GasStack, GasStack> getGasInputHandler(IExtendedGasTank tank, IntSupplier perOperationUsage, RecipeError notEnoughError,
          boolean resetOnNotEnough) {
        return getGasInputHandler(tank, perOperationUsage, notEnoughError, resetOnNotEnough, () -> true);
    }

    public static IInputHandler<GasStack, GasStack> getGasInputHandler(IExtendedGasTank tank, IntSupplier perOperationUsage, RecipeError notEnoughError,
          boolean resetOnNotEnough, BooleanSupplier deplete) {
        return new IInputHandler<>() {

            @Override
            public GasStack getInput() {
                GasStack stack = tank.getGas();
                return stack == null ? null : stack.copy();
            }

            @Override
            public GasStack getRecipeInput(GasStack recipeIngredient) {
                GasStack stack = tank.getGas();
                if (stack == null || recipeIngredient == null || !stack.isGasEqual(recipeIngredient)) {
                    return null;
                }
                return recipeIngredient.copy();
            }

            @Override
            public void use(GasStack recipeInput, int operations) {
                if (deplete.getAsBoolean() && recipeInput != null && operations > 0) {
                    tank.extract(perOperationUsage.getAsInt() * operations, Action.EXECUTE, AutomationType.INTERNAL);
                }
            }

            @Override
            public void calculateOperationsCanSupport(OperationTracker tracker, GasStack recipeInput, int usageMultiplier) {
                int amount = perOperationUsage.getAsInt() * usageMultiplier;
                if (amount <= 0) {
                    return;
                }
                if (recipeInput != null && tank.getStored() >= amount) {
                    tracker.updateOperations(tank.getStored() / amount);
                    return;
                }
                if (resetOnNotEnough) {
                    tracker.resetProgress(notEnoughError);
                } else {
                    tracker.updateOperations(0);
                    tracker.addError(notEnoughError);
                }
            }
        };
    }

    public static IInputHandler<FluidStack, FluidStack> getFluidInputHandler(IExtendedFluidTank tank, RecipeError notEnoughError) {
        return getFluidInputHandler(tank, notEnoughError, () -> true);
    }

    public static IInputHandler<FluidStack, FluidStack> getFluidInputHandler(IExtendedFluidTank tank, RecipeError notEnoughError, BooleanSupplier deplete) {
        return new IInputHandler<>() {

            @Override
            public FluidStack getInput() {
                FluidStack stack = tank.getFluid();
                return stack == null ? null : stack.copy();
            }

            @Override
            public FluidStack getRecipeInput(FluidStack recipeIngredient) {
                FluidStack stack = tank.getFluid();
                if (stack == null || recipeIngredient == null || !stack.isFluidEqual(recipeIngredient)) {
                    return null;
                }
                return recipeIngredient.copy();
            }

            @Override
            public void use(FluidStack recipeInput, int operations) {
                if (deplete.getAsBoolean() && recipeInput != null && operations > 0) {
                    tank.extract(recipeInput.amount * operations, Action.EXECUTE, AutomationType.INTERNAL);
                }
            }

            @Override
            public void calculateOperationsCanSupport(OperationTracker tracker, FluidStack recipeInput, int usageMultiplier) {
                if (recipeInput != null && recipeInput.amount > 0 && tank.getFluidAmount() >= recipeInput.amount * usageMultiplier) {
                    tracker.updateOperations(tank.getFluidAmount() / (recipeInput.amount * usageMultiplier));
                    return;
                }
                tracker.resetProgress(notEnoughError);
            }
        };
    }

    public static IInputHandler<InfuseStorage, InfuseStorage> getInfuseInputHandler(InfuseStorage storage, RecipeError notEnoughError) {
        return getInfuseInputHandler(storage, notEnoughError, () -> true);
    }

    public static IInputHandler<InfuseStorage, InfuseStorage> getInfuseInputHandler(InfuseStorage storage, RecipeError notEnoughError,
          BooleanSupplier deplete) {
        return new IInputHandler<>() {

            @Override
            public InfuseStorage getInput() {
                return new InfuseStorage(storage.getType(), storage.getAmount());
            }

            @Override
            public InfuseStorage getRecipeInput(InfuseStorage recipeIngredient) {
                if (recipeIngredient == null || recipeIngredient.getType() == null || storage.getType() != recipeIngredient.getType()) {
                    return null;
                }
                return new InfuseStorage(recipeIngredient.getType(), recipeIngredient.getAmount());
            }

            @Override
            public void use(InfuseStorage recipeInput, int operations) {
                if (deplete.getAsBoolean() && recipeInput != null && recipeInput.getType() != null && recipeInput.getAmount() > 0 && operations > 0) {
                    storage.subtract(new InfuseStorage(recipeInput.getType(), recipeInput.getAmount() * operations));
                }
            }

            @Override
            public void calculateOperationsCanSupport(OperationTracker tracker, InfuseStorage recipeInput, int usageMultiplier) {
                if (recipeInput != null && recipeInput.getType() != null && recipeInput.getAmount() > 0) {
                    int amount = recipeInput.getAmount() * usageMultiplier;
                    if (storage.getType() == recipeInput.getType() && storage.getAmount() >= amount) {
                        tracker.updateOperations(storage.getAmount() / amount);
                        return;
                    }
                }
                tracker.resetProgress(notEnoughError);
            }
        };
    }
}
