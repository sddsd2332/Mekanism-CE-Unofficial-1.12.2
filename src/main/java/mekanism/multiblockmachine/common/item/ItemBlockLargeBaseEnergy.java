package mekanism.multiblockmachine.common.item;

import cofh.redstoneflux.api.IEnergyContainerItem;
import ic2.api.item.IElectricItemManager;
import ic2.api.item.ISpecialElectricItem;
import mekanism.api.Action;
import mekanism.api.EnumColor;
import mekanism.api.functions.ConstantPredicates;
import mekanism.common.Upgrade;
import mekanism.common.capabilities.ItemCapabilityWrapper;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import mekanism.common.capabilities.energy.item.RateLimitEnergyHandler;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.integration.forgeenergy.ForgeEnergyItemWrapper;
import mekanism.common.integration.ic2.IC2ItemManager;
import mekanism.common.integration.redstoneflux.RFIntegration;
import mekanism.common.integration.tesla.TeslaItemWrapper;
import mekanism.common.item.interfaces.ILegacyEnergizedItem;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StorageUtils;
import mekanism.multiblockmachine.common.MultiblockMachineUpgrades;
import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.Optional.Interface;
import net.minecraftforge.fml.common.Optional.InterfaceList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

@InterfaceList({
        @Interface(iface = "cofh.redstoneflux.api.IEnergyContainerItem", modid = MekanismHooks.REDSTONEFLUX_MOD_ID),
        @Interface(iface = "ic2.api.item.ISpecialElectricItem", modid = MekanismHooks.IC2_MOD_ID)
})
public abstract class ItemBlockLargeBaseEnergy extends ItemBlockLargeBase implements ILegacyEnergizedItem, ISpecialElectricItem, IEnergyContainerItem {

    public String name;

    public ItemBlockLargeBaseEnergy(Block block, String machine) {
        super(block, machine);
    }


    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack itemstack, World world, @Nonnull List<String> list, @Nonnull ITooltipFlag flag) {
        super.addInformation(itemstack, world, list, flag);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemstack, List<String> list, World world, ITooltipFlag flag) {
        if (itemstack.getCount() <= 1) {
            list.add(EnumColor.BRIGHT_GREEN + LangUtils.localize("tooltip.storedEnergy") + ": " + EnumColor.GREY + MekanismUtils.getEnergyDisplay(StorageUtils.getStoredEnergy(itemstack), getEnergyCapacity(itemstack)));
        }
    }

    public void setStoredEnergy(ItemStack itemStack, double amount) {
        if (itemStack.getCount() > 1) {
            return;
        }
        StorageUtils.setStoredEnergy(itemStack, amount, getEnergyCapacity(itemStack));
    }

    public double getEnergyCapacity(ItemStack itemStack) {
        if (itemStack.getCount() > 1) {
            return 0;
        }
        double storage = getMachineStorage() * getThread(itemStack);
        return Upgrade.hasUpgradeData(ItemDataUtils.getDataMapIfPresent(itemStack)) ? MekanismUtils.getMaxEnergy(itemStack, storage) : storage;
    }

    abstract double getMachineStorage();

    private int getThread(ItemStack stack) {
        int thread = 1;
        Map<Upgrade, Integer> upgrades = Upgrade.buildComponentMap(ItemDataUtils.getDataMapIfPresent(stack));
        if (upgrades.get(MultiblockMachineUpgrades.THREAD) != null) {
            thread += upgrades.get(MultiblockMachineUpgrades.THREAD);
        }
        NBTTagCompound dataMap = ItemDataUtils.getDataMap(stack);
        if (dataMap.isEmpty() && stack.getTagCompound() != null) {
            stack.getTagCompound().removeTag(ItemDataUtils.DATA_ID);
        }
        if (stack.getTagCompound() != null && stack.getTagCompound().isEmpty()) {
            stack.setTagCompound(null);
        }
        return thread;
    }

    public double getEnergyTransfer(ItemStack itemStack) {
        if (itemStack.getCount() > 1) {
            return 0;
        }
        return getEnergyCapacity(itemStack) * 0.005;
    }

    public boolean canReceiveEnergy(ItemStack itemStack) {
        return itemStack.getCount() <= 1;
    }

    public boolean canSendEnergy(ItemStack itemStack) {
        return false;
    }

    @Override
    @Optional.Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int receiveEnergy(ItemStack theItem, int energy, boolean simulate) {
        if (theItem.getCount() > 1) {
            return 0;
        }
        if (canReceiveEnergy(theItem)) {
            double amount = RFIntegration.fromRF(energy);
            double remainder = StorageUtils.insertEnergy(theItem, amount, Action.get(!simulate));
            return RFIntegration.toRF(amount - remainder);
        }
        return 0;
    }

    @Override
    @Optional.Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int extractEnergy(ItemStack theItem, int energy, boolean simulate) {
        if (theItem.getCount() > 1) {
            return 0;
        }
        if (canSendEnergy(theItem)) {
            return RFIntegration.toRF(StorageUtils.extractEnergy(theItem, RFIntegration.fromRF(energy), Action.get(!simulate)));
        }
        return 0;
    }

    @Override
    @Optional.Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int getEnergyStored(ItemStack theItem) {
        if (theItem.getCount() > 1) {
            return 0;
        }
        return RFIntegration.toRF(StorageUtils.getStoredEnergy(theItem));
    }

    @Override
    @Optional.Method(modid = MekanismHooks.REDSTONEFLUX_MOD_ID)
    public int getMaxEnergyStored(ItemStack theItem) {
        if (theItem.getCount() > 1) {
            return 0;
        }
        return RFIntegration.toRF(getEnergyCapacity(theItem));
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return stack.getCount() == 1 && StorageUtils.getStoredEnergy(stack) > 0;
    }


    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        double capacity = getEnergyCapacity(stack);
        return capacity <= 0 ? 1D : 1D - (StorageUtils.getStoredEnergy(stack) / capacity);
    }

    @Override
    @Optional.Method(modid = MekanismHooks.IC2_MOD_ID)
    public IElectricItemManager getManager(ItemStack itemStack) {
        return IC2ItemManager.getManager();
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
        return new ItemCapabilityWrapper(stack, new TeslaItemWrapper(), new ForgeEnergyItemWrapper(),
              RateLimitEnergyHandler.create(() -> getEnergyTransfer(stack), () -> getEnergyCapacity(stack), BasicEnergyContainer.manualOnly, ConstantPredicates.alwaysTrue()));
    }

}
