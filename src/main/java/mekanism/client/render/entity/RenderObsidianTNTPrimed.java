package mekanism.client.render.entity;

import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismBlocks;
import mekanism.common.entity.EntityObsidianTNT;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.GlStateManager.DestFactor;
import net.minecraft.client.renderer.GlStateManager.SourceFactor;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

@SideOnly(Side.CLIENT)
public class RenderObsidianTNTPrimed extends Render<EntityObsidianTNT> {

    public RenderObsidianTNTPrimed(RenderManager renderManager) {
        super(renderManager);
        shadowSize = 0.5F;
    }

    @Override
    public void doRender(@Nonnull EntityObsidianTNT entityobsidiantnt, double x, double y, double z, float entityYaw, float partialTicks) {
        BlockRendererDispatcher renderer = Minecraft.getMinecraft().getBlockRendererDispatcher();
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y + 0.5F, (float) z);

        if (entityobsidiantnt.fuse - partialTicks + 1.0F < 10.0F) {
            float f = 1.0F - (entityobsidiantnt.fuse - partialTicks + 1.0F) / 10.0F;
            f = MathHelper.clamp(f, 0.0F, 1.0F);
            f = f * f;
            f = f * f;
            float scale = 1.0F + f * 0.3F;
            GlStateManager.scale(scale, scale, scale);
        }

        float f3 = (1.0F - ((entityobsidiantnt.fuse - partialTicks) + 1.0F) / 100F) * 0.8F;
        bindEntityTexture(entityobsidiantnt);
        GlStateManager.translate(-0.5F, -0.5F, 0.5F);
        renderer.renderBlockBrightness(MekanismBlocks.ObsidianTNT.getDefaultState(), entityobsidiantnt.getBrightness());
        GlStateManager.translate(0, 0, 1.0F);

        if (entityobsidiantnt.fuse / 5 % 2 == 0) {
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.DST_ALPHA);
            GlStateManager.color(1, 1, 1, f3);
            GlStateManager.doPolygonOffset(-3.0F, -3.0F);
            GlStateManager.enablePolygonOffset();
            renderer.renderBlockBrightness(MekanismBlocks.ObsidianTNT.getDefaultState(), 1.0F);
            GlStateManager.doPolygonOffset(0.0F, 0.0F);
            GlStateManager.disablePolygonOffset();
            MekanismRenderer.resetColor();
            GlStateManager.disableBlend();
            GlStateManager.enableLighting();
            GlStateManager.enableTexture2D();
        }

        GlStateManager.popMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(@Nonnull EntityObsidianTNT entity) {
        return TextureMap.LOCATION_BLOCKS_TEXTURE;
    }
}
