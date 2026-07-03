package mekanism.common.inventory.slot;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.base.IFluidContainerManager.ContainerEditMode;
import mekanism.common.util.FluidContainerUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StackUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public interface IFluidHandlerSlot extends IInventorySlot {

    IExtendedFluidTank getFluidTank();

    boolean isDraining();

    boolean isFilling();

    void setDraining(boolean draining);

    void setFilling(boolean filling);

    static int getTankCount(IFluidHandler handler) {
        return FluidContainerUtils.getTankCount(handler);
    }

    static FluidStack getFluidInTank(IFluidHandler handler, int tank) {
        return FluidContainerUtils.getFluidInTank(handler, tank);
    }

    static int getTankCapacity(IFluidHandler handler, int tank) {
        return FluidContainerUtils.getTankCapacity(handler, tank);
    }

    default void handleTank(IInventorySlot outputSlot, ContainerEditMode editMode) {
        if (isEmpty()) {
            return;
        }
        if (editMode == ContainerEditMode.FILL) {
            drainTank(outputSlot);
        } else if (editMode == ContainerEditMode.EMPTY) {
            fillTank(outputSlot);
        } else if (editMode == ContainerEditMode.BOTH) {
            ItemStack stack = getStack();
            IFluidHandlerItem fluidHandlerItem = FluidInventorySlot.tryGetFluidHandlerUnstacked(stack);
            if (fluidHandlerItem == null) {
                return;
            }
            boolean hasEmpty = false;
            for (int tank = 0, tanks = getTankCount(fluidHandlerItem); tank < tanks; tank++) {
                FluidStack fluidInTank = getFluidInTank(fluidHandlerItem, tank);
                if (isFluidStackEmpty(fluidInTank)) {
                    hasEmpty = true;
                } else if (!isDraining() && getFluidAmount(getFluidTank().insert(fluidInTank, Action.SIMULATE, AutomationType.INTERNAL)) < fluidInTank.amount) {
                    fillTank(outputSlot);
                    return;
                }
            }
            if (isFilling()) {
                if (moveItem(outputSlot, stack)) {
                    setFilling(false);
                }
            } else {
                FluidStack tankFluid = getFluidTank().getFluid();
                if (!isFluidStackEmpty(tankFluid) && (isDraining() || fluidHandlerItem.fill(tankFluid.copy(), false) > 0)) {
                    drainTank(outputSlot);
                } else if (getFluidTank().isEmpty() && hasEmpty) {
                    drainTank(outputSlot);
                }
            }
        }
    }

    /**
     * Fills the tank from the item in this slot, then moves the resulting container to the output slot.
     */
    default void fillTank(IInventorySlot outputSlot) {
        if (isEmpty()) {
            return;
        }
        IFluidHandlerItem itemFluidHandler = FluidInventorySlot.tryGetFluidHandlerUnstacked(getStack());
        if (itemFluidHandler == null) {
            return;
        }
        int tanks = getTankCount(itemFluidHandler);
        if (tanks == 1) {
            FluidStack fluidInItem = getFluidInTank(itemFluidHandler, 0);
            if (!isFluidStackEmpty(fluidInItem) && getFluidTank().isFluidValid(fluidInItem)) {
                drainItemAndMove(outputSlot, fluidInItem);
            }
        } else if (tanks > 1) {
            for (FluidStack knownFluid : gatherKnownFluids(itemFluidHandler, tanks)) {
                if (drainItemAndMove(outputSlot, knownFluid)) {
                    if (isEmpty()) {
                        break;
                    }
                }
            }
        }
    }

    /**
     * Drains this tank into the item in this slot, then moves the resulting container to the output slot.
     */
    default void drainTank(IInventorySlot outputSlot) {
        if (isEmpty()) {
            return;
        }
        FluidStack fluidInTank = getFluidTank().getFluid();
        if (isFluidStackEmpty(fluidInTank)) {
            return;
        }
        FluidStack simulatedDrain = getFluidTank().extract(fluidInTank.amount, Action.SIMULATE, AutomationType.INTERNAL);
        if (isFluidStackEmpty(simulatedDrain)) {
            return;
        }
        if (FluidInventorySlot.getFluidHandler(getStack(), false) == null) {
            return;
        }
        IFluidHandlerItem fluidHandlerItem = FluidInventorySlot.tryGetFluidHandlerUnstacked(StackUtils.size(getStack(), 1));
        if (fluidHandlerItem == null) {
            return;
        }
        int toDrain = fluidHandlerItem.fill(fluidInTank.copy(), true);
        if (toDrain <= 0) {
            return;
        }
        ItemStack container = fluidHandlerItem.getContainer();
        IFluidHandlerItem containerHandler = FluidInventorySlot.tryGetFluidHandlerUnstacked(container);
        if (getCount() == 1 && containerHandler != null && containerHandler.fill(fluidInTank.copy(), false) > 0) {
            setStack(container);
            setDraining(true);
            MekanismUtils.logMismatchedStackSize(getFluidTank().shrinkStack(toDrain, Action.EXECUTE), toDrain);
            return;
        }
        if (moveItem(outputSlot, container)) {
            MekanismUtils.logMismatchedStackSize(getFluidTank().shrinkStack(toDrain, Action.EXECUTE), toDrain);
            setDraining(false);
        }
    }

    /**
     * Fills this tank from the item in this slot without moving a resulting container to an output slot.
     */
    default boolean fillTank() {
        if (getCount() != 1) {
            return false;
        }
        IFluidHandlerItem itemFluidHandler = FluidInventorySlot.tryGetFluidHandlerUnstacked(getStack());
        if (itemFluidHandler == null) {
            return false;
        }
        int tanks = getTankCount(itemFluidHandler);
        if (tanks == 1) {
            FluidStack fluidInItem = getFluidInTank(itemFluidHandler, 0);
            if (!isFluidStackEmpty(fluidInItem) && getFluidTank().isFluidValid(fluidInItem) &&
                  fillHandlerFromOther(getFluidTank(), itemFluidHandler, fluidInItem)) {
                setStack(itemFluidHandler.getContainer());
                return true;
            }
        } else if (tanks > 1) {
            boolean changed = false;
            for (FluidStack knownFluid : gatherKnownFluids(itemFluidHandler, tanks)) {
                if (fillHandlerFromOther(getFluidTank(), itemFluidHandler, knownFluid)) {
                    changed = true;
                }
            }
            if (changed) {
                setStack(itemFluidHandler.getContainer());
                return true;
            }
        }
        return false;
    }

    /**
     * Drains this tank into the item in this slot without moving a resulting container to an output slot.
     */
    default boolean drainTank() {
        if (getCount() != 1) {
            return false;
        }
        FluidStack fluidInTank = getFluidTank().getFluid();
        if (isFluidStackEmpty(fluidInTank)) {
            return false;
        }
        IFluidHandlerItem handler = FluidInventorySlot.tryGetFluidHandlerUnstacked(getStack());
        if (handler == null) {
            return false;
        }
        int filled = handler.fill(fluidInTank.copy(), true);
        if (filled <= 0) {
            return false;
        }
        MekanismUtils.logMismatchedStackSize(getFluidTank().shrinkStack(filled, Action.EXECUTE), filled);
        setStack(handler.getContainer());
        setDraining(true);
        return true;
    }

    default boolean drainItemAndMove(IInventorySlot outputSlot, FluidStack fluidToTransfer) {
        FluidStack simulatedRemainder = getFluidTank().insert(fluidToTransfer, Action.SIMULATE, AutomationType.INTERNAL);
        int remainder = getFluidAmount(simulatedRemainder);
        int toTransfer = fluidToTransfer.amount;
        if (remainder == toTransfer) {
            return false;
        }
        ItemStack stack = getStack();
        if (FluidInventorySlot.getFluidHandler(stack, false) == null) {
            return false;
        }
        ItemStack input = StackUtils.size(stack, 1);
        IFluidHandlerItem fluidHandlerItem = FluidInventorySlot.tryGetFluidHandlerUnstacked(input);
        if (fluidHandlerItem == null) {
            return false;
        }
        FluidStack drained = fluidHandlerItem.drain(copyFluidStackWithAmount(fluidToTransfer, toTransfer - remainder), true);
        if (isFluidStackEmpty(drained)) {
            return false;
        }
        ItemStack container = fluidHandlerItem.getContainer();
        IFluidHandlerItem containerHandler = FluidInventorySlot.tryGetFluidHandlerUnstacked(container);
        if (getCount() == 1 && containerHandler != null && !isFluidStackEmpty(containerHandler.drain(Integer.MAX_VALUE, false))) {
            setStack(container);
            getFluidTank().insert(drained, Action.EXECUTE, AutomationType.INTERNAL);
            setFilling(true);
            return true;
        }
        if (moveItem(outputSlot, container)) {
            getFluidTank().insert(drained, Action.EXECUTE, AutomationType.INTERNAL);
            setFilling(false);
            return true;
        }
        return false;
    }

    default boolean moveItem(IInventorySlot outputSlot, ItemStack stackToMove) {
        if (!stackToMove.isEmpty()) {
            ItemStack remainder = outputSlot.insertItem(stackToMove, Action.SIMULATE, AutomationType.INTERNAL);
            if (!remainder.isEmpty()) {
                return false;
            }
        }
        MekanismUtils.logMismatchedStackSize(shrinkStack(1, Action.EXECUTE), 1);
        if (!stackToMove.isEmpty()) {
            MekanismUtils.logMismatchedStackSize(outputSlot.insertItem(stackToMove, Action.EXECUTE, AutomationType.INTERNAL).getCount(), 0);
        }
        return true;
    }

    default Set<FluidStack> gatherKnownFluids(IFluidHandlerItem itemFluidHandler, int tanks) {
        Map<FluidStack, Integer> knownFluids = new HashMap<>();
        for (int tank = 0; tank < tanks; tank++) {
            FluidStack fluidInItem = getFluidInTank(itemFluidHandler, tank);
            if (isFluidStackEmpty(fluidInItem)) {
                continue;
            }
            if (!knownFluids.containsKey(fluidInItem)) {
                if (!isFluidStackEmpty(itemFluidHandler.drain(fluidInItem.copy(), false)) && getFluidTank().isFluidValid(fluidInItem)) {
                    knownFluids.put(fluidInItem.copy(), fluidInItem.amount);
                }
            } else {
                knownFluids.computeIfPresent(fluidInItem, (fluid, amount) -> amount + fluidInItem.amount);
            }
        }
        Set<FluidStack> fluids = new HashSet<>();
        knownFluids.forEach((fluid, amount) -> fluids.add(FluidContainerUtils.copyWithAmount(fluid, amount)));
        return fluids;
    }

    default boolean fillHandlerFromOther(IExtendedFluidTank handlerToFill, IFluidHandler handlerToDrain, FluidStack fluid) {
        FluidStack simulatedDrain = handlerToDrain.drain(fluid.copy(), false);
        if (isFluidStackEmpty(simulatedDrain)) {
            return false;
        }
        FluidStack simulatedRemainder = handlerToFill.insert(simulatedDrain, Action.SIMULATE, AutomationType.INTERNAL);
        int remainder = getFluidAmount(simulatedRemainder);
        int drained = simulatedDrain.amount;
        if (remainder >= drained) {
            return false;
        }
        FluidStack actualDrain = handlerToDrain.drain(copyFluidStackWithAmount(fluid, drained - remainder), true);
        if (isFluidStackEmpty(actualDrain)) {
            return false;
        }
        handlerToFill.insert(actualDrain, Action.EXECUTE, AutomationType.INTERNAL);
        return true;
    }

    static boolean isFluidStackEmpty(FluidStack stack) {
        return stack == null || stack.amount <= 0;
    }

    static int getFluidAmount(FluidStack stack) {
        return isFluidStackEmpty(stack) ? 0 : stack.amount;
    }

    static FluidStack copyFluidStackWithAmount(FluidStack stack, int amount) {
        return FluidContainerUtils.copyWithAmount(stack, amount);
    }
}
