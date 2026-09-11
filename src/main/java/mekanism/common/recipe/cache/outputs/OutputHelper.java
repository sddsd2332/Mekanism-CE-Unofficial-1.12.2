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
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

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
        return getOutputHandler(primarySlot, primaryNotEnoughSpaceError, secondarySlot, secondaryNotEnoughSpaceError, null);
    }

    /**
     * Creates a chance output handler that draws from an explicit random source.
     *
     * @param random the source to roll probability outputs with, or null to keep using the legacy shared source
     */
    public static IOutputHandler<ChanceOutput> getOutputHandler(IInventorySlot primarySlot, IInventorySlot secondarySlot,
          RecipeError notEnoughSpaceError, @Nullable Random random) {
        return getOutputHandler(primarySlot, notEnoughSpaceError, secondarySlot, notEnoughSpaceError, random);
    }

    /**
     * Creates a chance output handler that draws from an explicit random source.
     *
     * @param random the source to roll probability outputs with, or null to keep using the legacy shared source
     */
    public static IOutputHandler<ChanceOutput> getOutputHandler(IInventorySlot primarySlot, RecipeError primaryNotEnoughSpaceError,
          IInventorySlot secondarySlot, RecipeError secondaryNotEnoughSpaceError, @Nullable Random random) {
        return new IOutputHandler<>() {

            @Override
            public void handleOutput(ChanceOutput toOutput, int operations) {
                growOutput(primarySlot, toOutput.getMainOutput(), operations);
                ItemStack secondaryOutput = random == null ? toOutput.getSecondaryOutput() : toOutput.getSecondaryOutput(random);
                for (int i = 0; i < operations; i++) {
                    growOutput(secondarySlot, secondaryOutput, 1);
                    if (i < operations - 1) {
                        secondaryOutput = random == null ? toOutput.nextSecondaryOutput() : toOutput.nextSecondaryOutput(random);
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
        return getOutputHandlerChance2(slot, notEnoughSpaceError, null);
    }

    /**
     * Creates a chance output handler that draws from an explicit random source.
     *
     * @param random the source to roll probability outputs with, or null to keep using the legacy shared source
     */
    public static IOutputHandler<ChanceOutput2> getOutputHandlerChance2(IInventorySlot slot, RecipeError notEnoughSpaceError, @Nullable Random random) {
        return new IOutputHandler<>() {

            @Override
            public void handleOutput(ChanceOutput2 toOutput, int operations) {
                for (int i = 0; i < operations; i++) {
                    growOutput(slot, random == null ? toOutput.getPrimaryOutput() : toOutput.getPrimaryOutput(random), 1);
                }
            }

            @Override
            public void calculateOperationsCanSupport(OperationTracker tracker, ChanceOutput2 toOutput) {
                OutputHelper.calculateOperationsCanSupport(tracker, notEnoughSpaceError, slot, toOutput.getMaxPrimaryOutput());
            }
        };
    }

    public static IOutputHandler<FarmOutput> getFarmOutputHandler(IInventorySlot primarySlot, IInventorySlot secondarySlot,
          RecipeError notEnoughSpaceError) {
        return getFarmOutputHandler(Arrays.asList(primarySlot, secondarySlot), notEnoughSpaceError);
    }

    public static IOutputHandler<FarmOutput> getFarmOutputHandler(List<? extends IInventorySlot> outputSlots,
          RecipeError notEnoughSpaceError) {
        return getFarmOutputHandler(outputSlots, notEnoughSpaceError, null);
    }

    /**
     * Creates a farm output handler that draws from an explicit random source.
     *
     * @param random the source to roll chance outputs with, or null to keep using the legacy shared source
     */
    public static IOutputHandler<FarmOutput> getFarmOutputHandler(List<? extends IInventorySlot> outputSlots,
          RecipeError notEnoughSpaceError, @Nullable Random random) {
        return new IOutputHandler<>() {

            @Override
            public void handleOutput(FarmOutput toOutput, int operations) {
                for (int i = 0; i < operations; i++) {
                    insertFarmOutputs(outputSlots, mergeFarmOutputs(random == null ? toOutput.getOutputs() : toOutput.getOutputs(random)));
                }
            }

            @Override
            public void calculateOperationsCanSupport(OperationTracker tracker, FarmOutput toOutput) {
                int operations = getFarmOutputOperations(outputSlots, toOutput, Math.max(1, tracker.getCurrentMaxOperations()));
                tracker.updateOperations(operations);
                if (operations == 0) {
                    tracker.addError(notEnoughSpaceError);
                }
            }
        };
    }

    public static boolean canFitFarmOutput(List<? extends IInventorySlot> outputSlots, FarmOutput output) {
        return getFarmOutputOperations(outputSlots, output, 1) > 0;
    }

    private static int getFarmOutputOperations(List<? extends IInventorySlot> outputSlots, FarmOutput output, int operationLimit) {
        if (operationLimit <= 0 || outputSlots.isEmpty() || output == null || !output.isValid()) {
            return 0;
        }
        List<ItemStack> worstCaseOutputs = mergeFarmOutputs(output.getMaxOutputs());
        List<ItemStack> virtualSlots = new ArrayList<>(outputSlots.size());
        for (IInventorySlot slot : outputSlots) {
            virtualSlots.add(slot.getStack().copy());
        }
        int maxOperations = Math.min(operationLimit,
              getGuaranteedOutputUpperBound(outputSlots, virtualSlots, output.getGuaranteedOutput()));
        int operations = 0;
        while (operations < maxOperations && insertFarmOutputsVirtual(outputSlots, virtualSlots, worstCaseOutputs)) {
            operations++;
        }
        return operations;
    }

    private static int getGuaranteedOutputUpperBound(List<? extends IInventorySlot> outputSlots, List<ItemStack> virtualSlots,
          ItemStack guaranteedOutput) {
        int capacity = 0;
        for (int i = 0; i < outputSlots.size(); i++) {
            IInventorySlot slot = outputSlots.get(i);
            ItemStack stored = virtualSlots.get(i);
            if (slot.isItemValid(guaranteedOutput) && (stored.isEmpty() || ItemHandlerHelper.canItemStacksStack(stored, guaranteedOutput))) {
                capacity += Math.max(0, slot.getLimit(guaranteedOutput) - stored.getCount());
            }
        }
        return guaranteedOutput.isEmpty() ? 0 : capacity / guaranteedOutput.getCount();
    }

    private static List<ItemStack> mergeFarmOutputs(List<ItemStack> outputs) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack output : outputs) {
            if (output == null || output.isEmpty()) {
                continue;
            }
            ItemStack matching = null;
            for (ItemStack existing : merged) {
                if (ItemHandlerHelper.canItemStacksStack(existing, output)) {
                    matching = existing;
                    break;
                }
            }
            if (matching == null) {
                merged.add(output.copy());
            } else {
                matching.grow(output.getCount());
            }
        }
        return merged;
    }

    private static boolean insertFarmOutputsVirtual(List<? extends IInventorySlot> outputSlots, List<ItemStack> virtualSlots,
          List<ItemStack> outputs) {
        for (ItemStack output : outputs) {
            int remaining = output.getCount();
            remaining = insertFarmOutputVirtual(outputSlots, virtualSlots, output, remaining, false);
            remaining = insertFarmOutputVirtual(outputSlots, virtualSlots, output, remaining, true);
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private static int insertFarmOutputVirtual(List<? extends IInventorySlot> outputSlots, List<ItemStack> virtualSlots,
          ItemStack output, int remaining, boolean emptySlots) {
        for (int i = 0; i < outputSlots.size() && remaining > 0; i++) {
            IInventorySlot slot = outputSlots.get(i);
            ItemStack stored = virtualSlots.get(i);
            if (stored.isEmpty() != emptySlots || !slot.isItemValid(output) ||
                  !stored.isEmpty() && !ItemHandlerHelper.canItemStacksStack(stored, output)) {
                continue;
            }
            int accepted = Math.min(remaining, Math.max(0, slot.getLimit(output) - stored.getCount()));
            if (accepted <= 0) {
                continue;
            }
            if (stored.isEmpty()) {
                ItemStack inserted = output.copy();
                inserted.setCount(accepted);
                virtualSlots.set(i, inserted);
            } else {
                stored.grow(accepted);
            }
            remaining -= accepted;
        }
        return remaining;
    }

    private static void insertFarmOutputs(List<? extends IInventorySlot> outputSlots, List<ItemStack> outputs) {
        for (ItemStack output : outputs) {
            ItemStack remainder = output.copy();
            remainder = insertFarmOutput(outputSlots, remainder, false);
            insertFarmOutput(outputSlots, remainder, true);
        }
    }

    private static ItemStack insertFarmOutput(List<? extends IInventorySlot> outputSlots, ItemStack stack, boolean emptySlots) {
        for (IInventorySlot slot : outputSlots) {
            if (slot.isEmpty() != emptySlots) {
                continue;
            }
            stack = slot.insertItem(stack, Action.EXECUTE, AutomationType.INTERNAL);
            if (stack.isEmpty()) {
                break;
            }
        }
        return stack;
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
        return getChanceGasOutputHandler(tank, notEnoughSpaceError, null);
    }

    /**
     * Creates a chance gas output handler that draws from an explicit random source.
     *
     * @param random the source to roll probability outputs with, or null to keep using the legacy shared source
     */
    public static IOutputHandler<ChanceGasOutput> getChanceGasOutputHandler(IExtendedGasTank tank, RecipeError notEnoughSpaceError,
          @Nullable Random random) {
        return new IOutputHandler<>() {

            @Override
            public void handleOutput(ChanceGasOutput toOutput, int operations) {
                if (toOutput != null) {
                    if (random == null) {
                        toOutput.applyOutputs(tank, true, operations);
                    } else {
                        toOutput.applyOutputs(tank, true, operations, random);
                    }
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
