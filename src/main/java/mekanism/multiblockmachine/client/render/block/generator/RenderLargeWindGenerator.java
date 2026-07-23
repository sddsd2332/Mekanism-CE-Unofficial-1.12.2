package mekanism.multiblockmachine.client.render.block.generator;

import mekanism.client.Utils.RenderTileEntityTime;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import mekanism.multiblockmachine.client.model.generator.ModelLargeWindGenerator;
import mekanism.multiblockmachine.common.MekanismMultiblockMachine;
import mekanism.multiblockmachine.common.tile.generator.TileEntityLargeWindGenerator;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderLargeWindGenerator extends RenderTileEntityTime<TileEntityLargeWindGenerator> {

    private final ModelLargeWindGenerator model = new ModelLargeWindGenerator();
    private static final float MODEL_SCALE = 0.0625F;
    private static final ResourceLocation MAIN_TEXTURE = MekanismUtils.getResource(MekanismMultiblockMachine.MODID, ResourceType.RENDER, "WindGenerator/LargeWindGenerator.png");

    @Override
    public void render(TileEntityLargeWindGenerator tileEntity, double x, double y, double z, float partialTick, int destroyStage, float alpha) {
        if (!MekanismConfig.current().client.largeWindGeneratorRending.val()) {
            return;
        }
        boolean active = tileEntity.getActive();
        double angle = angle(tileEntity, partialTick, active);
        int fanRenderDistance = MekanismConfig.current().client.largeWindGeneratorFanRenderDistance.val();
        boolean renderFans = fanRenderDistance <= 0 || tileEntity.getDistanceSq(rendererDispatcher.entityX, rendererDispatcher.entityY, rendererDispatcher.entityZ) <= (double) fanRenderDistance * fanRenderDistance;
        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        try {
            GlStateManager.color(1F, 1F, 1F, 1F);
            GlStateManager.enableCull();
            GlStateManager.enableRescaleNormal();
            GlStateManager.translate((float) x + 0.5F, (float) y + 1.5F, (float) z + 0.5F);
            bindTexture(MAIN_TEXTURE);
            MekanismRenderer.rotate(tileEntity.facing, 0, 180, 90, 270);
            GlStateManager.rotate(180, 0, 0, 1);
            model.renderBlock(getTime(), MODEL_SCALE, angle, active, rendererDispatcher.renderEngine, true, renderFans);
        } finally {
            GlStateManager.popAttrib();
            GlStateManager.popMatrix();
        }
    }

    public double angle(TileEntityLargeWindGenerator tileEntity, float partialTick) {
        return angle(tileEntity, partialTick, tileEntity.getActive());
    }

    private double angle(TileEntityLargeWindGenerator tileEntity, float partialTick, boolean active) {
        double angle = tileEntity.getAngle();
        if (active) {
            angle = (angle + ((tileEntity.getPos().getY() + 46F) / TileEntityLargeWindGenerator.SPEED_SCALED) * partialTick) % 360;
        }
        return angle;
    }

    public ModelLargeWindGenerator getModel() {
        return model;
    }

    @Override
    public boolean isGlobalRenderer(TileEntityLargeWindGenerator te) {
        return MekanismConfig.current().client.largeWindGeneratorisGlobalRenderer.val();
    }

}
