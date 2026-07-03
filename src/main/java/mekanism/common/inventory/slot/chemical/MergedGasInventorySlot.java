package mekanism.common.inventory.slot.chemical;

import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.merged.MergedTank;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Compatibility wrapper for older chemical package merged gas slot naming.
 *
 * @deprecated Use {@link mekanism.common.inventory.slot.gas.MergedGasInventorySlot}.
 */
@Deprecated
public class MergedGasInventorySlot<MERGED extends MergedTank> extends mekanism.common.inventory.slot.gas.MergedGasInventorySlot<MERGED> {

    /**
     * @deprecated Use {@link mekanism.common.inventory.slot.gas.MergedGasInventorySlot#drain(MergedTank, IContentsListener, int, int)}.
     */
    @Deprecated
    public static MergedGasInventorySlot<MergedTank> drain(MergedTank mergedTank, @Nullable IContentsListener listener, int x, int y) {
        return createDrainSlot(mergedTank, "Merged gas tank cannot be null", MergedGasInventorySlot::new, listener, x, y);
    }

    /**
     * @deprecated Use {@link mekanism.common.inventory.slot.gas.MergedGasInventorySlot#fill(MergedTank, IContentsListener, int, int)}.
     */
    @Deprecated
    public static MergedGasInventorySlot<MergedTank> fill(MergedTank mergedTank, @Nullable IContentsListener listener, int x, int y) {
        return createFillSlot(mergedTank, "Merged gas tank cannot be null", MergedGasInventorySlot::new, listener, x, y);
    }

    protected MergedGasInventorySlot(MERGED mergedTank, BiPredicate<ItemStack, AutomationType> canExtract,
          BiPredicate<ItemStack, AutomationType> canInsert, Predicate<ItemStack> validator, @Nullable IContentsListener listener, int x, int y) {
        super(mergedTank, canExtract, canInsert, validator, listener, x, y);
    }
}
