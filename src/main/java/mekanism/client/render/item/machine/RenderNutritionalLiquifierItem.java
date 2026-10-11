package mekanism.client.render.item.machine;

import mekanism.client.model.ModelNutritionalLiquifier;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

@SideOnly(Side.CLIENT)
public class RenderNutritionalLiquifierItem {

    private static ModelNutritionalLiquifier nutritionalliquifier = new ModelNutritionalLiquifier();

    public static void renderStack(@Nonnull ItemStack stack, TransformType transformType) {
        GlStateManager.pushMatrix();
        GlStateManager.rotate(180, 0, 0, 1);
        GlStateManager.scale(1.0F, 1.0F, 1.0F);
        GlStateManager.translate(0, -1.0F, 0);
        MekanismRenderer.bindTexture(MekanismUtils.getResource(ResourceType.RENDER, "NutritionalLiquifier.png"));
        nutritionalliquifier.render(0.0625F, false);
        nutritionalliquifier.renderBlade(0.0625F);
        nutritionalliquifier.renderGlass(0.0625F, false);
        GlStateManager.popMatrix();
    }
}
