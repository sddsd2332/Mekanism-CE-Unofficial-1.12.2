package mekanism.common.util;

import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IMekanismGasHandler;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nullable;

/**
 * Helpers for constructing pre-filled item variants through the current container capabilities.
 */
public final class ItemContainerFillUtils {

    private ItemContainerFillUtils() {
    }

    public static ItemStack getFilledVariant(ItemStack toFill, @Nullable GasStack gasStack, @Nullable FluidStack fluidStack) {
        fillEnergyContainers(toFill);
        fillGasTanks(toFill, gasStack);
        fillFluidTanks(toFill, fluidStack);
        return toFill;
    }

    public static ItemStack getFilledEnergyVariant(ItemStack toFill) {
        fillEnergyContainers(toFill);
        return toFill;
    }

    public static ItemStack getFilledGasVariant(ItemStack toFill, @Nullable GasStack gasStack) {
        fillGasTanks(toFill, gasStack);
        return toFill;
    }

    public static ItemStack getFilledFluidVariant(ItemStack toFill, @Nullable FluidStack fluidStack) {
        fillFluidTanks(toFill, fluidStack);
        return toFill;
    }

    public static boolean fillEnergyContainers(ItemStack stack) {
        IStrictEnergyHandler energyHandler = StorageUtils.getEnergyHandler(stack);
        if (energyHandler == null) {
            return false;
        }
        boolean changed = false;
        for (int container = 0, containers = energyHandler.getEnergyContainerCount(); container < containers; container++) {
            double capacity = energyHandler.getMaxEnergy(container);
            if (capacity > 0 && energyHandler.getEnergy(container) != capacity) {
                energyHandler.setEnergy(container, capacity);
                changed = true;
            }
        }
        return changed;
    }

    public static boolean fillGasTanks(ItemStack stack, @Nullable GasStack gasStack) {
        IMekanismGasHandler gasHandler = GasInventorySlot.getMekanismCapability(stack);
        if (gasHandler == null || gasStack == null || gasStack.amount <= 0 || gasStack.getGas() == null) {
            return false;
        }
        boolean changed = false;
        for (int tank = 0, tanks = gasHandler.getCountGasTanks(null); tank < tanks; tank++) {
            changed |= fillGasTank(gasHandler, tank, gasStack);
        }
        return changed;
    }

    public static boolean fillGasTank(ItemStack stack, int tank, @Nullable GasStack gasStack) {
        return fillGasTank(GasInventorySlot.getMekanismCapability(stack), tank, gasStack);
    }

    public static boolean setGasTank(ItemStack stack, int tank, @Nullable GasStack gasStack) {
        return setGasTank(GasInventorySlot.getMekanismCapability(stack), tank, gasStack);
    }

    public static boolean fillFluidTanks(ItemStack stack, @Nullable FluidStack fluidStack) {
        IMekanismFluidHandler fluidHandler = getMekanismFluidHandler(stack);
        if (fluidHandler == null || fluidStack == null || fluidStack.amount <= 0 || fluidStack.getFluid() == null) {
            return false;
        }
        boolean changed = false;
        for (int tank = 0, tanks = fluidHandler.getTanks(null); tank < tanks; tank++) {
            changed |= fillFluidTank(fluidHandler, tank, fluidStack);
        }
        return changed;
    }

    public static boolean fillFluidTank(ItemStack stack, int tank, @Nullable FluidStack fluidStack) {
        return fillFluidTank(getMekanismFluidHandler(stack), tank, fluidStack);
    }

    public static boolean setFluidTank(ItemStack stack, int tank, @Nullable FluidStack fluidStack) {
        return setFluidTank(getMekanismFluidHandler(stack), tank, fluidStack);
    }

    @Nullable
    private static IMekanismFluidHandler getMekanismFluidHandler(ItemStack stack) {
        IFluidHandlerItem fluidHandler = FluidContainerUtils.getFluidHandlerCapability(stack);
        return fluidHandler instanceof IMekanismFluidHandler mekanismFluidHandler ? mekanismFluidHandler : null;
    }

    private static boolean fillGasTank(@Nullable IMekanismGasHandler gasHandler, int tank, @Nullable GasStack gasStack) {
        if (gasHandler == null || gasStack == null || gasStack.amount <= 0 || gasStack.getGas() == null) {
            return false;
        }
        int capacity = gasHandler.getGasTankCapacity(tank, null);
        return capacity > 0 && setGasTank(gasHandler, tank, gasStack.copy().withAmount(capacity));
    }

    private static boolean setGasTank(@Nullable IMekanismGasHandler gasHandler, int tank, @Nullable GasStack gasStack) {
        if (gasHandler == null || tank < 0 || tank >= gasHandler.getCountGasTanks(null)) {
            return false;
        }
        GasStack toSet = sanitizeGasStack(gasStack, gasHandler.getGasTankCapacity(tank, null));
        if (toSet != null && !gasHandler.isGasValid(tank, toSet, null)) {
            return false;
        }
        GasStack stored = gasHandler.getGasInTank(tank, null);
        if (stacksEqual(stored, toSet)) {
            return false;
        }
        gasHandler.setGasInTank(tank, toSet, null);
        return true;
    }

    private static boolean fillFluidTank(@Nullable IMekanismFluidHandler fluidHandler, int tank, @Nullable FluidStack fluidStack) {
        if (fluidHandler == null || fluidStack == null || fluidStack.amount <= 0 || fluidStack.getFluid() == null) {
            return false;
        }
        int capacity = fluidHandler.getTankCapacity(tank, null);
        return capacity > 0 && setFluidTank(fluidHandler, tank, new FluidStack(fluidStack, capacity));
    }

    private static boolean setFluidTank(@Nullable IMekanismFluidHandler fluidHandler, int tank, @Nullable FluidStack fluidStack) {
        if (fluidHandler == null || tank < 0 || tank >= fluidHandler.getTanks(null)) {
            return false;
        }
        FluidStack toSet = sanitizeFluidStack(fluidStack, fluidHandler.getTankCapacity(tank, null));
        if (toSet != null && !fluidHandler.isFluidValid(tank, toSet, null)) {
            return false;
        }
        FluidStack stored = fluidHandler.getFluidInTank(tank, null);
        if (stacksEqual(stored, toSet)) {
            return false;
        }
        fluidHandler.setFluidInTank(tank, toSet, null);
        return true;
    }

    @Nullable
    private static GasStack sanitizeGasStack(@Nullable GasStack gasStack, int capacity) {
        if (gasStack == null || gasStack.amount <= 0 || gasStack.getGas() == null || capacity <= 0) {
            return null;
        }
        return gasStack.copy().withAmount(Math.min(gasStack.amount, capacity));
    }

    @Nullable
    private static FluidStack sanitizeFluidStack(@Nullable FluidStack fluidStack, int capacity) {
        if (fluidStack == null || fluidStack.amount <= 0 || fluidStack.getFluid() == null || capacity <= 0) {
            return null;
        }
        return new FluidStack(fluidStack, Math.min(fluidStack.amount, capacity));
    }

    private static boolean stacksEqual(@Nullable GasStack first, @Nullable GasStack second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.amount == second.amount && first.getGas() == second.getGas();
    }

    private static boolean stacksEqual(@Nullable FluidStack first, @Nullable FluidStack second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.amount == second.amount && first.isFluidEqual(second);
    }
}
