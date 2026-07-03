package mekanism.common.inventory.slot;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.NBTConstants;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.util.FluidContainerUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public class FluidInventorySlot extends BasicInventorySlot implements IFluidHandlerSlot {

    public static FluidInventorySlot input(IExtendedFluidTank fluidTank, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(fluidTank, "Fluid tank cannot be null");
        return new FluidInventorySlot(fluidTank, alwaysFalse, getInputPredicate(fluidTank), FluidInventorySlot::isFluidContainerItem, listener, x, y);
    }

    protected static Predicate<ItemStack> getInputPredicate(IExtendedFluidTank fluidTank) {
        return stack -> {
            IFluidHandlerItem fluidHandlerItem = tryGetFluidHandlerUnstacked(stack);
            if (fluidHandlerItem == null) {
                return false;
            }
            boolean hasEmpty = false;
            for (int tank = 0, tanks = IFluidHandlerSlot.getTankCount(fluidHandlerItem); tank < tanks; tank++) {
                FluidStack fluidInTank = IFluidHandlerSlot.getFluidInTank(fluidHandlerItem, tank);
                if (IFluidHandlerSlot.isFluidStackEmpty(fluidInTank)) {
                    hasEmpty = true;
                } else if (IFluidHandlerSlot.getFluidAmount(fluidTank.insert(fluidInTank, Action.SIMULATE, AutomationType.INTERNAL)) < fluidInTank.amount) {
                    return true;
                }
            }
            FluidStack stored = fluidTank.getFluid();
            if (IFluidHandlerSlot.isFluidStackEmpty(stored)) {
                return hasEmpty;
            }
            FluidStack toFill = stored.copy();
            if (toFill.amount < Fluid.BUCKET_VOLUME) {
                toFill.amount = Fluid.BUCKET_VOLUME;
            }
            return fluidHandlerItem.fill(toFill, false) > 0;
        };
    }

    public static FluidInventorySlot rotary(IExtendedFluidTank fluidTank, BooleanSupplier modeSupplier, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(fluidTank, "Fluid tank cannot be null");
        Objects.requireNonNull(modeSupplier, "Mode supplier cannot be null");
        return new FluidInventorySlot(fluidTank, alwaysFalse, stack -> {
            IFluidHandlerItem fluidHandlerItem = tryGetFluidHandlerUnstacked(stack);
            if (fluidHandlerItem == null) {
                return false;
            }
            boolean mode = modeSupplier.getAsBoolean();
            boolean allEmpty = true;
            for (int tank = 0, tanks = IFluidHandlerSlot.getTankCount(fluidHandlerItem); tank < tanks; tank++) {
                FluidStack fluidInTank = IFluidHandlerSlot.getFluidInTank(fluidHandlerItem, tank);
                if (!IFluidHandlerSlot.isFluidStackEmpty(fluidInTank)) {
                    if (IFluidHandlerSlot.getFluidAmount(fluidTank.insert(fluidInTank, Action.SIMULATE, AutomationType.INTERNAL)) < fluidInTank.amount) {
                        return mode;
                    }
                    allEmpty = false;
                }
            }
            return allEmpty && !mode;
        }, stack -> {
            IFluidHandlerItem fluidHandlerItem = tryGetFluidHandlerUnstacked(stack);
            if (fluidHandlerItem == null) {
                return false;
            }
            if (modeSupplier.getAsBoolean()) {
                for (int tank = 0, tanks = IFluidHandlerSlot.getTankCount(fluidHandlerItem); tank < tanks; tank++) {
                    FluidStack fluidInTank = IFluidHandlerSlot.getFluidInTank(fluidHandlerItem, tank);
                    if (!IFluidHandlerSlot.isFluidStackEmpty(fluidInTank) && fluidTank.isFluidValid(fluidInTank)) {
                        return true;
                    }
                }
                return false;
            }
            return true;
        }, listener, x, y);
    }

    public static FluidInventorySlot fill(IExtendedFluidTank fluidTank, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(fluidTank, "Fluid tank cannot be null");
        return new FluidInventorySlot(fluidTank, alwaysFalse, getFillPredicate(fluidTank), FluidInventorySlot::isFluidContainerItem, listener, x, y);
    }

    public static Predicate<ItemStack> getFillPredicate(IExtendedFluidTank fluidTank) {
        Objects.requireNonNull(fluidTank, "Fluid tank cannot be null");
        return stack -> {
            IFluidHandlerItem fluidHandlerItem = tryGetFluidHandlerUnstacked(stack);
            if (fluidHandlerItem == null) {
                return false;
            }
            for (int tank = 0, tanks = IFluidHandlerSlot.getTankCount(fluidHandlerItem); tank < tanks; tank++) {
                FluidStack fluidInTank = IFluidHandlerSlot.getFluidInTank(fluidHandlerItem, tank);
                if (!IFluidHandlerSlot.isFluidStackEmpty(fluidInTank) &&
                      IFluidHandlerSlot.getFluidAmount(fluidTank.insert(fluidInTank, Action.SIMULATE, AutomationType.INTERNAL)) < fluidInTank.amount) {
                    return true;
                }
            }
            return false;
        };
    }

    public static boolean fillInsertCheck(IExtendedFluidTank fluidTank, ItemStack stack) {
        Objects.requireNonNull(fluidTank, "Fluid tank cannot be null");
        return getFillPredicate(fluidTank).test(stack);
    }

    public static boolean fillExtractCheck(IExtendedFluidTank fluidTank, ItemStack stack) {
        Objects.requireNonNull(fluidTank, "Fluid tank cannot be null");
        return !fillInsertCheck(fluidTank, stack);
    }

    public static FluidInventorySlot drain(IExtendedFluidTank fluidTank, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(fluidTank, "Fluid tank cannot be null");
        return new FluidInventorySlot(fluidTank, alwaysFalse, stack -> {
            IFluidHandlerItem handler = tryGetFluidHandlerUnstacked(stack);
            if (handler == null) {
                return false;
            }
            FluidStack stored = fluidTank.getFluid();
            if (IFluidHandlerSlot.isFluidStackEmpty(stored)) {
                return true;
            }
            return handler.fill(stored.copy(), false) > 0;
        }, stack -> isNonFullFluidContainer(tryGetFluidHandlerUnstacked(stack)), listener, x, y);
    }

    public static boolean drainInsertCheck(IExtendedFluidTank fluidTank, ItemStack stack) {
        Objects.requireNonNull(fluidTank, "Fluid tank cannot be null");
        IFluidHandlerItem handler = tryGetFluidHandlerUnstacked(stack);
        if (handler == null) {
            return false;
        }
        FluidStack stored = fluidTank.getFluid();
        if (IFluidHandlerSlot.isFluidStackEmpty(stored)) {
            return isNonFullFluidContainer(handler);
        }
        return handler.fill(stored.copy(), false) > 0;
    }

    public static boolean drainExtractCheck(IExtendedFluidTank fluidTank, ItemStack stack) {
        Objects.requireNonNull(fluidTank, "Fluid tank cannot be null");
        return !drainInsertCheck(fluidTank, stack);
    }

    @Nullable
    public static IFluidHandlerItem tryGetFluidHandlerUnstacked(ItemStack stack) {
        return getFluidHandler(stack, true);
    }

    public static boolean isFluidContainerItem(ItemStack stack) {
        return FluidContainerUtils.isFluidContainer(stack);
    }

    @Nullable
    protected static IFluidHandlerItem getFluidHandler(ItemStack stack, boolean unstack) {
        if (!isFluidContainerItem(stack)) {
            return null;
        }
        return unstack ? FluidContainerUtils.getUnstackedFluidHandlerCapability(stack) : FluidContainerUtils.getFluidHandlerCapability(stack);
    }

    public static boolean isNonFullFluidContainer(@Nullable IFluidHandlerItem handler) {
        if (handler == null) {
            return false;
        }
        for (int tank = 0, tanks = IFluidHandlerSlot.getTankCount(handler); tank < tanks; tank++) {
            FluidStack fluid = IFluidHandlerSlot.getFluidInTank(handler, tank);
            int stored = IFluidHandlerSlot.getFluidAmount(fluid);
            if (stored < IFluidHandlerSlot.getTankCapacity(handler, tank)) {
                return true;
            }
        }
        return false;
    }

    protected final IExtendedFluidTank fluidTank;
    private boolean isDraining;
    private boolean isFilling;

    protected FluidInventorySlot(IExtendedFluidTank fluidTank, Predicate<ItemStack> canExtract, Predicate<ItemStack> canInsert,
          @Nullable IContentsListener listener, int x, int y) {
        this(fluidTank, canExtract, canInsert, alwaysTrue, listener, x, y);
    }

    protected FluidInventorySlot(IExtendedFluidTank fluidTank, Predicate<ItemStack> canExtract, Predicate<ItemStack> canInsert, Predicate<ItemStack> validator,
          @Nullable IContentsListener listener, int x, int y) {
        super(canExtract, canInsert, validator, listener, x, y);
        setSlotType(ContainerSlotType.EXTRA);
        this.fluidTank = fluidTank;
    }

    @Override
    public void setStack(@Nonnull ItemStack stack) {
        super.setStack(stack);
        isDraining = false;
        isFilling = false;
    }

    @Override
    public IExtendedFluidTank getFluidTank() {
        return fluidTank;
    }

    @Override
    public boolean isDraining() {
        return isDraining;
    }

    @Override
    public boolean isFilling() {
        return isFilling;
    }

    @Override
    public void setDraining(boolean draining) {
        isDraining = draining;
    }

    @Override
    public void setFilling(boolean filling) {
        isFilling = filling;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = super.serializeNBT();
        if (isDraining) {
            nbt.setBoolean(NBTConstants.DRAINING, true);
        }
        if (isFilling) {
            nbt.setBoolean(NBTConstants.FILLING, true);
        }
        return nbt;
    }

    @Override
    public void deserializeNBT(@Nonnull NBTTagCompound nbt) {
        isDraining = nbt.getBoolean(NBTConstants.DRAINING);
        isFilling = nbt.getBoolean(NBTConstants.FILLING);
        super.deserializeNBT(nbt);
    }
}
