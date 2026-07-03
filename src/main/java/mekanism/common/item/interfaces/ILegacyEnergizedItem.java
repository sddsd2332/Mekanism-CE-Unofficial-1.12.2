package mekanism.common.item.interfaces;

import mekanism.api.Action;
import mekanism.api.energy.IEnergizedItem;
import mekanism.common.util.StorageUtils;
import net.minecraft.item.ItemStack;

/**
 * Bridges Mekanism's legacy energized item API onto the current item energy capability storage.
 *
 * @deprecated Use the strict energy capability APIs instead.
 */
@Deprecated
public interface ILegacyEnergizedItem extends IEnergizedItem {

    @Override
    default double getEnergy(ItemStack itemStack) {
        return StorageUtils.getStoredEnergy(itemStack);
    }

    @Override
    default void setEnergy(ItemStack itemStack, double amount) {
        StorageUtils.setStoredEnergy(itemStack, amount);
    }

    @Override
    default double getMaxEnergy(ItemStack itemStack) {
        return StorageUtils.getMaxEnergy(itemStack);
    }

    @Override
    default double getMaxTransfer(ItemStack itemStack) {
        return StorageUtils.getMaxTransfer(itemStack);
    }

    @Override
    default boolean canReceive(ItemStack itemStack) {
        return StorageUtils.canReceiveEnergy(itemStack);
    }

    @Override
    default boolean canSend(ItemStack itemStack) {
        return StorageUtils.canExtractEnergy(itemStack);
    }

    @Override
    default double insert(ItemStack stack, double amount, boolean action) {
        return StorageUtils.insertEnergy(stack, amount, Action.get(action));
    }

    @Override
    default double extract(ItemStack stack, double amount, boolean action) {
        return StorageUtils.extractEnergy(stack, amount, Action.get(action));
    }
}
