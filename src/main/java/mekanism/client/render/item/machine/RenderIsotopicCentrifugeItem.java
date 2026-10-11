package mekanism.client.render.item.machine;

import mekanism.client.model.ModelIsotopicCentrifuge;
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
public class RenderIsotopicCentrifugeItem {

    private static ModelIsotopicCentrifuge isotopicCentrifuge = new ModelIsotopicCentrifuge();

    public static void renderStack(@Nonnull ItemStack stack, TransformType transformType) {
        GlStateManager.pushMatrix();
        GlStateManager.rotate(180, 0, 0, 1);
        GlStateManager.scale(0.6F, 0.6F, 0.6F);
        GlStateManager.translate(0, -0.55F, 0);
        MekanismRenderer.bindTexture(MekanismUtils.getResource(ResourceType.RENDER, "IsotopicCentrifuge.png"));
        isotopicCentrifuge.render(0.0625F, false);
        GlStateManager.popMatrix();
    }
}
