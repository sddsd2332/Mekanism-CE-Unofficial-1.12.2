package mekanism.multiblockmachine.client.render.item.generator;

import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.item.ItemLayerWrapper;
import mekanism.client.render.item.MekanismItemStackRenderer;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import mekanism.multiblockmachine.client.model.generator.ModelLargeWindGenerator;
import mekanism.multiblockmachine.common.MekanismMultiblockMachine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

@SideOnly(Side.CLIENT)
public class RenderLargeWindGeneratorItem extends MekanismItemStackRenderer {

    private static ModelLargeWindGenerator windGenerator = new ModelLargeWindGenerator();
    private static double angle = 0;
    private static float lastTicksUpdated = 0;
    public static ItemLayerWrapper model;

    @Override
    protected void renderBlockSpecific(@NotNull ItemStack stack, TransformType transformType) {
        GlStateManager.pushMatrix();
        GlStateManager.rotate(180, 0, 0, 1);
        if (transformType == TransformType.THIRD_PERSON_RIGHT_HAND || transformType == TransformType.THIRD_PERSON_LEFT_HAND) {
            GlStateManager.rotate(180, 0, 1, 0);
            GlStateManager.translate(0, 0.4F, 0);
            if (transformType == TransformType.THIRD_PERSON_LEFT_HAND) {
                GlStateManager.rotate(-45, 0, 1, 0);
            } else {
                GlStateManager.rotate(45, 0, 1, 0);
            }
            GlStateManager.rotate(50, 1, 0, 0);
            GlStateManager.scale(2.0F, 2.0F, 2.0F);
            GlStateManager.translate(0, -0.4F, 0);
        } else {
            if (transformType == TransformType.GUI) {
                GlStateManager.rotate(90, 0, 1, 0);
                GlStateManager.translate(0, 0.4F, 0);
            } else if (transformType == TransformType.FIRST_PERSON_RIGHT_HAND) {
                GlStateManager.rotate(180, 0, 1, 0);
            }
            GlStateManager.translate(0, 0.4F, 0);
        }
        MekanismRenderer.bindTexture(MekanismUtils.getResource(MekanismMultiblockMachine.MODID, ResourceType.RENDER, "WindGenerator/LargeWindGenerator.png"));
        if (MekanismConfig.current().client.windGeneratorItem.val()) {
            if (lastTicksUpdated != Minecraft.getMinecraft().getRenderPartialTicks()) {
                angle = (angle + 2) % 360;
                lastTicksUpdated = Minecraft.getMinecraft().getRenderPartialTicks();
            }
        } else {
            angle = 0;
        }
        windGenerator.renderItem(0.002F, angle);
        GlStateManager.popMatrix();
    }

    @Override
    protected void renderItemSpecific(@NotNull ItemStack stack, TransformType transformType) {

    }

    @Override
    protected @NotNull TransformType getTransform(@NotNull ItemStack stack) {
        return model.getTransform();
    }
}
