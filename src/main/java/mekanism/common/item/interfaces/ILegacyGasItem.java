package mekanism.common.item.interfaces;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasHandler;
import mekanism.api.gas.IGasItem;
import mekanism.api.gas.IMekanismGasHandler;
import mekanism.common.capabilities.gas.item.RateLimitGasHandler;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Bridges Mekanism's legacy gas item API onto the current item gas capability storage.
 *
 * @deprecated Use the strict gas capability APIs instead.
 */
@Deprecated
public interface ILegacyGasItem extends IGasItem {

    @Override
    default int addGas(ItemStack itemstack, GasStack stack) {
        if (itemstack.isEmpty() || stack == null || stack.amount <= 0 || stack.getGas() == null) {
            return 0;
        }
        IGasHandler gasHandler = GasInventorySlot.getCapability(itemstack);
        if (gasHandler == null) {
            return 0;
        }
        return GasInventorySlot.insertGas(gasHandler, stack.copy(), true);
    }

    @Override
    @Nullable
    default GasStack removeGas(ItemStack itemstack, int amount) {
        if (itemstack.isEmpty() || amount <= 0) {
            return null;
        }
        GasStack stored = getGas(itemstack);
        if (stored == null || stored.amount <= 0) {
            return null;
        }
        IGasHandler gasHandler = GasInventorySlot.getCapability(itemstack);
        if (gasHandler == null) {
            return null;
        }
        return GasInventorySlot.extractGas(gasHandler, stored.getGas(), amount, true);
    }

    @Override
    default boolean canReceiveGas(ItemStack itemstack, mekanism.api.gas.Gas type) {
        IGasHandler gasHandler = GasInventorySlot.getCapability(itemstack);
        return gasHandler != null && GasInventorySlot.canReceiveGas(gasHandler, type);
    }

    @Override
    default boolean canProvideGas(ItemStack itemstack, mekanism.api.gas.Gas type) {
        IGasHandler gasHandler = GasInventorySlot.getCapability(itemstack);
        return gasHandler != null && GasInventorySlot.extractGas(gasHandler, type, 1, false) != null;
    }

    @Override
    @Nullable
    default GasStack getGas(ItemStack itemstack) {
        IGasHandler gasHandler = GasInventorySlot.getCapability(itemstack);
        if (gasHandler == null) {
            return null;
        }
        for (int tank = 0, tanks = GasInventorySlot.getTankCount(gasHandler); tank < tanks; tank++) {
            GasStack stored = GasInventorySlot.getGasInTank(gasHandler, tank);
            if (stored != null && stored.amount > 0 && stored.getGas() != null) {
                return stored.copy();
            }
        }
        return null;
    }

    @Override
    default void setGas(ItemStack itemstack, @Nullable GasStack stack) {
        IMekanismGasHandler gasHandler = GasInventorySlot.getMekanismCapability(itemstack);
        if (gasHandler == null || gasHandler.getCountGasTanks(null) == 0) {
            return;
        }
        GasStack toSet = sanitizeLegacyGas(itemstack, stack);
        if (toSet != null && (!canSetLegacyGas(itemstack, toSet) || !gasHandler.isGasValid(0, toSet, null))) {
            return;
        }
        gasHandler.setGasInTank(0, toSet, null);
    }

    @Override
    default int getMaxGas(ItemStack itemstack) {
        IGasHandler gasHandler = GasInventorySlot.getCapability(itemstack);
        if (gasHandler == null) {
            return 0;
        }
        int capacity = 0;
        for (int tank = 0, tanks = GasInventorySlot.getTankCount(gasHandler); tank < tanks; tank++) {
            capacity += GasInventorySlot.getTankCapacity(gasHandler, tank);
        }
        return capacity;
    }

    @Override
    default int getRate(ItemStack itemstack) {
        IGasHandler gasHandler = GasInventorySlot.getCapability(itemstack);
        if (gasHandler instanceof RateLimitGasHandler rateLimitGasHandler) {
            return Math.max(0, rateLimitGasHandler.getTransferRate());
        }
        return gasHandler == null ? 0 : getMaxGas(itemstack);
    }

    @Nullable
    default GasStack sanitizeLegacyGas(ItemStack itemstack, @Nullable GasStack stack) {
        if (stack == null || stack.amount <= 0 || stack.getGas() == null) {
            return null;
        }
        int capacity = getMaxGas(itemstack);
        return capacity <= 0 ? null : stack.copy().withAmount(Math.min(stack.amount, capacity));
    }

    default boolean canSetLegacyGas(ItemStack itemstack, GasStack stack) {
        return true;
    }
}
