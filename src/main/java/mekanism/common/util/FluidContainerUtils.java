package mekanism.common.util;

import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.item.ItemBlockMachine;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class FluidContainerUtils {

    public static boolean isFluidContainer(ItemStack stack) {
        if (stack.getItem() instanceof ItemBlockMachine) {
            MachineType type = MachineType.get(stack);
            if (type != MachineType.FLUID_TANK) {
                return false;
            }
        }
        return !stack.isEmpty() && stack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
    }

    @Nullable
    public static IFluidHandlerItem getFluidHandlerCapability(ItemStack stack) {
        return stack.isEmpty() ? null : stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
    }

    @Nullable
    public static IFluidHandlerItem getUnstackedFluidHandlerCapability(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ItemStack toCheck = stack.getCount() > 1 ? StackUtils.size(stack, 1) : stack.copy();
        return toCheck.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
    }

    @Nullable
    public static FluidStack copyWithAmount(@Nullable FluidStack stack, int amount) {
        return stack == null ? null : new FluidStack(stack, amount);
    }

    public static int getTankCount(@Nullable IFluidHandler handler) {
        return getTankProperties(handler).length;
    }

    @Nullable
    public static FluidStack getFluidInTank(@Nullable IFluidHandler handler, int tank) {
        IFluidTankProperties[] tanks = getTankProperties(handler);
        IFluidTankProperties tankProperties = tank >= 0 && tank < tanks.length ? tanks[tank] : null;
        return tankProperties == null ? null : tankProperties.getContents();
    }

    public static int getTankCapacity(@Nullable IFluidHandler handler, int tank) {
        IFluidTankProperties[] tanks = getTankProperties(handler);
        IFluidTankProperties tankProperties = tank >= 0 && tank < tanks.length ? tanks[tank] : null;
        return tankProperties == null ? 0 : tankProperties.getCapacity();
    }

    @Nullable
    public static FluidStack getFluidContained(ItemStack stack) {
        IFluidHandlerItem fluidHandler = getUnstackedFluidHandlerCapability(stack);
        if (fluidHandler == null) {
            return null;
        }
        FluidStack contained = null;
        for (int tank = 0, tanks = getTankCount(fluidHandler); tank < tanks; tank++) {
            FluidStack fluidInTank = getFluidInTank(fluidHandler, tank);
            if (fluidInTank != null && fluidInTank.amount > 0) {
                if (contained == null) {
                    contained = fluidInTank.copy();
                } else if (contained.isFluidEqual(fluidInTank)) {
                    contained = copyWithAmount(contained, contained.amount + fluidInTank.amount);
                } else {
                    return contained;
                }
            }
        }
        return contained;
    }

    public static FluidTransferResult getTransferableFluid(ItemStack stack) {
        if (!isFluidContainer(stack)) {
            return FluidTransferResult.empty();
        }
        IFluidHandlerItem fluidHandler = getUnstackedFluidHandlerCapability(stack);
        if (fluidHandler == null) {
            return FluidTransferResult.empty();
        }
        FluidStack fluidFound = null;
        for (int tank = 0, tanks = getTankCount(fluidHandler); tank < tanks; tank++) {
            FluidStack stored = getFluidInTank(fluidHandler, tank);
            if (stored != null && stored.amount > 0) {
                FluidStack extracted = fluidHandler.drain(stored.copy(), true);
                if (extracted != null && extracted.amount > 0) {
                    if (fluidFound == null) {
                        fluidFound = extracted.copy();
                    } else if (fluidFound.getFluid() != extracted.getFluid()) {
                        return FluidTransferResult.invalid();
                    } else {
                        fluidFound = copyWithAmount(fluidFound, fluidFound.amount + extracted.amount);
                    }
                }
            }
        }
        return FluidTransferResult.of(fluidFound);
    }

    public static boolean isEmptyFluidContainer(ItemStack stack) {
        IFluidHandlerItem fluidHandler = getUnstackedFluidHandlerCapability(stack);
        if (fluidHandler == null) {
            return false;
        }
        for (int tank = 0, tanks = getTankCount(fluidHandler); tank < tanks; tank++) {
            FluidStack fluidInTank = getFluidInTank(fluidHandler, tank);
            if (fluidInTank != null && fluidInTank.amount > 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean canDrain(@Nullable FluidStack tankFluid, @Nullable FluidStack drainFluid) {
        return tankFluid != null && (drainFluid == null || tankFluid.isFluidEqual(drainFluid));
    }

    public static boolean canFill(@Nullable FluidStack tankFluid, @Nonnull FluidStack fillFluid) {
        return tankFluid == null || tankFluid.isFluidEqual(fillFluid);
    }

    private static IFluidTankProperties[] getTankProperties(@Nullable IFluidHandler handler) {
        IFluidTankProperties[] properties = handler == null ? null : handler.getTankProperties();
        return properties == null ? new IFluidTankProperties[0] : properties;
    }

    public static final class FluidTransferResult {

        private static final FluidTransferResult EMPTY = new FluidTransferResult(true, null);
        private static final FluidTransferResult INVALID = new FluidTransferResult(false, null);

        private final boolean valid;
        @Nullable
        private final FluidStack stack;

        private FluidTransferResult(boolean valid, @Nullable FluidStack stack) {
            this.valid = valid;
            this.stack = stack;
        }

        private static FluidTransferResult of(@Nullable FluidStack stack) {
            return stack == null ? empty() : new FluidTransferResult(true, stack);
        }

        private static FluidTransferResult empty() {
            return EMPTY;
        }

        private static FluidTransferResult invalid() {
            return INVALID;
        }

        public boolean isValid() {
            return valid;
        }

        @Nullable
        public FluidStack getStack() {
            return stack;
        }
    }
}
