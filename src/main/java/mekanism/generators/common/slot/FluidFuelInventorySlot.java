package mekanism.generators.common.slot;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.inventory.slot.FluidInventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

public class FluidFuelInventorySlot extends FluidInventorySlot {

    public static boolean fillOrBurnInsertCheck(IExtendedFluidTank fluidTank, ItemStack stack, ToIntFunction<ItemStack> fuelValue) {
        Objects.requireNonNull(fluidTank, "Fluid tank cannot be null");
        Objects.requireNonNull(fuelValue, "Fuel value calculator cannot be null");
        return FluidInventorySlot.fillInsertCheck(fluidTank, stack) || fuelValue.applyAsInt(stack) > 0;
    }

    public static boolean fillOrBurnExtractCheck(IExtendedFluidTank fluidTank, ItemStack stack, ToIntFunction<ItemStack> fuelValue) {
        Objects.requireNonNull(fluidTank, "Fluid tank cannot be null");
        Objects.requireNonNull(fuelValue, "Fuel value calculator cannot be null");
        return !fillOrBurnInsertCheck(fluidTank, stack, fuelValue) && fuelValue.applyAsInt(stack) == 0;
    }

    public static FluidFuelInventorySlot forFuel(IExtendedFluidTank fluidTank, ToIntFunction<ItemStack> fuelValue, IntFunction<FluidStack> fuelCreator,
          @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(fluidTank, "Fluid tank cannot be null");
        Objects.requireNonNull(fuelValue, "Fuel value calculator cannot be null");
        Objects.requireNonNull(fuelCreator, "Fuel fluid stack creator cannot be null");
        return new FluidFuelInventorySlot(fluidTank, fuelValue, fuelCreator,
              stack -> fillOrBurnExtractCheck(fluidTank, stack, fuelValue),
              stack -> fillOrBurnInsertCheck(fluidTank, stack, fuelValue),
              listener, x, y);
    }

    private final ToIntFunction<ItemStack> fuelValue;
    private final IntFunction<FluidStack> fuelCreator;

    private FluidFuelInventorySlot(IExtendedFluidTank fluidTank, ToIntFunction<ItemStack> fuelValue, IntFunction<FluidStack> fuelCreator,
          java.util.function.Predicate<ItemStack> canExtract, java.util.function.Predicate<ItemStack> canInsert,
          @Nullable IContentsListener listener, int x, int y) {
        super(fluidTank, canExtract, canInsert, alwaysTrue, listener, x, y);
        this.fuelValue = fuelValue;
        this.fuelCreator = fuelCreator;
    }

    public void fillOrBurn() {
        if (isEmpty()) {
            return;
        }
        int needed = fluidTank.getNeeded();
        if (needed <= 0 || fillTank()) {
            return;
        }
        int fuel = fuelValue.applyAsInt(current);
        if (fuel > 0 && fuel <= needed) {
            ItemStack container = current.getItem().getContainerItem(current);
            if (!container.isEmpty() && current.getCount() > 1) {
                return;
            }
            fluidTank.insert(fuelCreator.apply(fuel), Action.EXECUTE, AutomationType.INTERNAL);
            if (!container.isEmpty()) {
                setStack(container);
            } else {
                shrinkStack(1, Action.EXECUTE);
            }
        }
    }
}
