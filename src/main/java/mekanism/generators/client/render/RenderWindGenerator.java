package mekanism.generators.client.render;

import mekanism.client.render.MekanismRenderer;
import mekanism.common.config.MekanismConfig;
import mekanism.generators.client.model.ModelWindGenerator;
import mekanism.generators.common.tile.TileEntityWindGenerator;
import mekanism.generators.common.util.MekanismGeneratorUtils;
import mekanism.generators.common.util.MekanismGeneratorUtils.ResourceType;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderWindGenerator extends TileEntitySpecialRenderer<TileEntityWindGenerator> {

    private ModelWindGenerator model = new ModelWindGenerator();

    @Override
    public void render(TileEntityWindGenerator tileEntity, double x, double y, double z, float partialTick, int destroyStage, float alpha) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x + 0.5F, (float) y + 1.5F, (float) z + 0.5F);
        bindTexture(MekanismGeneratorUtils.getResource(ResourceType.RENDER, "WindGenerator.png"));
        MekanismRenderer.rotate(tileEntity.facing, 0, 180, 90, 270);
        GlStateManager.rotate(180, 0, 0, 1);
        double angle = tileEntity.getAngle();
        if (tileEntity.getActive()) {
            angle = (tileEntity.getAngle() + ((tileEntity.getPos().getY() + 4F) / TileEntityWindGenerator.SPEED_SCALED) * partialTick) % 360;
        }
        int bladeRenderDistance = MekanismConfig.current().client.windGeneratorBladeRenderDistance.val();
        boolean renderBlades = bladeRenderDistance <= 0 || tileEntity.getDistanceSq(rendererDispatcher.entityX, rendererDispatcher.entityY, rendererDispatcher.entityZ) <= (double) bladeRenderDistance * bladeRenderDistance;
        model.renderBlock(0.0625F, angle, renderBlades);
        GlStateManager.popMatrix();
    }
}
