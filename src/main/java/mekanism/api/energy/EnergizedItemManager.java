package mekanism.api.energy;

import net.minecraft.item.ItemStack;

/**
 * Legacy 1.12 item energy helper.
 *
 * @deprecated Use the strict energy capability APIs instead.
 */
@Deprecated
public final class EnergizedItemManager {

    private EnergizedItemManager() {
    }

    /**
     * Discharges a legacy energized item.
     *
     * @deprecated Use the strict energy capability APIs instead.
     */
    @Deprecated
    public static double discharge(ItemStack itemStack, double amount) {
        if (!itemStack.isEmpty() && itemStack.getItem() instanceof IEnergizedItem energizedItem && energizedItem.canSend(itemStack)) {
            return energizedItem.extract(itemStack, Math.min(energizedItem.getMaxTransfer(itemStack), amount), true);
        }
        return 0;
    }

    /**
     * Charges a legacy energized item.
     *
     * @deprecated Use the strict energy capability APIs instead.
     */
    @Deprecated
    public static double charge(ItemStack itemStack, double amount) {
        if (!itemStack.isEmpty() && itemStack.getItem() instanceof IEnergizedItem energizedItem && energizedItem.canReceive(itemStack)) {
            double toSend = Math.min(energizedItem.getMaxTransfer(itemStack), amount);
            return toSend - energizedItem.insert(itemStack, toSend, true);
        }
        return 0;
    }
}
