package mekanism.common.inventory.slot.chemical;

import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.merged.MergedTank;
import mekanism.common.capabilities.merged.MergedTank.CurrentType;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Compatibility wrapper for older merged chemical slot naming.
 *
 * @deprecated Use {@link mekanism.common.inventory.slot.gas.MergedGasInventorySlot}.
 */
@Deprecated
public class MergedChemicalInventorySlot<MERGED extends MergedTank> extends mekanism.common.inventory.slot.gas.MergedGasInventorySlot<MERGED> {

    /**
     * @deprecated Use {@link mekanism.common.inventory.slot.gas.MergedGasInventorySlot#drain(MergedTank, IContentsListener, int, int)}.
     */
    @Deprecated
    public static MergedChemicalInventorySlot<MergedTank> drain(MergedTank mergedTank, @Nullable IContentsListener listener, int x, int y) {
        return createDrainSlot(mergedTank, "Merged chemical tank cannot be null", MergedChemicalInventorySlot::new, listener, x, y);
    }

    /**
     * @deprecated Use {@link mekanism.common.inventory.slot.gas.MergedGasInventorySlot#fill(MergedTank, IContentsListener, int, int)}.
     */
    @Deprecated
    public static MergedChemicalInventorySlot<MergedTank> fill(MergedTank mergedTank, @Nullable IContentsListener listener, int x, int y) {
        return createFillSlot(mergedTank, "Merged chemical tank cannot be null", MergedChemicalInventorySlot::new, listener, x, y);
    }

    protected MergedChemicalInventorySlot(MERGED mergedTank, BiPredicate<ItemStack, AutomationType> canExtract,
          BiPredicate<ItemStack, AutomationType> canInsert, Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        super(mergedTank, canExtract, canInsert, validator, listener, x, y);
    }

    // Compatibility wrappers for older merged chemical slot naming.
    @Deprecated
    public boolean drainChemicalTanks() {
        return drainGasTanks();
    }

    @Deprecated
    public boolean drainChemicalTank() {
        return drainGasTank();
    }

    @Deprecated
    public boolean drainChemicalTank(CurrentType type) {
        return drainGasTank(type);
    }

    @Deprecated
    public boolean fillChemicalTanks() {
        return fillGasTanks();
    }

    @Deprecated
    public boolean fillChemicalTank() {
        return fillGasTank();
    }

    @Deprecated
    public boolean fillChemicalTank(CurrentType type) {
        return fillGasTank(type);
    }
}
