package mekanism.client.render.item.gear;

import mekanism.client.model.ModelAtomicDisassembler;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.item.ItemLayerWrapper;
import mekanism.client.render.item.MekanismItemStackRenderer;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

@SideOnly(Side.CLIENT)
public class RenderMekaTool extends MekanismItemStackRenderer {

    public static ItemLayerWrapper model;
    //  private static final ModelMekaToolLeft mekToolLeft = new ModelMekaToolLeft();
    //  private static final ModelMekaToolRight mekaToolRight = new ModelMekaToolRight();
    private static ModelAtomicDisassembler atomicDisassembler = new ModelAtomicDisassembler();

    @Override
    protected void renderBlockSpecific(@Nonnull ItemStack stack, TransformType transformType) {
    }

    @Override
    protected void renderItemSpecific(@Nonnull ItemStack stack, TransformType transformType) {
        /** 需要手动凹下，先用原子分解机当模型
         GlStateManager.pushMatrix();
         MekanismRenderer.bindTexture(MekanismUtils.getResource(ResourceType.RENDER, "MekaTool.png"));
         GlStateManager.rotate(180,1,0,0);
         GlStateManager.rotate(45,0,1,0);
         GlStateManager.translate(0.5, 0, -0.5);
         /*
         if (transformType == TransformType.FIRST_PERSON_RIGHT_HAND){
         GlStateManager.rotate(90,1,0,0);
         GlStateManager.rotate(-10,0,0,1);
         GlStateManager.translate(0, -3, 0);
         }else if (transformType == TransformType.THIRD_PERSON_RIGHT_HAND){
         GlStateManager.rotate(90,1,0,0);
         GlStateManager.translate(1, -10, -4);
         }else if (transformType == TransformType.GUI) {
         GlStateManager.rotate(90,1,0,0);
         GlStateManager.rotate(40,0,1,0);
         GlStateManager.rotate(90,0,0,1);
         GlStateManager.translate(6.5,-3.5,0);
         }else if (transformType == TransformType.GROUND){
         GlStateManager.translate(1,2,7);
         }else if (transformType == TransformType.HEAD){
         GlStateManager.rotate(270,1,0,0);
         GlStateManager.rotate(220,0,1,0);
         GlStateManager.rotate(90,0,0,1);
         GlStateManager.translate(0,2,0);
         }else if (transformType == TransformType.FIXED){
         GlStateManager.rotate(270,1,0,0);
         GlStateManager.rotate(220,0,1,0);
         GlStateManager.rotate(90,0,0,1);
         GlStateManager.translate( -6, -4, -1);
         }

         else if (transformType == TransformType.FIRST_PERSON_LEFT_HAND){
         GlStateManager.rotate(90,1,0,0);
         GlStateManager.translate(-15, -3, 0);
         }else if (transformType == TransformType.THIRD_PERSON_LEFT_HAND){
         GlStateManager.rotate(90,1,0,0);
         GlStateManager.translate( -15.5, -10, -4);
         }

         if (transformType == TransformType.FIRST_PERSON_LEFT_HAND || transformType == TransformType.THIRD_PERSON_LEFT_HAND) {
         mekToolLeft.render(0.0625F);
         } else {
         mekaToolRight.render(0.0625F);
         }
         GlStateManager.popMatrix();
         */
        GlStateManager.pushMatrix();
        GlStateManager.scale(1.4F, 1.4F, 1.4F);
        GlStateManager.rotate(180, 0, 0, 1);

        if (transformType == TransformType.THIRD_PERSON_RIGHT_HAND || transformType == TransformType.THIRD_PERSON_LEFT_HAND) {
            if (transformType == TransformType.THIRD_PERSON_LEFT_HAND) {
                GlStateManager.rotate(-90, 0, 1, 0);
            }
            GlStateManager.rotate(45, 0, 1, 0);
            GlStateManager.rotate(50, 1, 0, 0);
            GlStateManager.scale(2.0F, 2.0F, 2.0F);
            GlStateManager.translate(0, -0.4F, 0.4F);
        } else if (transformType == TransformType.GUI) {
            GlStateManager.rotate(225, 0, 1, 0);
            GlStateManager.rotate(45, -1, 0, -1);
            GlStateManager.scale(0.6F, 0.6F, 0.6F);
            GlStateManager.translate(0, -0.2F, 0);
        } else {
            if (transformType == TransformType.FIRST_PERSON_LEFT_HAND) {
                GlStateManager.rotate(90, 0, 1, 0);
            }
            GlStateManager.rotate(45, 0, 1, 0);
            GlStateManager.translate(0, -0.7F, 0);
        }

        MekanismRenderer.bindTexture(MekanismUtils.getResource(ResourceType.RENDER, "AtomicDisassemblerBlack.png"));
        atomicDisassembler.render(0.0625F);
        GlStateManager.popMatrix();
    }

    @Nonnull
    @Override
    protected TransformType getTransform(@Nonnull ItemStack stack) {
        return model.getTransform();
    }
}