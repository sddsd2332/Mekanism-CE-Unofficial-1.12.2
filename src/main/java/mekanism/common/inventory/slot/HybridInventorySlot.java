package mekanism.common.inventory.slot;

import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.NBTConstants;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.base.IFluidContainerManager.ContainerEditMode;
import mekanism.common.capabilities.merged.MergedTank;
import mekanism.common.capabilities.merged.MergedTank.CurrentType;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.inventory.slot.gas.MergedGasInventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class HybridInventorySlot extends MergedGasInventorySlot<MergedTank> implements IFluidHandlerSlot, IGasHandlerSlot {

    protected static boolean hasCapability(ItemStack stack) {
        return FluidInventorySlot.isFluidContainerItem(stack) || GasInventorySlot.isGasContainerItem(stack);
    }

    public static HybridInventorySlot inputOrDrain(MergedTank mergedTank, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(mergedTank, "Merged tank cannot be null");
        Predicate<ItemStack> fluidInsertPredicate = FluidInventorySlot.getInputPredicate(mergedTank.getFluidTank());
        Predicate<ItemStack> gasInsertPredicate = stack -> GasInventorySlot.drainInsertCheck(mergedTank.getGasTank(), stack);
        BiPredicate<ItemStack, AutomationType> insertPredicate = (stack, automationType) -> {
            CurrentType currentType = mergedTank.getCurrentType();
            if (currentType == CurrentType.FLUID) {
                return fluidInsertPredicate.test(stack);
            }
            if (currentType.isGas()) {
                return gasInsertPredicate.test(stack);
            }
            return fluidInsertPredicate.test(stack) || gasInsertPredicate.test(stack);
        };
        return new HybridInventorySlot(mergedTank, (stack, automationType) -> automationType == AutomationType.MANUAL || !insertPredicate.test(stack, automationType),
              insertPredicate, HybridInventorySlot::hasCapability, listener, x, y);
    }

    public static HybridInventorySlot inputOrDrainOrConvert(MergedTank mergedTank, Supplier<?> worldSupplier,
          @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(mergedTank, "Merged tank cannot be null");
        Objects.requireNonNull(worldSupplier, "World supplier cannot be null");
        Predicate<ItemStack> fluidInsertPredicate = FluidInventorySlot.getFillPredicate(mergedTank.getFluidTank());
        Predicate<ItemStack> gasInsertPredicate = stack -> GasInventorySlot.fillOrConvertInsertCheck(mergedTank.getGasTank(), worldSupplier, stack);
        BiPredicate<ItemStack, AutomationType> insertPredicate = (stack, automationType) -> {
            CurrentType currentType = mergedTank.getCurrentType();
            if (currentType == CurrentType.FLUID) {
                return fluidInsertPredicate.test(stack);
            }
            if (currentType.isGas()) {
                return gasInsertPredicate.test(stack);
            }
            return fluidInsertPredicate.test(stack) || gasInsertPredicate.test(stack);
        };
        Predicate<ItemStack> validator = stack -> hasCapability(stack) || gasInsertPredicate.test(stack);
        return new HybridInventorySlot(mergedTank, (stack, automationType) -> automationType == AutomationType.MANUAL ||
              !insertPredicate.test(stack, automationType), insertPredicate, validator, listener, x, y);
    }

    public static HybridInventorySlot outputOrFill(MergedTank mergedTank, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(mergedTank, "Merged tank cannot be null");
        Predicate<ItemStack> gasExtractPredicate = stack -> GasInventorySlot.fillExtractCheck(mergedTank.getGasTank(), stack);
        Predicate<ItemStack> gasInsertPredicate = stack -> GasInventorySlot.fillInsertCheck(mergedTank.getGasTank(), stack);
        return new HybridInventorySlot(mergedTank, (stack, automationType) -> {
            if (automationType == AutomationType.MANUAL) {
                return true;
            }
            CurrentType currentType = mergedTank.getCurrentType();
            if (currentType == CurrentType.FLUID) {
                return true;
            }
            return gasExtractPredicate.test(stack);
        }, (stack, automationType) -> {
            CurrentType currentType = mergedTank.getCurrentType();
            if (currentType == CurrentType.FLUID) {
                return automationType == AutomationType.INTERNAL;
            }
            if (currentType.isGas()) {
                return gasInsertPredicate.test(stack);
            }
            return automationType == AutomationType.INTERNAL && FluidInventorySlot.isFluidContainerItem(stack) || gasInsertPredicate.test(stack);
        }, HybridInventorySlot::hasCapability, listener, x, y);
    }

    private boolean isDraining;
    private boolean isFilling;

    private HybridInventorySlot(MergedTank mergedTank, BiPredicate<ItemStack, AutomationType> canExtract,
          BiPredicate<ItemStack, AutomationType> canInsert, Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        super(mergedTank, canExtract, canInsert, validator, listener, x, y);
    }

    @Override
    public IExtendedFluidTank getFluidTank() {
        return mergedTank.getFluidTank();
    }

    @Override
    public IExtendedGasTank getGasTank() {
        return mergedTank.getGasTank();
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
    public void handleTank(IInventorySlot outputSlot, ContainerEditMode editMode) {
        if (isEmpty()) {
            return;
        }
        if (mergedTank.getCurrentType().isGas()) {
            return;
        }
        IFluidHandlerSlot.super.handleTank(outputSlot, editMode);
    }

    @Override
    public void setStack(@Nonnull ItemStack stack) {
        super.setStack(stack);
        isDraining = false;
        isFilling = false;
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
        super.deserializeNBT(nbt);
        isDraining = nbt.getBoolean(NBTConstants.DRAINING);
        isFilling = nbt.getBoolean(NBTConstants.FILLING);
    }
}
