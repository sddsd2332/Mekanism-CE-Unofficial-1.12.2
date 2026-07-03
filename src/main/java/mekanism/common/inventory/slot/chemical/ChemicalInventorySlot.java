package mekanism.common.inventory.slot.chemical;

import mekanism.api.IContentsListener;
import mekanism.api.gas.IExtendedGasTank;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Compatibility wrapper for older gas slot chemical naming.
 *
 * @deprecated Use {@link mekanism.common.inventory.slot.gas.GasHandlerInventorySlot} or {@link mekanism.common.inventory.slot.gas.GasInventorySlot}.
 */
@Deprecated
public abstract class ChemicalInventorySlot extends mekanism.common.inventory.slot.gas.GasInventorySlot {

    protected ChemicalInventorySlot(IExtendedGasTank gasTank, Predicate<ItemStack> canExtract, Predicate<ItemStack> canInsert,
          @Nullable IContentsListener listener, int x, int y) {
        super(gasTank, canExtract, canInsert, listener, x, y);
    }

    protected ChemicalInventorySlot(IExtendedGasTank gasTank, Predicate<ItemStack> canExtract, Predicate<ItemStack> canInsert,
          Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        super(gasTank, canExtract, canInsert, validator, listener, x, y);
    }

    protected ChemicalInventorySlot(IExtendedGasTank gasTank, Supplier<?> worldSupplier, Predicate<ItemStack> canExtract,
          Predicate<ItemStack> canInsert, @Nullable IContentsListener listener, int x, int y) {
        super(gasTank, worldSupplier, canExtract, canInsert, listener, x, y);
    }

    protected ChemicalInventorySlot(IExtendedGasTank gasTank, Supplier<?> worldSupplier, Predicate<ItemStack> canExtract,
          Predicate<ItemStack> canInsert, Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        super(gasTank, worldSupplier, canExtract, canInsert, validator, listener, x, y);
    }
}
