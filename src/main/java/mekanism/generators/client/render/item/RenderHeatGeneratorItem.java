package mekanism.generators.client.render.item;

import mekanism.client.render.MekanismRenderer;
import mekanism.generators.client.model.ModelHeatGenerator;
import mekanism.generators.common.util.MekanismGeneratorUtils;
import mekanism.generators.common.util.MekanismGeneratorUtils.ResourceType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

@SideOnly(Side.CLIENT)
public class RenderHeatGeneratorItem {

    private static ModelHeatGenerator heatGenerator = new ModelHeatGenerator();

    public static void renderStack(@Nonnull ItemStack stack, TransformType transformType) {
        GlStateManager.rotate(180, 0, 0, 1);
        GlStateManager.translate(0, -1.0F, 0);
        MekanismRenderer.bindTexture(MekanismGeneratorUtils.getResource(ResourceType.RENDER, "HeatGenerator.png"));
        heatGenerator.render(0.0625F, false, Minecraft.getMinecraft().renderEngine, false);
    }
}
