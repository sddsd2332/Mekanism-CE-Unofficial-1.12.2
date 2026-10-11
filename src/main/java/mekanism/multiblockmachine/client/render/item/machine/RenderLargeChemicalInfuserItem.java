package mekanism.multiblockmachine.client.render.item.machine;

import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.item.ItemLayerWrapper;
import mekanism.client.render.item.MekanismItemStackRenderer;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import mekanism.multiblockmachine.client.model.machine.ModelLargeChemicalInfuser;
import mekanism.multiblockmachine.common.MekanismMultiblockMachine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;

@SideOnly(Side.CLIENT)
public class RenderLargeChemicalInfuserItem extends MekanismItemStackRenderer {

    public static ItemLayerWrapper model;

    private static ModelLargeChemicalInfuser cube = new ModelLargeChemicalInfuser();

    @Override
    protected void renderBlockSpecific(@NotNull ItemStack stack, TransformType transformType) {
        GlStateManager.pushMatrix();
        GlStateManager.rotate(180, 0, 0, 1);
        if (transformType == TransformType.THIRD_PERSON_LEFT_HAND) {
            GlStateManager.rotate(-90, 0, 1, 0);
        } else if (transformType != TransformType.GUI) {
            GlStateManager.rotate(90, 0, 1, 0);
        }
        GlStateManager.translate(0, 0, 0);
        MekanismRenderer.bindTexture(MekanismUtils.getResource(MekanismMultiblockMachine.MODID, ResourceType.RENDER_MACHINE, "ChemicalInfuser/ChemicalInfuser.png"));
        cube.render(0, 0.022F, false, Minecraft.getMinecraft().renderEngine, 0, 0, 0, false);
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
