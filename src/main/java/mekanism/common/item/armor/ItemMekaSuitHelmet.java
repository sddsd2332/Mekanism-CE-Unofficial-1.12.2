package mekanism.common.item.armor;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasItem;
import mekanism.api.mixninapi.EnderMaskMixinHelp;
import mekanism.client.gui.GuiUtils;
import mekanism.client.model.mekasuitarmour.ModelMekAsuitHead;
import mekanism.client.model.mekasuitarmour.ModuleSolarHelmet;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismFluids;
import mekanism.common.MekanismModules;
import mekanism.common.config.MekanismConfig;
import mekanism.common.interfaces.IOverlayRenderAware;
import mekanism.common.inventory.slot.gas.GasInventorySlot;
import mekanism.common.util.LangUtils;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.List;

public class ItemMekaSuitHelmet extends ItemMekaSuitArmor implements IGasItem, EnderMaskMixinHelp, IOverlayRenderAware {

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
        if (hasModule(stack, MekanismModules.NUTRITIONAL_INJECTION_UNIT)) {
            GasStack gasStack = getGas(stack);
            if (gasStack == null) {
                tooltip.add(LangUtils.localize("tooltip.noGas") + ".");
            } else {
                tooltip.add(LangUtils.localize("tooltip.stored") + " " + gasStack.getGas().getLocalizedName() + ": " + gasStack.amount);
            }
        }
    }


    @Override
    public int getRate(ItemStack itemstack) {
        return MekanismConfig.current().meka.mekaSuitNutritionalTransferRate.val();
    }

    @Override
    public int addGas(ItemStack itemstack, GasStack stack) {
        if (!hasModule(itemstack, MekanismModules.NUTRITIONAL_INJECTION_UNIT)) {
            return 0;
        }
        GasStack storedGas = getGas(itemstack);
        if (storedGas != null && storedGas.getGas() != stack.getGas()) {
            return 0;
        }
        if (stack.getGas() != MekanismFluids.NutritionalPaste) {
            return 0;
        }
        int stored = storedGas == null ? 0 : storedGas.amount;
        int toUse = Math.min(getMaxGas(itemstack) - stored, Math.min(getRate(itemstack), stack.amount));
        setGas(itemstack, new GasStack(stack.getGas(), stored + toUse));
        return toUse;
    }

    public int getStored(ItemStack itemstack) {
        GasStack gas = getGas(itemstack);
        return gas == null ? 0 : gas.amount;
    }


    public GasStack useGas(ItemStack itemstack, int amount) {
        GasStack gas = getGas(itemstack);
        if (gas == null) {
            return null;
        }
        Gas type = gas.getGas();
        int gasToUse = Math.min(gas.amount, Math.min(getRate(itemstack), amount));
        setGas(itemstack, new GasStack(type, gas.amount - gasToUse));
        return new GasStack(type, gasToUse);
    }


    @Override
    public GasStack removeGas(ItemStack itemstack, int amount) {
        GasStack gas = getGas(itemstack);
        if (gas == null || gas.getGas() != MekanismFluids.NutritionalPaste || amount <= 0) {
            return null;
        }
        int gasToUse = Math.min(gas.amount, Math.min(getRate(itemstack), amount));
        if (gasToUse <= 0) {
            return null;
        }
        int remaining = gas.amount - gasToUse;
        setGas(itemstack, remaining <= 0 ? null : new GasStack(gas.getGas(), remaining));
        return new GasStack(gas.getGas(), gasToUse);
    }

    @Override
    public boolean canReceiveGas(ItemStack itemstack, Gas type) {
        return hasModule(itemstack, MekanismModules.NUTRITIONAL_INJECTION_UNIT) && type == MekanismFluids.NutritionalPaste;
    }

    @Override
    public boolean canProvideGas(ItemStack itemstack, Gas type) {
        GasStack gas = getGas(itemstack);
        return gas != null && gas.amount > 0 && (type == null || gas.getGas() == type);
    }

    @Override
    public GasStack getGas(ItemStack itemstack) {
        if (!hasModule(itemstack, MekanismModules.NUTRITIONAL_INJECTION_UNIT)) {
            return null;
        }
        return GasInventorySlot.getStoredGas(itemstack, "gasStored");
    }

    @Override
    public void setGas(ItemStack itemstack, GasStack stack) {
        if (!hasModule(itemstack, MekanismModules.NUTRITIONAL_INJECTION_UNIT)) {
            return;
        }
        if (stack != null && stack.getGas() != null && stack.getGas() != MekanismFluids.NutritionalPaste) {
            return;
        }
        GasInventorySlot.setStoredGas(itemstack, stack, "gasStored", getMaxGas(itemstack));
    }

    @Override
    public int getMaxGas(ItemStack itemstack) {
        return hasModule(itemstack, MekanismModules.NUTRITIONAL_INJECTION_UNIT) ? MekanismConfig.current().meka.mekaSuitNutritionalMaxStorage.val() : 0;
    }

    @Override
    protected boolean hasGasCapabilitySupport() {
        return true;
    }

    @Override
    protected boolean isGasCapabilityEnabled(ItemStack stack) {
        return hasModule(stack, MekanismModules.NUTRITIONAL_INJECTION_UNIT);
    }

    @Override
    protected int getGasCapabilityRate(ItemStack stack) {
        return getRate(stack);
    }

    @Override
    protected int getGasCapabilityCapacity(ItemStack stack) {
        return getMaxGas(stack);
    }

    @Override
    protected String getGasCapabilityLegacyKey() {
        return "gasStored";
    }

    @Override
    protected java.util.function.Predicate<GasStack> getGasCapabilityValidator(ItemStack stack) {
        return gasStack -> gasStack != null && gasStack.getGas() == MekanismFluids.NutritionalPaste && hasModule(stack, MekanismModules.NUTRITIONAL_INJECTION_UNIT);
    }

    @Override
    public boolean isEnderMask(ItemStack stack, EntityPlayer player, EntityEnderman endermanEntity) {
        return armorType == EntityEquipmentSlot.HEAD;
    }

    @Override
    public boolean renderItemOverlayIntoGUI(@NotNull ItemStack stack, int xPosition, int yPosition) {
        if (!stack.isEmpty() && hasModule(stack, MekanismModules.NUTRITIONAL_INJECTION_UNIT)) {
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.disableTexture2D();
            GlStateManager.disableAlpha();
            GlStateManager.disableBlend();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder bufferbuilder = tessellator.getBuffer();
            double health = getDurabilityForDisplayGas(stack);
            int rgbfordisplay = getGASRGBDurabilityForDisplay(stack);
            int i = Math.round(13.0F - (float) health * 13.0F);
            GuiUtils.draw(bufferbuilder, xPosition + 2, yPosition + 12, 13, 1, 0, 0, 0, 255);
            GuiUtils.draw(bufferbuilder, xPosition + 2, yPosition + 12, i, 1, rgbfordisplay >> 16 & 255, rgbfordisplay >> 8 & 255, rgbfordisplay & 255, 255);
            MekanismRenderer.resetColor();
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            GlStateManager.enableDepth();
            return true;
        }
        return false;
    }

    private double getDurabilityForDisplayGas(ItemStack stack) {
        GasStack gas = getGas(stack);
        return 1D - ((gas != null ? (double) gas.amount : 0D) / (double) getMaxGas(stack));
    }


    public int getGASRGBDurabilityForDisplay(@Nonnull ItemStack stack) {
        GasStack gas = getGas(stack);
        if (gas != null) {
            MekanismRenderer.color(gas);
            return gas.getGas().getTint();
        } else {
            return MathHelper.hsvToRGB(Math.max(0.0F, (float) (1 - getDurabilityForDisplay(stack))) / 3.0F, 1.0F, 1.0F);
        }
    }
}
