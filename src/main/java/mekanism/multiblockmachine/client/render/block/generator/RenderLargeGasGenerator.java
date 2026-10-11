package mekanism.multiblockmachine.client.render.block.generator;

import mekanism.client.Utils.RenderTileEntityTime;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import mekanism.multiblockmachine.client.model.generator.ModelLargeGasGenerator;
import mekanism.multiblockmachine.common.MekanismMultiblockMachine;
import mekanism.multiblockmachine.common.tile.generator.TileEntityLargeGasGenerator;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderLargeGasGenerator extends RenderTileEntityTime<TileEntityLargeGasGenerator> {

    private ModelLargeGasGenerator model = new ModelLargeGasGenerator();

    @Override
    public void render(TileEntityLargeGasGenerator tileEntity, double x, double y, double z, float partialTick, int destroyStage, float alpha) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x + 0.5F, (float) y + 1.5F, (float) z + 0.5F);
        bindTexture(MekanismUtils.getResource(MekanismMultiblockMachine.MODID, ResourceType.RENDER, "gasgenerator/LargeGasGenerator.png"));
        MekanismRenderer.rotate(tileEntity.facing, 0, 180, 90, 270);
        GlStateManager.rotate(180, 0, 0, 1);
        model.render(getTime(), 0.0625F, tileEntity.getActive(), rendererDispatcher.renderEngine, true);
        GlStateManager.popMatrix();
    }

    public ModelLargeGasGenerator getModel() {
        return model;
    }
}
