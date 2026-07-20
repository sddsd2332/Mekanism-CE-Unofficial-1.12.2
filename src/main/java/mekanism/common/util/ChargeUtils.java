package mekanism.common.util;

import cofh.redstoneflux.api.IEnergyContainerItem;
import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItemManager;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.energy.IStrictEnergyStorage;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.integration.forgeenergy.ForgeEnergyIntegration;
import mekanism.common.integration.ic2.IC2Integration;
import mekanism.common.integration.redstoneflux.RFIntegration;
import mekanism.common.integration.tesla.TeslaIntegration;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import net.darkhax.tesla.api.ITeslaConsumer;
import net.darkhax.tesla.api.ITeslaProducer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

public final class ChargeUtils {

    public static boolean isIC2Chargeable(ItemStack itemStack) {
        return ElectricItem.manager.getMaxCharge(itemStack) > 0 && IC2Integration.chargeItem(itemStack, Integer.MAX_VALUE, true, true) > 0;
    }

    public static boolean isIC2Dischargeable(ItemStack itemStack) {
        return ElectricItem.manager.getMaxCharge(itemStack) > 0 && IC2Integration.dischargeItemToMekanism(itemStack, Integer.MAX_VALUE, true, true) > 0;
    }

    /**
     * Universally discharges an item, and updates the TileEntity's energy level.
     *
     * @param slotID - ID of the slot of which to charge
     * @param storer - TileEntity the item is being charged in
     */
    public static void discharge(int slotID, IStrictEnergyStorage storer) {
        IInventory inv = (TileEntityContainerBlock) storer;
        discharge(inv.getStackInSlot(slotID), storer);
    }

    /**
     * Universally discharges an item, and updates the TileEntity's energy level.
     *
     * @param stack - ItemStack to discharge
     * @param storer - TileEntity the item is being discharged into
     */
    public static void discharge(ItemStack stack, IStrictEnergyStorage storer) {
        if (!stack.isEmpty() && storer.getEnergy() < storer.getMaxEnergy()) {
            if (stack.hasCapability(Capabilities.STRICT_ENERGY_CAPABILITY, null)) {
                IStrictEnergyHandler energyHandler = stack.getCapability(Capabilities.STRICT_ENERGY_CAPABILITY, null);
                if (energyHandler != null && energyHandler.getEnergyContainerCount() > 0) {
                    double needed = storer.getMaxEnergy() - storer.getEnergy();
                    double extracted = energyHandler.extractEnergy(needed, Action.EXECUTE);
                    if (extracted > 0) {
                        storer.setEnergy(storer.getEnergy() + extracted);
                    }
                    return;
                }
            }
            if (MekanismUtils.useTesla() && stack.hasCapability(Capabilities.TESLA_PRODUCER_CAPABILITY, null)) {
                ITeslaProducer producer = stack.getCapability(Capabilities.TESLA_PRODUCER_CAPABILITY, null);
                long needed = TeslaIntegration.toTesla(storer.getMaxEnergy() - storer.getEnergy());
                storer.setEnergy(storer.getEnergy() + TeslaIntegration.fromTesla(producer.takePower(needed, false)));
            } else if (MekanismUtils.useForge() && stack.hasCapability(CapabilityEnergy.ENERGY, null)) {
                IEnergyStorage storage = stack.getCapability(CapabilityEnergy.ENERGY, null);
                if (storage.canExtract()) {
                    int needed = ForgeEnergyIntegration.toForge(storer.getMaxEnergy() - storer.getEnergy());
                    storer.setEnergy(storer.getEnergy() + ForgeEnergyIntegration.fromForge(storage.extractEnergy(needed, false)));
                }
            } else if (MekanismUtils.useRF() && stack.getItem() instanceof IEnergyContainerItem item) {
                int needed = RFIntegration.toRF(storer.getMaxEnergy() - storer.getEnergy());
                storer.setEnergy(storer.getEnergy() + RFIntegration.fromRF(item.extractEnergy(stack, needed, false)));
            } else if (MekanismUtils.useIC2() && isIC2Dischargeable(stack)) {
                double gain = IC2Integration.fromEU(IC2Integration.dischargeItemToMekanism(stack, IC2Integration.toEU(storer.getMaxEnergy() - storer.getEnergy()), true, false));
                storer.setEnergy(storer.getEnergy() + gain);
            } /*else if (stack.getItem() == Items.REDSTONE && storer.getEnergy() + MekanismConfig.current().general.ENERGY_PER_REDSTONE.val() <= storer.getMaxEnergy()) {
                storer.setEnergy(storer.getEnergy() + MekanismConfig.current().general.ENERGY_PER_REDSTONE.val());
                stack.shrink(1);
            } else if (stack.getItem() == Item.getItemFromBlock(Blocks.REDSTONE_BLOCK) && storer.getEnergy() + MekanismConfig.current().general.ENERGY_PER_REDSTONE_BLOCK.val() <= storer.getMaxEnergy()) {
                storer.setEnergy(storer.getEnergy() + MekanismConfig.current().general.ENERGY_PER_REDSTONE_BLOCK.val());
                stack.shrink(1);
            }*/ else if (RecipeHandler.Recipe.ENERGY_RECIPE.containsRecipe(stack) && RecipeHandler.getItemStackToEnergyRecipe(stack) != null) {
                int itemAmount = RecipeHandler.getItemStackToEnergyRecipe(stack).getInput().ingredient.getCount();
                double getEnergy = storer.getEnergy() + RecipeHandler.getItemStackToEnergyRecipe(stack).getOutput().energyOutput;
                if (stack.getCount() >= itemAmount && getEnergy <= storer.getMaxEnergy()) {
                    storer.setEnergy(getEnergy);
                    stack.shrink(itemAmount);
                }
            }
        }
    }


