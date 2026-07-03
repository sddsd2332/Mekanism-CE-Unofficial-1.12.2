package mekanism.common.content.gear.mekasuit;

import mekanism.api.annotations.ParametersAreNotNullByDefault;
import mekanism.api.gas.GasStack;
import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IModule;
import mekanism.api.gear.config.IModuleConfigItem;
import mekanism.api.gear.config.ModuleBooleanData;
import mekanism.api.gear.config.ModuleConfigItemCreator;
import mekanism.common.MekanismFluids;
import mekanism.common.MekanismItems;
import mekanism.common.MekanismLang;
import mekanism.common.MekanismModules;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.ModuleHelper;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.item.armor.ItemMekaSuitArmor;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

@ParametersAreNotNullByDefault
public class ModuleElectrolyticBreathingUnit implements ICustomModule<ModuleElectrolyticBreathingUnit> {

    private IModuleConfigItem<Boolean> fillHeld;

    @Override
    public void init(IModule<ModuleElectrolyticBreathingUnit> module, ModuleConfigItemCreator configItemCreator) {
        fillHeld = configItemCreator.createConfigItem("fill_held", MekanismLang.MODULE_BREATHING_HELD, new ModuleBooleanData());
    }

    @Override
    public void tickServer(IModule<ModuleElectrolyticBreathingUnit> module, EntityPlayer player) {
        int productionRate = 0;
        //Check if the mask is underwater
        //Note: Being in water is checked first to ensure that if it is raining and the player is in water
        // they get the full strength production
        if (player.isInsideOfMaterial(Material.WATER) && player.getEyeHeight() >= 0.11) {
            //If the position the bottom of the mask is almost entirely in water set the production rate to our max rate
            // if the mask is only partially in water treat it as not being in it enough to actually function
            productionRate = getMaxRate(module);
        } else if (isInRain(player)) {
            //If the player is not in water but is in rain set the production to half power
            productionRate = getMaxRate(module) / 2;
        }
        if (productionRate > 0) {
            double usage = MekanismConfig.current().general.FROM_H2.val() * 2;
            int maxRate = (int) Math.min(productionRate, module.getContainerEnergy() / (usage));
            int hydrogenUsed = 0;
            GasStack hydrogenStack = new GasStack(MekanismFluids.Hydrogen, maxRate * 2);
            ItemStack chestStack = player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
            if (checkChestPlate(chestStack)) {
                int accepted;
                if (chestStack.getItem() instanceof ItemMekaSuitArmor armor) {
                    accepted = armor.insertGas(chestStack, hydrogenStack);
                } else {
                    accepted = GasInventorySlot.insertGas(chestStack, hydrogenStack, true);
                }
                hydrogenUsed += accepted;
                hydrogenStack.withAmount(hydrogenStack.amount - accepted);
            }
            if (fillHeld.get()) {
                ItemStack handStack = player.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND);
                if (handStack.getItem() instanceof ItemMekaSuitArmor armor) {
                    hydrogenUsed += armor.insertGas(handStack, hydrogenStack);
                } else {
                    hydrogenUsed += GasInventorySlot.insertGas(handStack, hydrogenStack, true);
                }
            }
            int oxygenUsed = Math.min(maxRate, 300 - player.getAir());
            double used = Math.max(Math.ceil(hydrogenUsed / 2D), oxygenUsed);
            module.useEnergy(player, usage * used);
            player.setAir(player.getAir() + oxygenUsed);
        }
    }

    /**
     * Checks whether the given chestplate should be filled with hydrogen, if it can store hydrogen. Does not check whether the chestplate can store hydrogen.
     *
     * @param chestPlate the chestplate to check
     * @return whether the given chestplate should be filled with hydrogen.
     */
    private boolean checkChestPlate(ItemStack chestPlate) {
        if (chestPlate.getItem() == MekanismItems.MEKASUIT_BODYARMOR) {
            return ModuleHelper.get().load(chestPlate, MekanismModules.JETPACK_UNIT) != null;
        }
        return true;
    }

    private boolean isInRain(EntityPlayer player) {
        BlockPos blockpos = new BlockPos(player.posX, player.posY, player.posZ);
        return player.world.isRainingAt(blockpos) || player.world.isRainingAt(new BlockPos(blockpos.getX(), player.getEntityBoundingBox().maxY, blockpos.getZ()));
    }

    private int getMaxRate(IModule<ModuleElectrolyticBreathingUnit> module) {
        return (int) Math.pow(2, module.getInstalledCount());
    }
}
