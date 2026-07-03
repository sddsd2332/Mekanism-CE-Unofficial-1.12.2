package mekanism.common.inventory.slot.gas;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.NBTConstants;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.gas.IGasHandler;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.IGasHandlerSlot;
import mekanism.common.recipe.GasConversionHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Gas handler slot state and gas conversion predicate helpers.
 */
public abstract class GasHandlerInventorySlot extends BasicInventorySlot implements IGasHandlerSlot {

    protected static final Predicate<ItemStack> GAS_ITEM_VALIDATOR = GasInventorySlot::isGasContainerItem;

    /**
     * Gets the GasStack from ItemStack conversion, ignoring the size of the item stack.
     */
    @Nullable
    public static GasStack getPotentialConversion(@Nullable Object world, ItemStack itemStack) {
        return GasConversionHandler.getItemGas(itemStack, Integer.MAX_VALUE, (gas, amount) -> gas == null ? null : new GasStack(gas, amount));
    }

    @Nullable
    protected static GasStack getValidConversion(IExtendedGasTank gasTank, Supplier<?> worldSupplier, ItemStack stack) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        Objects.requireNonNull(worldSupplier, "World supplier cannot be null");
        GasStack conversion = getPotentialConversion(worldSupplier.get(), stack);
        return conversion != null && gasTank.isValid(conversion) ? conversion : null;
    }

    protected static Predicate<ItemStack> getFillOrConvertValidator(IExtendedGasTank gasTank, Supplier<?> worldSupplier) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        Objects.requireNonNull(worldSupplier, "World supplier cannot be null");
        return stack -> GasInventorySlot.isGasContainerItem(stack) || getValidConversion(gasTank, worldSupplier, stack) != null;
    }

    protected static Predicate<ItemStack> getFillOrConvertExtractPredicate(IExtendedGasTank gasTank, Supplier<?> worldSupplier) {
        return getFillOrConvertExtractPredicate(gasTank, GasInventorySlot::getCapability, worldSupplier);
    }

    protected static Predicate<ItemStack> getFillOrConvertExtractPredicate(IExtendedGasTank gasTank, Function<ItemStack, IGasHandler> handlerFunction,
          Supplier<?> worldSupplier) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        Objects.requireNonNull(handlerFunction, "Gas handler function cannot be null");
        Objects.requireNonNull(worldSupplier, "World supplier cannot be null");
        return stack -> {
            IGasHandler handler = handlerFunction.apply(stack);
            if (handler != null) {
                return GasInventorySlot.fillExtractCheck(gasTank, handler);
            }
            if (GasInventorySlot.isGasContainerItem(stack)) {
                return GasInventorySlot.fillExtractCheck(gasTank, stack);
            }
            return getValidConversion(gasTank, worldSupplier, stack) == null;
        };
    }

    protected static Predicate<ItemStack> getFillOrConvertInsertPredicate(IExtendedGasTank gasTank, Supplier<?> worldSupplier) {
        return getFillOrConvertInsertPredicate(gasTank, GasInventorySlot::getCapability, worldSupplier);
    }

    protected static Predicate<ItemStack> getFillOrConvertInsertPredicate(IExtendedGasTank gasTank, Function<ItemStack, IGasHandler> handlerFunction,
          Supplier<?> worldSupplier) {
        Objects.requireNonNull(gasTank, "Gas tank cannot be null");
        Objects.requireNonNull(handlerFunction, "Gas handler function cannot be null");
        Objects.requireNonNull(worldSupplier, "World supplier cannot be null");
        return stack -> {
            IGasHandler handler = handlerFunction.apply(stack);
            if (handler != null ? GasInventorySlot.fillInsertCheck(gasTank, handler) : GasInventorySlot.fillInsertCheck(gasTank, stack)) {
                return true;
            }
            GasStack conversion = getValidConversion(gasTank, worldSupplier, stack);
            if (conversion == null) {
                return false;
            }
            GasStack remainder = gasTank.insert(conversion, Action.SIMULATE, AutomationType.INTERNAL);
            int remainderAmount = remainder == null ? 0 : remainder.amount;
            return remainderAmount < conversion.amount || gasTank.getNeeded() == 0 && gasTank.isTypeEqual(conversion);
        };
    }

    private final Supplier<?> worldSupplier;
    protected final IExtendedGasTank gasTank;
    private boolean draining;
    private boolean filling;

    protected GasHandlerInventorySlot(IExtendedGasTank gasTank, Predicate<ItemStack> canExtract, Predicate<ItemStack> canInsert,
          @Nullable IContentsListener listener, int x, int y) {
        this(gasTank, () -> null, canExtract, canInsert, GAS_ITEM_VALIDATOR, listener, x, y);
    }

    protected GasHandlerInventorySlot(IExtendedGasTank gasTank, Predicate<ItemStack> canExtract, Predicate<ItemStack> canInsert,
          Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        this(gasTank, () -> null, canExtract, canInsert, validator, listener, x, y);
    }

    protected GasHandlerInventorySlot(IExtendedGasTank gasTank, Supplier<?> worldSupplier, Predicate<ItemStack> canExtract,
          Predicate<ItemStack> canInsert, @Nullable IContentsListener listener, int x, int y) {
        this(gasTank, worldSupplier, canExtract, canInsert, GAS_ITEM_VALIDATOR, listener, x, y);
    }

    protected GasHandlerInventorySlot(IExtendedGasTank gasTank, Supplier<?> worldSupplier, Predicate<ItemStack> canExtract,
          Predicate<ItemStack> canInsert, Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        super(canExtract, canInsert, validator, listener, x, y);
        setSlotType(ContainerSlotType.EXTRA);
        this.gasTank = gasTank;
        this.worldSupplier = worldSupplier;
    }

    @Override
    public IExtendedGasTank getGasTank() {
        return gasTank;
    }

    protected Supplier<?> getWorldSupplier() {
        return worldSupplier;
    }

    @Override
    public boolean isDraining() {
        return draining;
    }

    @Override
    public boolean isFilling() {
        return filling;
    }

    @Override
    public void setDraining(boolean draining) {
        this.draining = draining;
    }

    @Override
    public void setFilling(boolean filling) {
        this.filling = filling;
    }

    @Override
    public void setStack(@Nonnull ItemStack stack) {
        super.setStack(stack);
        draining = false;
        filling = false;
    }

    public abstract boolean fillTankOrConvert();

    public abstract boolean fillTank();

    public boolean drainTank() {
        return drainTank(true);
    }

    public abstract boolean drainTank(boolean doDraw);

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = super.serializeNBT();
        if (draining) {
            nbt.setBoolean(NBTConstants.DRAINING, true);
        }
        if (filling) {
            nbt.setBoolean(NBTConstants.FILLING, true);
        }
        return nbt;
    }

    @Override
    public void deserializeNBT(@Nonnull NBTTagCompound nbt) {
        draining = nbt.getBoolean(NBTConstants.DRAINING);
        filling = nbt.getBoolean(NBTConstants.FILLING);
        super.deserializeNBT(nbt);
    }
}
