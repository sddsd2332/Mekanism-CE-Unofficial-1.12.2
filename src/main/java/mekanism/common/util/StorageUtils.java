package mekanism.common.util;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.NBTConstants;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IMekanismStrictEnergyHandler;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nullable;

public class StorageUtils {

    public static double getRatio(int amount, int capacity) {
        return capacity == 0 ? 1 : (double) amount / capacity;
    }

    @Nullable
    public static IStrictEnergyHandler getEnergyHandler(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasCapability(Capabilities.STRICT_ENERGY_CAPABILITY, null)) {
            return null;
        }
        Object handler = stack.getCapability(Capabilities.STRICT_ENERGY_CAPABILITY, null);
        if (!(handler instanceof IStrictEnergyHandler energyHandler)) {
            return null;
        }
        return energyHandler.getEnergyContainerCount() > 0 ? energyHandler : null;
    }

    @Nullable
    public static IEnergyContainer getEnergyContainer(ItemStack stack, int container) {
        IStrictEnergyHandler energyHandler = getEnergyHandler(stack);
        if (energyHandler instanceof IMekanismStrictEnergyHandler strictEnergyHandler) {
            return strictEnergyHandler.getEnergyContainer(container, null);
        }
        return null;
    }

    public static double getStoredEnergy(ItemStack stack) {
        IStrictEnergyHandler energyHandler = getEnergyHandler(stack);
        if (energyHandler == null) {
            return 0;
        }
        double stored = 0;
        for (int container = 0, containers = energyHandler.getEnergyContainerCount(); container < containers; container++) {
            stored += energyHandler.getEnergy(container);
        }
        return stored;
    }

    /**
     * Reads persisted item energy without using capabilities. This mirrors modern Mekanism's
     * attachment fallback and lets stacked block items restore their stored energy when placed.
     */
    public static double getStoredEnergyFromItemData(ItemStack stack) {
        double stored = 0;
        NBTTagList containers = ItemDataUtils.getList(stack, NBTConstants.ENERGY_CONTAINERS);
        for (int container = 0; container < containers.tagCount(); container++) {
            stored += getStoredEnergy(containers.getCompoundTagAt(container));
        }
        if (stored > 0) {
            return stored;
        }
        NBTTagCompound dataMap = ItemDataUtils.getDataMapIfPresent(stack);
        if (dataMap != null) {
            stored = getStoredEnergy(dataMap);
            if (stored > 0) {
                return stored;
            }
        }
        return stack.hasTagCompound() ? getStoredEnergy(stack.getTagCompound()) : 0;
    }

    private static double getStoredEnergy(NBTTagCompound tag) {
        if (tag.hasKey(NBTConstants.STORED)) {
            return tag.getDouble(NBTConstants.STORED);
        } else if (tag.hasKey(NBTConstants.ENERGY_STORED)) {
            return tag.getDouble(NBTConstants.ENERGY_STORED);
        } else if (tag.hasKey("energyStored")) {
            return tag.getDouble("energyStored");
        }
        return 0;
    }

    public static double getStoredEnergyForDisplay(ItemStack stack) {
        double stored = getStoredEnergy(stack);
        return stored > 0 ? stored : getStoredEnergyFromItemData(stack);
    }

    public static void setStoredEnergy(ItemStack stack, double energy) {
        setStoredEnergy(stack, energy, getMaxEnergy(stack));
    }

    public static void setStoredEnergy(ItemStack stack, double energy, double maxEnergy) {
        IStrictEnergyHandler energyHandler = getEnergyHandler(stack);
        if (energyHandler != null && energyHandler.getEnergyContainerCount() > 0) {
            double remaining = Math.max(0, energy);
            for (int container = 0, containers = energyHandler.getEnergyContainerCount(); container < containers; container++) {
                double toSet = Math.min(remaining, energyHandler.getMaxEnergy(container));
                energyHandler.setEnergy(container, toSet);
                remaining -= toSet;
            }
        }
    }

    public static double getMaxEnergy(ItemStack stack) {
        IStrictEnergyHandler energyHandler = getEnergyHandler(stack);
        if (energyHandler == null) {
            return 0;
        }
        double max = 0;
        for (int container = 0, containers = energyHandler.getEnergyContainerCount(); container < containers; container++) {
            max += energyHandler.getMaxEnergy(container);
        }
        return max;
    }

    public static double getNeededEnergy(ItemStack stack) {
        IStrictEnergyHandler energyHandler = getEnergyHandler(stack);
        if (energyHandler == null) {
            return 0;
        }
        double needed = 0;
        for (int container = 0, containers = energyHandler.getEnergyContainerCount(); container < containers; container++) {
            needed += energyHandler.getNeededEnergy(container);
        }
        return needed;
    }

    public static double getInsertableEnergy(ItemStack stack) {
        IStrictEnergyHandler energyHandler = getEnergyHandler(stack);
        if (energyHandler == null) {
            return 0;
        }
        double maxEnergy = getMaxEnergy(stack);
        return maxEnergy <= 0 ? 0 : getInsertableEnergy(energyHandler, maxEnergy);
    }

    private static double getInsertableEnergy(IStrictEnergyHandler energyHandler, double amount) {
        return Math.max(0, amount - energyHandler.insertEnergy(amount, Action.SIMULATE));
    }

    public static double getExtractableEnergy(ItemStack stack) {
        IStrictEnergyHandler energyHandler = getEnergyHandler(stack);
        if (energyHandler == null) {
            return 0;
        }
        double maxEnergy = getMaxEnergy(stack);
        return maxEnergy <= 0 ? 0 : energyHandler.extractEnergy(maxEnergy, Action.SIMULATE);
    }

    public static double getMaxTransfer(ItemStack stack) {
        IStrictEnergyHandler energyHandler = getEnergyHandler(stack);
        if (energyHandler == null) {
            return 0;
        }
        double maxEnergy = getMaxEnergy(stack);
        if (maxEnergy <= 0) {
            return 0;
        }
        return Math.max(getInsertableEnergy(energyHandler, maxEnergy), energyHandler.extractEnergy(maxEnergy, Action.SIMULATE));
    }

    public static boolean canReceiveEnergy(ItemStack stack) {
        return getInsertableEnergy(stack) > 0;
    }

    public static boolean canExtractEnergy(ItemStack stack) {
        return getExtractableEnergy(stack) > 0;
    }

    public static double insertEnergy(ItemStack stack, double amount, Action action) {
        if (amount <= 0) {
            return 0;
        }
        IStrictEnergyHandler energyHandler = getEnergyHandler(stack);
        return energyHandler == null ? amount : energyHandler.insertEnergy(amount, action);
    }

    public static double extractEnergy(ItemStack stack, double amount, Action action) {
        if (amount <= 0) {
            return 0;
        }
        IStrictEnergyHandler energyHandler = getEnergyHandler(stack);
        return energyHandler == null ? 0 : energyHandler.extractEnergy(amount, action);
    }

    public static ItemStack getFilledEnergyVariant(ItemStack stack) {
        IStrictEnergyHandler energyHandler = getEnergyHandler(stack);
        if (energyHandler != null) {
            for (int container = 0, containers = energyHandler.getEnergyContainerCount(); container < containers; container++) {
                energyHandler.setEnergy(container, energyHandler.getMaxEnergy(container));
            }
        }
        return stack;
    }

    public static double extractFromContainer(ItemStack stack, double amount, Action action) {
        IEnergyContainer energyContainer = getEnergyContainer(stack, 0);
        return energyContainer == null ? 0 : energyContainer.extract(amount, action, AutomationType.MANUAL);
    }

    public static double getEnergyRatio(ItemStack stack) {
        double max = getMaxEnergy(stack);
        return max <= 0 ? 0 : Math.min(1, getStoredEnergy(stack) / max);
    }

    public static int getEnergyBarWidth(ItemStack stack) {
        if (stack.getCount() > 1) {
            return 0;
        }
        return Math.round(13.0F - 13.0F * (float) (1 - getEnergyRatio(stack)));
    }
}