    /**
     * Universally charges an item, and updates the TileEntity's energy level.
     *
     * @param slotID - ID of the slot of which to discharge
     * @param storer - TileEntity the item is being discharged in
     */
    public static void charge(int slotID, IStrictEnergyStorage storer) {
        IInventory inv = (TileEntityContainerBlock) storer;
        charge(inv.getStackInSlot(slotID), storer);
    }

    /**
     * Universally charges an item, and updates the TileEntity's energy level.
     *
     * @param stack  - ItemStack to charge
     * @param storer - TileEntity the item is being discharged in
     */
    public static void charge(ItemStack stack, IStrictEnergyStorage storer) {
        if (!stack.isEmpty() && storer.getEnergy() > 0) {
            if (stack.hasCapability(Capabilities.STRICT_ENERGY_CAPABILITY, null)) {
                IStrictEnergyHandler energyHandler = stack.getCapability(Capabilities.STRICT_ENERGY_CAPABILITY, null);
                if (energyHandler != null && energyHandler.getEnergyContainerCount() > 0) {
                    double remainder = energyHandler.insertEnergy(storer.getEnergy(), Action.EXECUTE);
                    double inserted = storer.getEnergy() - remainder;
                    if (inserted > 0) {
                        storer.setEnergy(storer.getEnergy() - inserted);
                    }
                    return;
                }
            }
            if (MekanismUtils.useTesla() && stack.hasCapability(Capabilities.TESLA_CONSUMER_CAPABILITY, null)) {
                ITeslaConsumer consumer = stack.getCapability(Capabilities.TESLA_CONSUMER_CAPABILITY, null);
                long stored = TeslaIntegration.toTesla(storer.getEnergy());
                storer.setEnergy(storer.getEnergy() - TeslaIntegration.fromTesla(consumer.givePower(stored, false)));
            } else if (MekanismUtils.useForge() && stack.hasCapability(CapabilityEnergy.ENERGY, null)) {
                IEnergyStorage storage = stack.getCapability(CapabilityEnergy.ENERGY, null);
                if (storage.canReceive()) {
                    int stored = ForgeEnergyIntegration.toForge(storer.getEnergy());
                    storer.setEnergy(storer.getEnergy() - ForgeEnergyIntegration.fromForge(storage.receiveEnergy(stored, false)));
                }
            } else if (MekanismUtils.useRF() && stack.getItem() instanceof IEnergyContainerItem item) {
                int toTransfer = RFIntegration.toRF(storer.getEnergy());
                storer.setEnergy(storer.getEnergy() - RFIntegration.fromRF(item.receiveEnergy(stack, toTransfer, false)));
            } else if (MekanismUtils.useIC2() && isIC2Chargeable(stack)) {
                double sent = IC2Integration.fromEU(IC2Integration.chargeItem(stack, IC2Integration.toEU(storer.getEnergy()), true, false));
                storer.setEnergy(storer.getEnergy() - sent);
            }
        }
    }

