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
        if (itemStack.getCount() > 1 || tier < getTier(itemStack)) {
            return 0;
        }
        IStrictEnergyHandler energyHandler = StorageUtils.getEnergyHandler(itemStack);
        if (energyHandler != null) {
            if (!ignoreTransferLimit) {
                amount = Math.min(amount, IC2Integration.toEU(StorageUtils.getMaxTransfer(itemStack)));
            }
            double energyToStore = IC2Integration.fromEU(amount);
            double remainder = energyHandler.insertEnergy(energyToStore, Action.get(!simulate));
            return IC2Integration.toEU(energyToStore - remainder);
        }
        return 0;
    }

    @Override
    public double discharge(ItemStack itemStack, double amount, int tier, boolean ignoreTransferLimit, boolean external,
                            boolean simulate) {
        if (itemStack.getCount() > 1 || tier < getTier(itemStack)) {
            return 0;
        }
        IStrictEnergyHandler energyHandler = StorageUtils.getEnergyHandler(itemStack);
        if (energyHandler != null) {
            if (!ignoreTransferLimit) {
                amount = Math.min(amount, IC2Integration.toEU(StorageUtils.getMaxTransfer(itemStack)));
            }
            return IC2Integration.toEU(energyHandler.extractEnergy(IC2Integration.fromEU(amount), Action.get(!simulate)));
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
