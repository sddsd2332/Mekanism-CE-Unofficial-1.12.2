package mekanism.common.inventory.slot.chemical;

import mekanism.api.IContentsListener;
import mekanism.api.gas.IExtendedGasTank;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Compatibility wrapper for older chemical package gas slot naming.
 *
 * @deprecated Use {@link mekanism.common.inventory.slot.gas.GasInventorySlot}.
 */
@Deprecated
public class GasInventorySlot extends mekanism.common.inventory.slot.gas.GasInventorySlot {

    /**
     * @deprecated Use {@link mekanism.common.inventory.slot.gas.GasInventorySlot#rotaryDrain(IExtendedGasTank, BooleanSupplier, IContentsListener, int, int)}.
     */
    @Deprecated
    public static GasInventorySlot rotaryDrain(IExtendedGasTank gasTank, BooleanSupplier modeSupplier, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        Objects.requireNonNull(modeSupplier, "Mode supplier cannot be null");
        Predicate<ItemStack> drainInsertPredicate = getDrainInsertPredicate(gasTank, mekanism.common.inventory.slot.gas.GasInventorySlot::getCapability);
        Predicate<ItemStack> insertPredicate = stack -> modeSupplier.getAsBoolean() && drainInsertPredicate.test(stack);
        return new GasInventorySlot(gasTank, insertPredicate.negate(), insertPredicate, GAS_ITEM_VALIDATOR, listener, x, y);
    }

    /**
     * @deprecated Use {@link mekanism.common.inventory.slot.gas.GasInventorySlot#rotaryFill(IExtendedGasTank, BooleanSupplier, IContentsListener, int, int)}.
     */
    @Deprecated
    public static GasInventorySlot rotaryFill(IExtendedGasTank gasTank, BooleanSupplier modeSupplier, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        Objects.requireNonNull(modeSupplier, "Mode supplier cannot be null");
        return new GasInventorySlot(gasTank, getFillExtractPredicate(gasTank, mekanism.common.inventory.slot.gas.GasInventorySlot::getCapability),
              stack -> !modeSupplier.getAsBoolean() && fillInsertCheck(gasTank, stack, mekanism.common.inventory.slot.gas.GasInventorySlot::getCapability),
              GAS_ITEM_VALIDATOR, listener, x, y);
    }

    /**
     * @deprecated Use {@link mekanism.common.inventory.slot.gas.GasInventorySlot#fill(IExtendedGasTank, IContentsListener, int, int)}.
     */
    @Deprecated
    public static GasInventorySlot fill(IExtendedGasTank gasTank, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        return new GasInventorySlot(gasTank, getFillExtractPredicate(gasTank, mekanism.common.inventory.slot.gas.GasInventorySlot::getCapability),
              stack -> fillInsertCheck(gasTank, stack, mekanism.common.inventory.slot.gas.GasInventorySlot::getCapability),
              GAS_ITEM_VALIDATOR, listener, x, y);
    }

    /**
     * @deprecated Use {@link mekanism.common.inventory.slot.gas.GasInventorySlot#drain(IExtendedGasTank, IContentsListener, int, int)}.
     */
    @Deprecated
    public static GasInventorySlot drain(IExtendedGasTank gasTank, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        Predicate<ItemStack> insertPredicate = getDrainInsertPredicate(gasTank, mekanism.common.inventory.slot.gas.GasInventorySlot::getCapability);
        return new GasInventorySlot(gasTank, insertPredicate.negate(), insertPredicate, GAS_ITEM_VALIDATOR, listener, x, y);
    }

    /**
     * @deprecated Use {@link mekanism.common.inventory.slot.gas.GasInventorySlot#fillOrConvert(IExtendedGasTank, Supplier, IContentsListener, int, int)}.
     */
    @Deprecated
    public static GasInventorySlot fillOrConvert(IExtendedGasTank gasTank, Supplier<?> worldSupplier, @Nullable IContentsListener listener, int x, int y) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        Objects.requireNonNull(worldSupplier, "World supplier cannot be null");
        return new GasInventorySlot(gasTank, worldSupplier,
              getFillOrConvertExtractPredicate(gasTank, mekanism.common.inventory.slot.gas.GasInventorySlot::getCapability, worldSupplier),
              getFillOrConvertInsertPredicate(gasTank, mekanism.common.inventory.slot.gas.GasInventorySlot::getCapability, worldSupplier),
              getFillOrConvertValidator(gasTank, worldSupplier), listener, x, y);
    }

    protected GasInventorySlot(IExtendedGasTank gasTank, Predicate<ItemStack> canExtract, Predicate<ItemStack> canInsert,
          @Nullable IContentsListener listener, int x, int y) {
        super(gasTank, canExtract, canInsert, listener, x, y);
    }

    protected GasInventorySlot(IExtendedGasTank gasTank, Predicate<ItemStack> canExtract, Predicate<ItemStack> canInsert,
          Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        super(gasTank, canExtract, canInsert, validator, listener, x, y);
    }

    protected GasInventorySlot(IExtendedGasTank gasTank, Supplier<?> worldSupplier, Predicate<ItemStack> canExtract,
          Predicate<ItemStack> canInsert, @Nullable IContentsListener listener, int x, int y) {
        super(gasTank, worldSupplier, canExtract, canInsert, listener, x, y);
    }

    protected GasInventorySlot(IExtendedGasTank gasTank, Supplier<?> worldSupplier, Predicate<ItemStack> canExtract,
          Predicate<ItemStack> canInsert, Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        super(gasTank, worldSupplier, canExtract, canInsert, validator, listener, x, y);
    }
}
