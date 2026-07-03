/**
 * https://github.com/mekanism/Mekanism/commit/8c15b6f
 * Modify based on the above submissions
 */
package mekanism.client.render;

import mekanism.client.model.mekasuitarmour.ModelMekAsuitBodyArm;
import mekanism.client.render.bloom.MekaSuitFirstPersonBloomHelper;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.item.armor.ItemMekaSuitArmor;
import mekanism.common.item.armor.ItemMekaSuitBodyArmor;
import mekanism.common.lib.Color;
import mekanism.common.util.MekanismUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped.ArmPose;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;


public class RenderFirstPersonMekaSuitArms {


    @SubscribeEvent
    public void renderHand(RenderSpecificHandEvent event) {
        if (!MekanismConfig.current().client.enableFirstPersonMekaSuitArms.val()) {
            return;
        }
        AbstractClientPlayer player = Minecraft.getMinecraft().player;
        if (player != null && !player.isInvisible() && !player.isSpectator()) {
            ItemStack chestStack = player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
            if (chestStack.getItem() instanceof ItemMekaSuitBodyArmor) {
                ItemStack stack = event.getItemStack();
                EnumHand hand = event.getHand();
                if (stack.isEmpty()) {
                    if (hand == EnumHand.MAIN_HAND) {
                        renderFirstPersonHand(player, player.getPrimaryHand() == EnumHandSide.RIGHT, event.getSwingProgress(), event.getEquipProgress());
                        event.setCanceled(true);
                    }
                } else if (stack.getItem() instanceof ItemMap) {
                    if (hand == EnumHand.MAIN_HAND && player.getHeldItemOffhand().isEmpty()) {
                        renderTwoHandedMap(player, event.getSwingProgress(), event.getEquipProgress(), event.getInterpolatedPitch(), stack);
                    } else {
                        renderOneHandedMap(player, event.getSwingProgress(), event.getEquipProgress(), hand, stack);
                    }
                    event.setCanceled(true);
                }
            }
        }
    }

    private void renderArm(boolean rightHand, AbstractClientPlayer player) {
        ModelMekAsuitBodyArm armor = ModelMekAsuitBodyArm.armorModel;
        ItemStack chestStack = player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        Color renderColor = getRenderTint(ItemMekaSuitArmor.getColorModulation(chestStack));

        armor.setVisible(true);
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
    }

    private void renderFirstPersonHand(AbstractClientPlayer player, boolean rightHand, float swingProgress, float equipProgress) {
        GlStateManager.pushMatrix();
        float f = rightHand ? 1.0F : -1.0F;
        float f1 = MathHelper.sqrt(swingProgress);
        float f2 = -0.3F * MathHelper.sin(f1 * (float) Math.PI);
        float f3 = 0.4F * MathHelper.sin(f1 * ((float) Math.PI * 2F));
        float f4 = -0.4F * MathHelper.sin(swingProgress * (float) Math.PI);
        GlStateManager.translate(f * (f2 + 0.64000005F), f3 - 0.6F + equipProgress * -0.6F, f4 - 0.71999997F);
        GlStateManager.rotate(f * 45.0F, 0.0F, 1.0F, 0.0F);
        float f5 = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float f6 = MathHelper.sin(f1 * (float) Math.PI);
        GlStateManager.rotate(f * f6 * 70.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(f * f5 * -20.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.translate(f * -1.0F, 3.6F, 3.5D);
        GlStateManager.rotate(f * 120.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(200.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(f * -135.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.translate(f * 5.6F, 0.0F, 0.0F);
        renderArm(rightHand, player);
        GlStateManager.popMatrix();
    }

    private void renderTwoHandedMap(AbstractClientPlayer player, float swingProgress, float equipProgress, float interpolatedPitch, ItemStack map) {
        GlStateManager.pushMatrix();
        ItemRenderer firstPersonRenderer = Minecraft.getMinecraft().getItemRenderer();
        float f = MathHelper.sqrt(swingProgress);
        float f1 = -0.2F * MathHelper.sin(swingProgress * (float) Math.PI);
        float f2 = -0.4F * MathHelper.sin(f * (float) Math.PI);
        GlStateManager.translate(0.0D, -f1 / 2.0F, f2);
        float f3 = firstPersonRenderer.getMapAngleFromPitch(interpolatedPitch);
        GlStateManager.translate(0.0D, 0.04F + equipProgress * -1.2F + f3 * -0.5F, -0.72F);
        GlStateManager.rotate(f3 * -85.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.pushMatrix();
        GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(92.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(45.0F, 1.0F, 0.0F, 0.0F);
        renderMapHand(true, player);
        renderMapHand(false, player);
        GlStateManager.popMatrix();
        float f4 = MathHelper.sin(f * (float) Math.PI);
        GlStateManager.rotate(f4 * 20.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(2.0F, 2.0F, 2.0F);
        firstPersonRenderer.renderMapFirstPerson(map);
        GlStateManager.popMatrix();
    }


    private void renderMapHand(boolean rightHand, AbstractClientPlayer player) {
        GlStateManager.pushMatrix();
        float f = rightHand ? 1.0F : -1.0F;
        GlStateManager.rotate(f * -41.0F, 0F, 0F, 1F);
        GlStateManager.translate(f * 0.3F, -1.1F, 0.45F);
        renderArm(rightHand, player);
        GlStateManager.popMatrix();
    }


    private void renderOneHandedMap(AbstractClientPlayer player, float swingProgress, float equipProgress, EnumHand hand, ItemStack map) {
        boolean rightHand = (player.getPrimaryHand() == EnumHandSide.RIGHT) == (hand == EnumHand.MAIN_HAND);
        float f = rightHand ? 1.0F : -1.0F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(f * 0.125F, -0.125D, 0.0D);
        GlStateManager.pushMatrix();
        GlStateManager.rotate(f * 10.0F, 0.0F, 0.0F, 1.0F);
        renderFirstPersonHand(player, rightHand, swingProgress, equipProgress);
        GlStateManager.popMatrix();
        GlStateManager.pushMatrix();
        GlStateManager.translate(f * 0.51F, -0.08F + equipProgress * -1.2F, -0.75D);
        float f1 = MathHelper.sqrt(swingProgress);
        float f2 = MathHelper.sin(f1 * (float) Math.PI);
        float f3 = -0.5F * f2;
        float f4 = 0.4F * MathHelper.sin(f1 * ((float) Math.PI * 2F));
        float f5 = -0.3F * MathHelper.sin(swingProgress * (float) Math.PI);
        GlStateManager.translate(f * f3, f4 - 0.3F * f2, f5);
        GlStateManager.rotate(f2 * -45.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(f * f2 * -30.0F, 0.0F, 1.0F, 0.0F);
        Minecraft.getMinecraft().getItemRenderer().renderMapFirstPerson(map);
        GlStateManager.popMatrix();
        GlStateManager.popMatrix();
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

