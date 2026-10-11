package mekanism.client.render.item.machine;

import mekanism.client.model.ModelChemicalCrystallizer;
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
public class RenderChemicalCrystallizerItem {

    private static ModelChemicalCrystallizer chemicalCrystallizer = new ModelChemicalCrystallizer();

    public static void renderStack(@Nonnull ItemStack stack, TransformType transformType) {
        GlStateManager.pushMatrix();
        GlStateManager.rotate(180, 0, 0, 1);
        GlStateManager.translate(0.05F, -1.001F, 0.05F);
        MekanismRenderer.bindTexture(MekanismUtils.getResource(ResourceType.RENDER, "ChemicalCrystallizer.png"));
        chemicalCrystallizer.render(0.0625F, false);
        GlStateManager.popMatrix();
    }
}
