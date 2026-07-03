package mekanism.api.energy;

import net.minecraft.item.ItemStack;

/**
 * Legacy 1.12 item energy API.
 *
 * @deprecated Use the strict energy capability APIs instead.
 */
@Deprecated
public interface IEnergizedItem {

    /**
     * @deprecated Use the strict energy capability APIs instead.
     */
    @Deprecated
    double getEnergy(ItemStack itemStack);

    /**
     * @deprecated Use the strict energy capability APIs instead.
     */
    @Deprecated
    void setEnergy(ItemStack itemStack, double amount);

    /**
     * @deprecated Use the strict energy capability APIs instead.
     */
    @Deprecated
    double getMaxEnergy(ItemStack itemStack);

    /**
     * @deprecated Use the strict energy capability APIs instead.
     */
    @Deprecated
    double getMaxTransfer(ItemStack itemStack);

    /**
     * @deprecated Use the strict energy capability APIs instead.
     */
    @Deprecated
    boolean canReceive(ItemStack itemStack);

    /**
     * @deprecated Use the strict energy capability APIs instead.
     */
    @Deprecated
    boolean canSend(ItemStack itemStack);

    /**
     * @deprecated Use the strict energy capability APIs instead.
     */
    @Deprecated
    default double getEnergyRatio(ItemStack stack) {
        double maxEnergy = getMaxEnergy(stack);
        return maxEnergy <= 0 ? 0 : getEnergy(stack) / maxEnergy;
    }

    /**
     * @deprecated Use the strict energy capability APIs instead.
     */
    @Deprecated
    default double getNeeded(ItemStack stack) {
        return Math.max(0, getMaxEnergy(stack) - getEnergy(stack));
    }

    /**
     * Inserts energy and returns the unaccepted remainder.
     *
     * @deprecated Use the strict energy capability APIs instead.
     */
    @Deprecated
    default double insert(ItemStack stack, double amount, boolean action) {
        if (amount <= 0) {
            return amount;
        }
        double needed = getNeeded(stack);
        if (needed <= 0) {
            return amount;
        }
        double toAdd = Math.min(amount, needed);
        if (toAdd > 0 && action) {
            setEnergy(stack, getEnergy(stack) + toAdd);
        }
        return amount - toAdd;
    }

    /**
     * Extracts energy and returns the extracted amount.
     *
     * @deprecated Use the strict energy capability APIs instead.
     */
    @Deprecated
    default double extract(ItemStack stack, double amount, boolean action) {
        if (amount <= 0 || getEnergy(stack) <= 0) {
            return 0;
        }
        double extracted = Math.min(getEnergy(stack), amount);
        if (extracted > 0 && action) {
            setEnergy(stack, getEnergy(stack) - extracted);
        }
        return extracted;
    }
}
