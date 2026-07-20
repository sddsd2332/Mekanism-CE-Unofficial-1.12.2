package mekanism.common.integration.ic2;

import ic2.api.item.IElectricItemManager;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.util.StorageUtils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

public class IC2ItemManager implements IElectricItemManager {

    public static final IC2ItemManager INSTANCE = new IC2ItemManager();

    public static IC2ItemManager getManager() {
        return INSTANCE;
    }

    @Override
    public double charge(ItemStack itemStack, double amount, int tier, boolean ignoreTransferLimit, boolean simulate) {
        if (itemStack.getCount() > 1) {
            return 0;
        }
        IStrictEnergyHandler energyHandler = StorageUtils.getEnergyHandler(itemStack);
        if (energyHandler != null) {
            // IC2's charge-slot probe uses positive infinity. Clamp the request in joules before
            // passing it to the handler so that an accepted amount never becomes Infinity - Infinity.
            double energyToStore = IC2Integration.fromEU(amount);
            if (!ignoreTransferLimit) {
                energyToStore = Math.min(energyToStore, StorageUtils.getMaxTransfer(itemStack));
            }
            energyToStore = Math.min(energyToStore, StorageUtils.getNeededEnergy(itemStack));
            if (!(energyToStore > 0) || Double.isNaN(energyToStore)) {
                return 0;
            }
            double remainder = energyHandler.insertEnergy(energyToStore, Action.get(!simulate));
            double accepted = energyToStore - remainder;
            return accepted > 0 && !Double.isNaN(accepted) ? IC2Integration.toEU(accepted) : 0;
        }
        return 0;
    }

    @Override
    public double discharge(ItemStack itemStack, double amount, int tier, boolean ignoreTransferLimit, boolean external,
                            boolean simulate) {
        if (itemStack.getCount() > 1) {
            return 0;
        }
        IStrictEnergyHandler energyHandler = StorageUtils.getEnergyHandler(itemStack);
        if (energyHandler != null) {
            double energyToExtract = IC2Integration.fromEU(amount);
            if (!ignoreTransferLimit) {
                energyToExtract = Math.min(energyToExtract, StorageUtils.getMaxTransfer(itemStack));
            }
            energyToExtract = Math.min(energyToExtract, StorageUtils.getStoredEnergy(itemStack));
            if (!(energyToExtract > 0) || Double.isNaN(energyToExtract)) {
                return 0;
            }
            double extracted = energyHandler.extractEnergy(energyToExtract, Action.get(!simulate));
            return extracted > 0 && !Double.isNaN(extracted) ? IC2Integration.toEU(extracted) : 0;
        }
        return 0;
    }

    @Override
    public boolean canUse(ItemStack itemStack, double amount) {
        return StorageUtils.getStoredEnergy(itemStack) >= IC2Integration.fromEU(amount);
    }

    @Override
    public double getCharge(ItemStack itemStack) {
        return IC2Integration.toEU(StorageUtils.getStoredEnergy(itemStack));
    }

    @Override
    public boolean use(ItemStack itemStack, double amount, EntityLivingBase entity) {
        return false;
    }

    @Override
    public void chargeFromArmor(ItemStack itemStack, EntityLivingBase entity) {
    }

    @Override
    public String getToolTip(ItemStack itemStack) {
        return null;
    }

    @Override
    public double getMaxCharge(ItemStack stack) {
        return IC2Integration.toEU(StorageUtils.getMaxEnergy(stack));
    }

    @Override
    public int getTier(ItemStack stack) {
        return IC2Integration.getMekanismItemTier(stack);
    }
}
