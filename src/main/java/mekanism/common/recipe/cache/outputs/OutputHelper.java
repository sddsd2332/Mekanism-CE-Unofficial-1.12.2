package mekanism.common.recipe.cache.outputs;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker;
import mekanism.common.recipe.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.common.recipe.outputs.*;
import mekanism.common.util.FluidContainerUtils;
import mekanism.common.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public final class OutputHelper {

    private OutputHelper() {
    }

    public static IOutputHandler<ItemStack> getOutputHandler(IInventorySlot slot, RecipeError notEnoughSpaceError) {
        return new IOutputHandler<>() {

            @Override
            public void handleOutput(ItemStack toOutput, int operations) {
                growOutput(slot, toOutput, operations);
            }

            @Override
            public void calculateOperationsCanSupport(OperationTracker tracker, ItemStack toOutput) {
                OutputHelper.calculateOperationsCanSupport(tracker, notEnoughSpaceError, slot, toOutput);
            }
        };
    }

    public static IOutputHandler<FluidStack> getOutputHandler(IExtendedFluidTank tank, RecipeError notEnoughSpaceError) {
        return new IOutputHandler<>() {

            @Override
            public void handleOutput(FluidStack toOutput, int operations) {
                OutputHelper.handleOutput(tank, toOutput, operations);
            }

            @Override
            public void calculateOperationsCanSupport(OperationTracker tracker, FluidStack toOutput) {
                OutputHelper.calculateOperationsCanSupport(tracker, notEnoughSpaceError, tank, toOutput);
            }
        };
    }

    public static IOutputHandler<ChanceOutput> getOutputHandler(IInventorySlot primarySlot, IInventorySlot secondarySlot,
          RecipeError notEnoughSpaceError) {
        return getOutputHandler(primarySlot, notEnoughSpaceError, secondarySlot, notEnoughSpaceError);
    }

    public static IOutputHandler<ChanceOutput> getOutputHandler(IInventorySlot primarySlot, RecipeError primaryNotEnoughSpaceError,
          IInventorySlot secondarySlot, RecipeError secondaryNotEnoughSpaceError) {
        return new IOutputHandler<>() {

            @Override
            public void handleOutput(ChanceOutput toOutput, int operations) {
                growOutput(primarySlot, toOutput.getMainOutput(), operations);
                ItemStack secondaryOutput = toOutput.getSecondaryOutput();
                for (int i = 0; i < operations; i++) {
                    growOutput(secondarySlot, secondaryOutput, 1);
                    if (i < operations - 1) {
                        secondaryOutput = toOutput.nextSecondaryOutput();
                    }
                }
            }

            @Override
            public void calculateOperationsCanSupport(OperationTracker tracker, ChanceOutput toOutput) {
                OutputHelper.calculateOperationsCanSupport(tracker, primaryNotEnoughSpaceError, primarySlot, toOutput.getMainOutput());
                if (tracker.shouldContinueChecking()) {
                    OutputHelper.calculateOperationsCanSupport(tracker, secondaryNotEnoughSpaceError, secondarySlot, toOutput.getMaxSecondaryOutput());
                }
            }
        };
    }

    public static IOutputHandler<ChanceOutput2> getOutputHandlerChance2(IInventorySlot slot, RecipeError notEnoughSpaceError) {
        return new IOutputHandler<>() {

            @Override
            public void handleOutput(ChanceOutput2 toOutput, int operations) {
                for (int i = 0; i < operations; i++) {
                    growOutput(slot, toOutput.getPrimaryOutput(), 1);
                }
            }

            @Override
            public void calculateOperationsCanSupport(OperationTracker tracker, ChanceOutput2 toOutput) {
                OutputHelper.calculateOperationsCanSupport(tracker, notEnoughSpaceError, slot, toOutput.getMaxPrimaryOutput());
            }
        };
    }

    public static IOutputHandler<GasStack> getGasOutputHandler(IExtendedGasTank tank, RecipeError notEnoughSpaceError) {
        return new IOutputHandler<>() {

            @Override
            public void handleOutput(GasStack toOutput, int operations) {
                OutputHelper.handleOutput(tank, toOutput, operations);
            }

            @Override
            public void calculateOperationsCanSupport(OperationTracker tracker, GasStack toOutput) {
                OutputHelper.calculateOperationsCanSupport(tracker, notEnoughSpaceError, tank, toOutput);
            }
        };
    }

    public static IOutputHandler<ChanceGasOutput> getChanceGasOutputHandler(IExtendedGasTank tank, RecipeError notEnoughSpaceError) {
        return new IOutputHandler<>() {

            @Override
            public void handleOutput(ChanceGasOutput toOutput, int operations) {
                if (toOutput != null) {
                    toOutput.applyOutputs(tank, true, operations);
                }
            }

            @Override
            public void calculateOperationsCanSupport(OperationTracker tracker, ChanceGasOutput toOutput) {
                if (toOutput == null) {
                    return;
                }
                OutputHelper.calculateOperationsCanSupport(tracker, notEnoughSpaceError, tank, toOutput.getMaxOutput());
            }
        };
    }

    public static IOutputHandler<PressurizedOutput> getOutputHandler(IInventorySlot itemSlot, IExtendedGasTank tank,
          RecipeError notEnoughSpaceError) {
        return getOutputHandler(itemSlot, notEnoughSpaceError, tank, notEnoughSpaceError);
    }

    public static IOutputHandler<PressurizedOutput> getOutputHandler(IInventorySlot itemSlot, RecipeError itemNotEnoughSpaceError,
          IExtendedGasTank tank, RecipeError gasNotEnoughSpaceError) {
        return new IOutputHandler<>() {

            @Override
            public void handleOutput(PressurizedOutput toOutput, int operations) {
                if (toOutput != null && operations > 0) {
                    ItemStack itemOutput = toOutput.getItemOutput();
                    if (itemOutput != null && !itemOutput.isEmpty()) {
                        growOutput(itemSlot, itemOutput, operations);
                    }
                    OutputHelper.handleOutput(tank, toOutput.getGasOutput(), operations);
                }
            }

            @Override
            public void calculateOperationsCanSupport(OperationTracker tracker, PressurizedOutput toOutput) {
                if (toOutput == null) {
                    return;
                }
                ItemStack itemOutput = toOutput.getItemOutput();
                if (itemOutput != null && !itemOutput.isEmpty()) {
                    OutputHelper.calculateOperationsCanSupport(tracker, itemNotEnoughSpaceError, itemSlot, itemOutput);
                }
                if (tracker.shouldContinueChecking()) {
                    OutputHelper.calculateOperationsCanSupport(tracker, gasNotEnoughSpaceError, tank, toOutput.getGasOutput());
                }
            }
        };
    }

    private static void growOutput(IInventorySlot slot, ItemStack output, int operations) {
        if (output.isEmpty() || operations <= 0) {
            return;
        }
        slot.insertItem(StackUtils.size(output, output.getCount() * operations), Action.EXECUTE, AutomationType.INTERNAL);
    }

    private static void handleOutput(IExtendedGasTank tank, GasStack toOutput, int operations) {
        if (toOutput != null && toOutput.amount > 0 && operations > 0) {
            tank.insert(toOutput.copy().withAmount(toOutput.amount * operations), Action.EXECUTE, AutomationType.INTERNAL);
        }
    }

    private static void handleOutput(IExtendedFluidTank tank, FluidStack toOutput, int operations) {
        if (toOutput != null && toOutput.amount > 0 && operations > 0) {
            tank.insert(FluidContainerUtils.copyWithAmount(toOutput, toOutput.amount * operations), Action.EXECUTE, AutomationType.INTERNAL);
        }
    }

    public static IOutputHandler<ChemicalPairOutput> getChemicalPairOutputHandler(IExtendedGasTank leftTank, IExtendedGasTank rightTank,
          RecipeError notEnoughSpaceError) {
        return getChemicalPairOutputHandler(leftTank, notEnoughSpaceError, rightTank, notEnoughSpaceError);
    }

    public static IOutputHandler<ChemicalPairOutput> getChemicalPairOutputHandler(IExtendedGasTank leftTank, RecipeError leftNotEnoughSpaceError,
          IExtendedGasTank rightTank, RecipeError rightNotEnoughSpaceError) {
        return new IOutputHandler<>() {

            @Override
            public void handleOutput(ChemicalPairOutput toOutput, int operations) {
                if (toOutput != null) {
                    toOutput.applyOutputs(leftTank, rightTank, true, operations);
                }
            }

            @Override
            public void calculateOperationsCanSupport(OperationTracker tracker, ChemicalPairOutput toOutput) {
                if (toOutput == null || !toOutput.isValid()) {
                    return;
                }
                int operations = getMaxChemicalPairOutputOperations(leftTank, rightTank, toOutput);
                if (operations > 0) {
                    tracker.updateOperations(operations);
                } else {
                    tracker.updateOperations(0);
                    addChemicalPairOutputErrors(tracker, leftTank, rightTank, toOutput, leftNotEnoughSpaceError, rightNotEnoughSpaceError);
                }
            }
        };
    }

    private static void calculateOperationsCanSupport(OperationTracker tracker, RecipeError notEnoughSpaceError, IInventorySlot slot, ItemStack toOutput) {
        if (toOutput.isEmpty()) {
            return;
        }
        int amountUsed = getItemOutputAmountUsed(slot, toOutput);
        int operations = amountUsed / toOutput.getCount();
        tracker.updateOperations(operations);
        if (operations == 0) {
            tracker.addError(getItemOutputError(slot, amountUsed, notEnoughSpaceError));
        }
    }

    private static int getItemOutputAmountUsed(IInventorySlot slot, ItemStack toOutput) {
        ItemStack output = toOutput.copy();
        output.setCount(Math.max(toOutput.getCount(), slot.getLimit(toOutput)));
        ItemStack remainder = slot.insertItem(output, Action.SIMULATE, AutomationType.INTERNAL);
        return output.getCount() - remainder.getCount();
    }

    private static RecipeError getItemOutputError(IInventorySlot slot, int amountUsed, RecipeError notEnoughSpaceError) {
        if (amountUsed == 0 && slot.getLimit(slot.getStack()) - slot.getCount() > 0) {
            return RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT;
        }
        return notEnoughSpaceError;
    }

    private static void calculateOperationsCanSupport(OperationTracker tracker, RecipeError notEnoughSpaceError, IExtendedGasTank tank, GasStack toOutput) {
        if (toOutput == null || toOutput.amount <= 0) {
            return;
        }
        int amountUsed = getGasOutputAmountUsed(tank, toOutput);
        int operations = amountUsed / toOutput.amount;
        tracker.updateOperations(operations);
        if (operations == 0) {
            tracker.addError(getGasOutputError(tank, amountUsed, notEnoughSpaceError));
        }
    }

    private static int getGasOutputAmountUsed(IExtendedGasTank tank, GasStack toOutput) {
        GasStack maxOutput = toOutput.copy().withAmount(Integer.MAX_VALUE);
        GasStack remainder = tank.insert(maxOutput, Action.SIMULATE, AutomationType.INTERNAL);
        return maxOutput.amount - getAmount(remainder);
    }

    private static void calculateOperationsCanSupport(OperationTracker tracker, RecipeError notEnoughSpaceError, IExtendedFluidTank tank, FluidStack toOutput) {
        if (toOutput == null || toOutput.amount <= 0) {
            return;
        }
        int amountUsed = getFluidOutputAmountUsed(tank, toOutput);
        int operations = amountUsed / toOutput.amount;
        tracker.updateOperations(operations);
        if (operations == 0) {
            tracker.addError(getFluidOutputError(tank, amountUsed, notEnoughSpaceError));
        }
    }

    private static int getFluidOutputAmountUsed(IExtendedFluidTank tank, FluidStack toOutput) {
        FluidStack maxOutput = FluidContainerUtils.copyWithAmount(toOutput, Integer.MAX_VALUE);
        FluidStack remainder = tank.insert(maxOutput, Action.SIMULATE, AutomationType.INTERNAL);
        return maxOutput.amount - getAmount(remainder);
    }

    private static RecipeError getFluidOutputError(IExtendedFluidTank tank, int amountUsed, RecipeError notEnoughSpaceError) {
        if (amountUsed == 0 && tank.getNeeded() > 0) {
            return RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT;
        }
        return notEnoughSpaceError;
    }

    private static int getMaxGasOutputOperations(IExtendedGasTank tank, GasStack toOutput) {
        if (toOutput == null || toOutput.amount <= 0) {
            return Integer.MAX_VALUE;
        }
        return getGasOutputAmountUsed(tank, toOutput) / toOutput.amount;
    }

    private static RecipeError getGasOutputError(IExtendedGasTank tank, int amountUsed, RecipeError notEnoughSpaceError) {
        if (amountUsed == 0 && tank.getNeeded() > 0) {
            return RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT;
        }
        return notEnoughSpaceError;
    }

    private static int getAmount(GasStack stack) {
        return stack == null ? 0 : stack.amount;
    }

    private static int getAmount(FluidStack stack) {
        return stack == null ? 0 : stack.amount;
    }

    private static int getMaxChemicalPairOutputOperations(IExtendedGasTank leftTank, IExtendedGasTank rightTank, ChemicalPairOutput toOutput) {
        return Math.max(getMaxChemicalPairOutputOperations(leftTank, toOutput.leftGas, rightTank, toOutput.rightGas),
              getMaxChemicalPairOutputOperations(leftTank, toOutput.rightGas, rightTank, toOutput.leftGas));
    }

    private static int getMaxChemicalPairOutputOperations(IExtendedGasTank leftTank, GasStack leftOutput, IExtendedGasTank rightTank, GasStack rightOutput) {
        return Math.min(getMaxGasOutputOperations(leftTank, leftOutput), getMaxGasOutputOperations(rightTank, rightOutput));
    }

    private static void addChemicalPairOutputErrors(OperationTracker tracker, IExtendedGasTank leftTank, IExtendedGasTank rightTank, ChemicalPairOutput toOutput,
          RecipeError leftNotEnoughSpaceError, RecipeError rightNotEnoughSpaceError) {
        boolean addedSpaceError = addChemicalPairOrientationErrors(tracker, leftTank, toOutput.leftGas, leftNotEnoughSpaceError, rightTank, toOutput.rightGas,
              rightNotEnoughSpaceError);
        addedSpaceError |= addChemicalPairOrientationErrors(tracker, leftTank, toOutput.rightGas, leftNotEnoughSpaceError, rightTank, toOutput.leftGas,
              rightNotEnoughSpaceError);
        if (!addedSpaceError) {
            tracker.addError(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT);
        }
    }

    private static boolean addChemicalPairOrientationErrors(OperationTracker tracker, IExtendedGasTank leftTank, GasStack leftOutput,
          RecipeError leftNotEnoughSpaceError, IExtendedGasTank rightTank, GasStack rightOutput, RecipeError rightNotEnoughSpaceError) {
        return addChemicalPairTankOutputError(tracker, leftTank, leftOutput, leftNotEnoughSpaceError) |
              addChemicalPairTankOutputError(tracker, rightTank, rightOutput, rightNotEnoughSpaceError);
    }

    private static boolean addChemicalPairTankOutputError(OperationTracker tracker, IExtendedGasTank tank, GasStack output, RecipeError notEnoughSpaceError) {
        if (output == null || output.amount <= 0) {
            return false;
        }
        int amountUsed = getGasOutputAmountUsed(tank, output);
        if (amountUsed / output.amount > 0) {
            return false;
        }
        RecipeError error = getGasOutputError(tank, amountUsed, notEnoughSpaceError);
        if (error == RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT) {
            return false;
        }
        tracker.addError(error);
        return true;
    }
}