    /**
     * Whether or not a defined ItemStack can be discharged for energy in some way. Note: The ItemStack must also have energy to discharge.
     *
     * @param itemstack - ItemStack to check
     * @return if the ItemStack can be discharged
     */
    public static boolean canBeDischarged(ItemStack itemstack) {
        return canBeDischargedEnergyContainer(itemstack) || RecipeHandler.Recipe.ENERGY_RECIPE.containsRecipe(itemstack);
        // return itemstack.getItem() == Items.REDSTONE || itemstack.getItem() == Item.getItemFromBlock(Blocks.REDSTONE_BLOCK);
    }

    /**
     * Whether or not a defined ItemStack is a real energy container that currently has energy to discharge.
     */
    public static boolean canBeDischargedEnergyContainer(ItemStack itemstack) {
        if (itemstack.hasCapability(Capabilities.STRICT_ENERGY_CAPABILITY, null)) {
            IStrictEnergyHandler energyHandler = itemstack.getCapability(Capabilities.STRICT_ENERGY_CAPABILITY, null);
            if (energyHandler != null && energyHandler.extractEnergy(1, Action.SIMULATE) > 0) {
                return true;
            }
        }
        if (MekanismUtils.useTesla()) {
            if (itemstack.hasCapability(Capabilities.TESLA_PRODUCER_CAPABILITY, null)) {
                if ((itemstack.hasCapability(Capabilities.TESLA_HOLDER_CAPABILITY, null) && itemstack.getCapability(Capabilities.TESLA_HOLDER_CAPABILITY, null).getCapacity() > 0)) {
                    if (itemstack.getCapability(Capabilities.TESLA_PRODUCER_CAPABILITY, null).takePower(1, true) > 0) {
                        return true;
                    }
                }
            }
        }
        if (MekanismUtils.useForge()) {
            if (itemstack.hasCapability(CapabilityEnergy.ENERGY, null) && itemstack.getCapability(CapabilityEnergy.ENERGY, null).getMaxEnergyStored() > 0) {
                if (itemstack.getCapability(CapabilityEnergy.ENERGY, null).extractEnergy(1, true) > 0) {
                    return true;
                }
            }
        }
        if (MekanismUtils.useRF()) {
            if (itemstack.getItem() instanceof IEnergyContainerItem item && item.getMaxEnergyStored(itemstack) > 0) {
                if (item.extractEnergy(itemstack, 1, true) != 0) {
                    return true;
                }
            }
        }
        if (MekanismUtils.useIC2()) {
            if (ElectricItem.manager.getMaxCharge(itemstack) > 0 && IC2Integration.dischargeItemToMekanism(itemstack, 1, true, true) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether or not a defined ItemStack can be charged with energy in some way. Note: The ItemStack must also have room for more energy.
     *
     * @param itemstack - ItemStack to check
     * @return if the ItemStack can be discharged
     */
    public static boolean canBeCharged(ItemStack itemstack) {
        if (itemstack.hasCapability(Capabilities.STRICT_ENERGY_CAPABILITY, null)) {
            IStrictEnergyHandler energyHandler = itemstack.getCapability(Capabilities.STRICT_ENERGY_CAPABILITY, null);
            if (energyHandler != null && energyHandler.insertEnergy(1, Action.SIMULATE) == 0) {
                return true;
            }
        }
        if (MekanismUtils.useTesla()) {
            if (itemstack.hasCapability(Capabilities.TESLA_CONSUMER_CAPABILITY, null)) {
                if ((itemstack.hasCapability(Capabilities.TESLA_HOLDER_CAPABILITY, null) && itemstack.getCapability(Capabilities.TESLA_HOLDER_CAPABILITY, null).getCapacity() > 0)) {
                    if (itemstack.getCapability(Capabilities.TESLA_CONSUMER_CAPABILITY, null).givePower(1, true) > 0) {
                        return true;
                    }
                }
            }

        }
        if (MekanismUtils.useForge()) {
            if (itemstack.hasCapability(CapabilityEnergy.ENERGY, null) && itemstack.getCapability(CapabilityEnergy.ENERGY, null).getMaxEnergyStored() > 0) {
                if (itemstack.getCapability(CapabilityEnergy.ENERGY, null).receiveEnergy(1, true) > 0) {
                    return true;
                }
            }
        }
        if (MekanismUtils.useRF()) {
            if (itemstack.getItem() instanceof IEnergyContainerItem item && item.getMaxEnergyStored(itemstack) > 0) {
                if (item.receiveEnergy(itemstack, 1, true) > 0) {
                    return true;
                }
            }
        }
        if (MekanismUtils.useIC2()) {
            return isIC2Chargeable(itemstack);
        }
        return false;
    }

    /**
     * Whether or not a defined deemed-electrical ItemStack can be outputted out of a slot. This puts into account whether or not that slot is used for charging or
     * discharging.
     *
     * @param itemstack  - ItemStack to perform the check on
     * @param chargeSlot - whether or not the outputting slot is for charging or discharging
     * @return if the ItemStack can be outputted
     */
    public static boolean canBeOutputted(ItemStack itemstack, boolean chargeSlot) {
        if (itemstack.hasCapability(Capabilities.STRICT_ENERGY_CAPABILITY, null)) {
            IStrictEnergyHandler energyHandler = itemstack.getCapability(Capabilities.STRICT_ENERGY_CAPABILITY, null);
            if (energyHandler != null && energyHandler.getEnergyContainerCount() > 0) {
                return chargeSlot ? energyHandler.insertEnergy(1, Action.SIMULATE) != 0 : energyHandler.extractEnergy(1, Action.SIMULATE) == 0;
            }
        }
        if (MekanismUtils.useTesla()) {
            if (chargeSlot && itemstack.hasCapability(Capabilities.TESLA_CONSUMER_CAPABILITY, null)) {
                ITeslaConsumer consumer = itemstack.getCapability(Capabilities.TESLA_CONSUMER_CAPABILITY, null);
                return consumer.givePower(1, true) == 0;
            } else if (!chargeSlot && itemstack.hasCapability(Capabilities.TESLA_PRODUCER_CAPABILITY, null)) {
                ITeslaProducer producer = itemstack.getCapability(Capabilities.TESLA_PRODUCER_CAPABILITY, null);
                return producer.takePower(1, true) == 0;
            }
        }
        if (MekanismUtils.useForge() && itemstack.hasCapability(CapabilityEnergy.ENERGY, null)) {
            IEnergyStorage storage = itemstack.getCapability(CapabilityEnergy.ENERGY, null);
            if (chargeSlot) {
                return !storage.canReceive() || storage.receiveEnergy(1, true) == 0;
            }
            return !storage.canExtract() || storage.extractEnergy(1, true) == 0;
        }
        if (MekanismUtils.useRF() && itemstack.getItem() instanceof IEnergyContainerItem energyContainer) {
            if (chargeSlot) {
                return energyContainer.receiveEnergy(itemstack, 1, true) == 0;
            }
            return energyContainer.extractEnergy(itemstack, 1, true) == 0;
        }
        if (MekanismUtils.useIC2() && (isIC2Chargeable(itemstack) || isIC2Dischargeable(itemstack))) {
            IElectricItemManager manager = ElectricItem.manager;
            if (manager != null) {
                if (chargeSlot) {
                    return IC2Integration.chargeItem(itemstack, 1, true, true) == 0;
                }
                return IC2Integration.dischargeItemToMekanism(itemstack, 1, true, true) == 0;
            }
        }
        return true;
    }
}
