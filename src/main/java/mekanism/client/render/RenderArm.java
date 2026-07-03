package mekanism.client.render;

import mekanism.client.model.mekasuitarmour.ModelMekAsuitBodyArm;
import mekanism.client.render.bloom.MekaSuitFirstPersonBloomHelper;
import mekanism.common.Mekanism;
import mekanism.common.item.armor.ItemMekaSuitArmor;
import mekanism.common.item.armor.ItemMekaSuitBodyArmor;
import mekanism.common.lib.Color;
import mekanism.common.util.MekanismUtils;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped.ArmPose;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHandSide;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class RenderArm {

    @SubscribeEvent
    public void renderArm(RenderArmEvent event) {
        AbstractClientPlayer player = event.getPlayer();
        ItemStack chestStack = player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        if (chestStack.getItem() instanceof ItemMekaSuitBodyArmor) {
            ModelMekAsuitBodyArm armor = ModelMekAsuitBodyArm.armorModel;
            Color renderColor = getRenderTint(ItemMekaSuitArmor.getColorModulation(chestStack));
            armor.setVisible(true);
            boolean rightHand = event.getArm() == EnumHandSide.RIGHT;
            if (rightHand) {
                armor.rightArmPose = ArmPose.EMPTY;
            } else {
                armor.leftArmPose = ArmPose.EMPTY;
            }
            GlStateManager.enableBlend();
            armor.isSneak = false;
            armor.swingProgress = 0.0F;
            armor.setRotationAngles(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0625F, player);
            MekanismRenderer.bindTexture(MekanismUtils.getResource(MekanismUtils.ResourceType.RENDER, "MekAsuit.png"));
            GlStateManager.color(renderColor.rf(), renderColor.gf(), renderColor.bf(), 1.0F);
            if (rightHand) {
                armor.rightArmRender(0.0625F);
            } else {
                armor.leftArmRender(0.0625F);
            }
            MekanismRenderer.resetColor();
            if (Mekanism.hooks.Bloom) {
                MekaSuitFirstPersonBloomHelper.requestArmBloom(player, rightHand);
            }
            GlStateManager.disableBlend();
            event.setCanceled(true);
        }
    }

    private static Color getRenderTint(Color color) {
        if (color == null) {
            return Color.WHITE;
        }
        double tintStrength = color.ad();
        double tintBase = 1.0D - tintStrength;
        return Color.rgbd(color.rd() * tintStrength + tintBase, color.gd() * tintStrength + tintBase, color.bd() * tintStrength + tintBase);
    }
}
