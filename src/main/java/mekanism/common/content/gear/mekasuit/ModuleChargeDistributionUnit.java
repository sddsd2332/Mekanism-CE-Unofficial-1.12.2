package mekanism.common.content.gear.mekasuit;

import baubles.api.BaublesApi;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.annotations.ParametersAreNotNullByDefault;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IModule;
import mekanism.api.gear.config.IModuleConfigItem;
import mekanism.api.gear.config.ModuleBooleanData;
import mekanism.api.gear.config.ModuleConfigItemCreator;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.network.distribution.EnergySaveTarget;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.integration.energy.EnergyCompatUtils;
import mekanism.common.util.EmitUtils;
import mekanism.common.util.StorageUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

@ParametersAreNotNullByDefault
public class ModuleChargeDistributionUnit implements ICustomModule<ModuleChargeDistributionUnit> {

    private IModuleConfigItem<Boolean> chargeSuit;
    private IModuleConfigItem<Boolean> chargeInventory;

    @Override
    public void init(IModule<ModuleChargeDistributionUnit> module, ModuleConfigItemCreator configItemCreator) {
        chargeSuit = configItemCreator.createConfigItem("charge_suit", MekanismLang.MODULE_CHARGE_SUIT, new ModuleBooleanData());
        chargeInventory = configItemCreator.createConfigItem("charge_inventory", MekanismLang.MODULE_CHARGE_INVENTORY, new ModuleBooleanData(false));
    }

    @Override
    public void tickServer(IModule<ModuleChargeDistributionUnit> module, EntityPlayer player) {
        // charge inventory first
        if (chargeInventory.get()) {
            chargeInventory(module, player);
        }

        // distribute suit charge next
        if (chargeSuit.get()) {
            chargeSuit(player);
        }
    }

    private void chargeSuit(EntityPlayer player) {
        double total = 0;
        EnergySaveTarget saveTarget = new EnergySaveTarget(4);
        for (ItemStack stack : player.inventory.armorInventory) {
            IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(stack, 0);
            if (energyContainer != null) {
                saveTarget.addDelegate(energyContainer);
                total += energyContainer.getEnergy();
            }
        }
        if (saveTarget.getHandlerCount() > 1) {
            EmitUtils.sendToAcceptors(saveTarget, total);
            saveTarget.save();
        }
    }

    private void chargeInventory(IModule<ModuleChargeDistributionUnit> module, EntityPlayer player) {
        IEnergyContainer energyContainer = module.getEnergyContainer();
        if (energyContainer == null) {
            return;
        }
        double toCharge = Math.min(MekanismConfig.current().meka.mekaSuitInventoryChargeRate.val(), energyContainer.getEnergy());
        if (toCharge <= 0) {
            return;
        }
        ItemStack mainHand = player.getHeldItemMainhand();
        ItemStack offHand = player.getHeldItemOffhand();
        toCharge = charge(energyContainer, mainHand, toCharge);
        toCharge = charge(energyContainer, offHand, toCharge);
        if (toCharge <= 0) {
            return;
        }
        List<ItemStack> stacks = new ArrayList<>(player.inventory.mainInventory);
        if (Mekanism.hooks.Baubles) {
            stacks.addAll(chargeBaublesInventory(player));
        }
        for (ItemStack stack : stacks) {
            if (stack != mainHand && stack != offHand) {
                toCharge = charge(energyContainer, stack, toCharge);
                if (toCharge <= 0) {
                    return;
                }
            }
        }
    }

    @Optional.Method(modid = MekanismHooks.Baubles_MOD_ID)
    public List<ItemStack> chargeBaublesInventory(EntityPlayer player) {
        IItemHandler baubles = BaublesApi.getBaublesHandler(player);
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < baubles.getSlots(); i++) {
            stacks.add(baubles.getStackInSlot(i));
        }
        return stacks;
    }

    /** return rejects */
    private double charge(IEnergyContainer energyContainer, ItemStack stack, double amount) {
        if (!stack.isEmpty() && amount > 0) {
            IStrictEnergyHandler handler = EnergyCompatUtils.getStrictEnergyHandler(stack);
            if (handler != null) {
                double remaining = handler.insertEnergy(amount, Action.SIMULATE);
                if (remaining < amount) {
                    double extracted = energyContainer.extract(amount - remaining, Action.EXECUTE, AutomationType.MANUAL);
                    double insertRemainder = handler.insertEnergy(extracted, Action.EXECUTE);
                    return insertRemainder + remaining;
                }
            }
        }
        return amount;
    }
}
