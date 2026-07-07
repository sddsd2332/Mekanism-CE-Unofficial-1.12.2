package mekanism.common.item.armor;

import mekanism.api.IContentsListener;
import mekanism.api.gas.Gas;
import mekanism.api.gas.IExtendedGasTank;
import mekanism.api.gas.GasStack;
import mekanism.api.mixninapi.EnderMaskMixinHelp;
import mekanism.client.model.mekasuitarmour.ModelMekAsuitHead;
import mekanism.client.model.mekasuitarmour.ModuleSolarHelmet;
import mekanism.common.MekanismFluids;
import mekanism.common.MekanismModules;
import mekanism.common.config.MekanismConfig;
import mekanism.common.interfaces.IOverlayRenderAware;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.item.interfaces.ILegacyGasItem;
import mekanism.common.util.LangUtils;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ItemMekaSuitHelmet extends ItemMekaSuitArmor implements ILegacyGasItem, EnderMaskMixinHelp, IOverlayRenderAware {

    public ItemMekaSuitHelmet() {
        super(0, EntityEquipmentSlot.HEAD);
    }


    @Override
    @SideOnly(Side.CLIENT)
    public ModelBiped getArmorModel(EntityLivingBase entityLiving, ItemStack itemStack, EntityEquipmentSlot armorSlot, ModelBiped _default) {
        ModelMekAsuitHead armorModel = ModelMekAsuitHead.head;
        ModuleSolarHelmet Solar = ModuleSolarHelmet.solar;

        if (isModuleEnabled(itemStack, MekanismModules.SOLAR_RECHARGING_UNIT)) {
            armorModel.helmet_armor.childModels.remove(armorModel.hide);
            if (!armorModel.bipedHead.childModels.contains(Solar.solar_helmet)) {
                armorModel.bipedHead.addChild(Solar.solar_helmet);
            }
        } else {
            armorModel.bipedHead.childModels.remove(Solar.solar_helmet);
            if (!armorModel.helmet_armor.childModels.contains(armorModel.hide)) {
                armorModel.helmet_armor.childModels.add(armorModel.hide);
            }
        }
        return armorModel;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, World world, List<String> tooltip) {
        if (isModuleEnabled(stack, MekanismModules.NUTRITIONAL_INJECTION_UNIT)) {
            GasStack gasStack = getStoredGas(stack);
            if (gasStack == null) {
                tooltip.add(LangUtils.localize("tooltip.noGas") + ".");
            } else {
                tooltip.add(LangUtils.localize("tooltip.stored") + " " + gasStack.getGas().getLocalizedName() + ": " + gasStack.amount);
            }
        }
    }


    private int getNutritionalTransferRate(ItemStack itemstack) {
        return MekanismConfig.current().meka.mekaSuitNutritionalTransferRate.val();
    }

    public int getStored(ItemStack itemstack) {
        GasStack gas = getStoredGas(itemstack);
        return gas == null ? 0 : gas.amount;
    }


    public GasStack useGas(ItemStack itemstack, int amount) {
        GasStack gas = getStoredGas(itemstack);
        if (gas == null) {
            return null;
        }
        Gas type = gas.getGas();
        int gasToUse = Math.min(gas.amount, Math.min(getNutritionalTransferRate(itemstack), amount));
        setStoredGas(itemstack, new GasStack(type, gas.amount - gasToUse));
        return new GasStack(type, gasToUse);
    }


    private GasStack getStoredGas(ItemStack itemstack) {
        if (!isModuleEnabled(itemstack, MekanismModules.NUTRITIONAL_INJECTION_UNIT)) {
            return null;
        }
        return GasInventorySlot.getStoredGas(itemstack, null);
    }

    private void setStoredGas(ItemStack itemstack, GasStack stack) {
        if (!isModuleEnabled(itemstack, MekanismModules.NUTRITIONAL_INJECTION_UNIT)) {
            return;
        }
        if (stack != null && stack.getGas() != null && stack.getGas() != MekanismFluids.NutritionalPaste) {
            return;
        }
        GasInventorySlot.setStoredGas(itemstack, stack, null, getNutritionalCapacity(itemstack));
    }

    private int getNutritionalCapacity(ItemStack itemstack) {
        return isModuleEnabled(itemstack, MekanismModules.NUTRITIONAL_INJECTION_UNIT) ? MekanismConfig.current().meka.mekaSuitNutritionalMaxStorage.val() : 0;
    }

    @Override
    protected boolean hasGasCapabilitySupport() {
        return true;
    }

    @Override
    protected boolean isGasCapabilityEnabled(ItemStack stack) {
        return isModuleEnabled(stack, MekanismModules.NUTRITIONAL_INJECTION_UNIT);
    }

    @Override
    protected int getGasCapabilityRate(ItemStack stack) {
        return getNutritionalTransferRate(stack);
    }

    @Override
    protected int getGasCapabilityCapacity(ItemStack stack) {
        return getNutritionalCapacity(stack);
    }

    @Override
    protected java.util.function.Predicate<GasStack> getGasCapabilityValidator(ItemStack stack) {
        return gasStack -> gasStack != null && gasStack.getGas() == MekanismFluids.NutritionalPaste && isModuleEnabled(stack, MekanismModules.NUTRITIONAL_INJECTION_UNIT);
    }

    @Override
    protected void collectGasCapabilityTanks(ItemStack stack, IContentsListener listener, List<IExtendedGasTank> tanks) {
        super.collectGasCapabilityTanks(stack, listener, tanks);
    }

    @Override
    public boolean isEnderMask(ItemStack stack, EntityPlayer player, EntityEnderman endermanEntity) {
        return armorType == EntityEquipmentSlot.HEAD;
    }

    @Override
    public boolean renderItemOverlayIntoGUI(@NotNull ItemStack stack, int xPosition, int yPosition) {
        return renderGasCapabilityItemOverlayIntoGUI(stack, xPosition, yPosition);
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected int getGasOverlaySortOrder(ItemStack stack, IExtendedGasTank tank, int tankIndex) {
        if (gasOverlayTankSupportsGas(tank, MekanismFluids.Oxygen)) {
            return -100 + tankIndex;
        } else if (gasOverlayTankSupportsGas(tank, MekanismFluids.NutritionalPaste)) {
            return 100 + tankIndex;
        }
        return super.getGasOverlaySortOrder(stack, tank, tankIndex);
    }
}
