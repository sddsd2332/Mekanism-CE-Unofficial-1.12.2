package mekanism.multiblockmachine.client.model.machine;

import mekanism.client.render.MekanismRenderer;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import mekanism.multiblockmachine.common.MekanismMultiblockMachine;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class ModelLargeChemicalWasher extends ModelBase {

    public static ResourceLocation OVERLAY_ON = MekanismUtils.getResource(MekanismMultiblockMachine.MODID, ResourceType.RENDER_MACHINE, "ChemicalWasher/ChemicalWasher_ON.png");
    public static ResourceLocation OVERLAY_OFF = MekanismUtils.getResource(MekanismMultiblockMachine.MODID, ResourceType.RENDER_MACHINE, "ChemicalWasher/ChemicalWasher_OFF.png");
    private static final ResourceLocation[] LED_ON = createIndexedTextures("ChemicalWasher/LED/LED_", 5);

    ModelRenderer tube;
    ModelRenderer up_2;
    ModelRenderer bb_main;

    public ModelLargeChemicalWasher() {
        textureWidth = 256;
        textureHeight = 256;

        tube = new ModelRenderer(this);
        tube.setRotationPoint(0.0F, 24.0F, 0.0F);
        tube.cubeList.add(new ModelBox(tube, 114, 147, 17.0F, -48.0F, -5.0F, 3, 3, 15, 0.0F, false));
        tube.cubeList.add(new ModelBox(tube, 112, 51, 11.0F, -48.0F, -5.0F, 3, 3, 15, 0.0F, false));
        tube.cubeList.add(new ModelBox(tube, 65, 109, 5.0F, -48.0F, -7.0F, 3, 3, 17, 0.0F, false));

        up_2 = new ModelRenderer(this);
        up_2.setRotationPoint(-0.5F, 16.0F, 21.0F);
        up_2.cubeList.add(new ModelBox(up_2, 98, 109, 15.5F, -35.75F, -32.0F, 2, 1, 2, 0.0F, false));
        up_2.cubeList.add(new ModelBox(up_2, 0, 125, 14.5F, -36.75F, -33.0F, 4, 1, 4, 0.0F, false));
        up_2.cubeList.add(new ModelBox(up_2, 8, 43, 13.5F, -37.0F, -28.0F, 2, 2, 1, 0.0F, false));
        up_2.cubeList.add(new ModelBox(up_2, 36, 5, 12.5F, -37.0F, -30.0F, 1, 2, 3, 0.0F, false));
        up_2.cubeList.add(new ModelBox(up_2, 88, 109, 12.5F, -37.0F, -35.0F, 3, 2, 1, 0.0F, false));
        up_2.cubeList.add(new ModelBox(up_2, 16, 123, 12.5F, -37.0F, -34.0F, 1, 2, 2, 0.0F, false));
        up_2.cubeList.add(new ModelBox(up_2, 38, 28, 12.5F, -36.0F, -40.0F, 2, 2, 2, 0.0F, false));
        up_2.cubeList.add(new ModelBox(up_2, 36, 24, 15.5F, -36.0F, -40.0F, 2, 2, 2, 0.0F, false));
        up_2.cubeList.add(new ModelBox(up_2, 36, 20, 18.5F, -36.0F, -40.0F, 2, 2, 2, 0.0F, false));
        up_2.cubeList.add(new ModelBox(up_2, 36, 0, 19.75F, -37.0F, -35.0F, 1, 2, 3, 0.0F, false));
        up_2.cubeList.add(new ModelBox(up_2, 33, 0, 17.75F, -37.0F, -35.0F, 2, 2, 1, 0.0F, false));
        up_2.cubeList.add(new ModelBox(up_2, 0, 113, 19.5F, -37.0F, -30.0F, 1, 2, 2, 0.0F, false));
        up_2.cubeList.add(new ModelBox(up_2, 0, 43, 17.5F, -37.0F, -28.0F, 3, 2, 1, 0.0F, false));

        bb_main = new ModelRenderer(this);
        bb_main.setRotationPoint(0.0F, 24.0F, 0.0F);
        bb_main.cubeList.add(new ModelBox(bb_main, 0, 0, -23.0F, -5.0F, -23.0F, 46, 5, 46, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 0, 51, -23.0F, -43.0F, 3.0F, 46, 38, 20, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 109, 86, 2.0F, -43.0F, -23.0F, 21, 38, 23, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 0, 109, -23.0F, -43.0F, -23.0F, 21, 38, 23, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 134, 147, -20.0F, -48.0F, -9.0F, 3, 3, 20, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 88, 147, -14.0F, -48.0F, -9.0F, 3, 3, 20, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 132, 51, -8.0F, -48.0F, -9.0F, 3, 3, 20, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 172, 56, 17.0F, -45.0F, 7.0F, 3, 2, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 172, 51, 5.0F, -45.0F, 7.0F, 3, 2, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 171, 159, 5.0F, -45.0F, -7.0F, 3, 2, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 168, 170, 5.0F, -45.0F, -13.0F, 3, 2, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 156, 170, 5.0F, -45.0F, -19.0F, 3, 2, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 0, 38, -18.0F, -44.0F, 16.0F, 5, 1, 4, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 144, 170, -12.0F, -44.0F, 16.0F, 2, 1, 4, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 132, 170, -9.0F, -44.0F, 16.0F, 2, 1, 4, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 120, 170, -6.0F, -44.0F, 16.0F, 2, 1, 4, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 108, 170, -3.0F, -44.0F, 16.0F, 2, 1, 4, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 96, 170, 0.0F, -44.0F, 16.0F, 2, 1, 4, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 84, 170, 5.0F, -45.0F, 13.0F, 3, 2, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 72, 170, 11.0F, -45.0F, 7.0F, 3, 2, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 60, 170, 11.0F, -45.0F, -5.0F, 3, 2, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 48, 170, 17.0F, -45.0F, -5.0F, 3, 2, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 36, 170, -20.0F, -45.0F, -9.0F, 3, 2, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 24, 170, -20.0F, -45.0F, 8.0F, 3, 2, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 12, 170, -14.0F, -45.0F, -9.0F, 3, 2, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 0, 170, -14.0F, -45.0F, 8.0F, 3, 2, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 169, 64, -8.0F, -45.0F, -9.0F, 3, 2, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 160, 162, -8.0F, -45.0F, 8.0F, 3, 2, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 138, 0, 5.0F, -40.0F, 0.0F, 15, 35, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 0, 0, -20.0F, -40.0F, 0.0F, 15, 35, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 168, 74, -2.0F, -40.0F, -19.0F, 4, 3, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 166, 38, -2.0F, -34.0F, -19.0F, 4, 3, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 165, 80, -2.0F, -28.0F, -19.0F, 4, 3, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 88, 161, -2.0F, -22.0F, -19.0F, 4, 3, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 160, 156, -2.0F, -16.0F, -19.0F, 4, 3, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 135, 156, -2.0F, -10.0F, -19.0F, 4, 3, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 114, 153, -2.0F, -40.0F, -6.0F, 4, 3, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 152, 38, -2.0F, -34.0F, -6.0F, 4, 3, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 114, 147, -2.0F, -28.0F, -6.0F, 4, 3, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 138, 38, -2.0F, -22.0F, -6.0F, 4, 3, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 112, 57, -2.0F, -16.0F, -6.0F, 4, 3, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 112, 51, -2.0F, -10.0F, -6.0F, 4, 3, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 160, 147, 12.0F, -12.0F, 23.0F, 8, 8, 1, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 88, 109, 23.0F, -12.0F, -20.0F, 1, 8, 8, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 0, 109, 23.0F, -12.0F, 12.0F, 1, 8, 8, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 0, 51, -24.0F, -12.0F, 12.0F, 1, 8, 8, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 28, 30, -24.0F, -12.0F, -20.0F, 1, 8, 8, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 150, 74, -20.0F, -12.0F, -24.0F, 8, 8, 1, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 135, 147, 12.0F, -12.0F, -24.0F, 8, 8, 1, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 133, 51, -20.0F, -12.0F, 23.0F, 8, 8, 1, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 132, 74, -4.0F, -12.0F, 23.0F, 8, 8, 1, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 0, 109, -18.0F, -44.0F, -15.0F, 2, 2, 2, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 16, 127, -7.0F, -45.0F, -14.0F, 1, 2, 2, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 98, 112, -9.0F, -45.0F, -12.0F, 3, 2, 1, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 65, 129, -13.0F, -45.0F, -12.0F, 2, 2, 1, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 36, 15, -14.0F, -45.0F, -14.0F, 1, 2, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 12, 125, -14.0F, -45.0F, -18.0F, 1, 2, 2, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 88, 112, -14.0F, -45.0F, -19.0F, 3, 2, 1, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 120, 63, -8.75F, -45.0F, -19.0F, 2, 2, 1, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 36, 10, -6.75F, -45.0F, -19.0F, 1, 2, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 8, 67, -18.0F, -44.0F, -18.0F, 2, 2, 2, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 0, 67, 15.0F, -28.0F, -23.5F, 2, 2, 2, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 10, 55, 15.0F, -31.0F, -23.5F, 2, 2, 2, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 0, 55, 15.0F, -34.0F, -23.5F, 2, 2, 2, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 10, 109, 15.0F, -39.0F, -23.5F, 3, 3, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 15, 40, -10.0F, -39.0F, -23.5F, 3, 3, 3, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 10, 51, -10.0F, -28.0F, -23.5F, 2, 2, 2, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 0, 51, -10.0F, -31.0F, -23.5F, 2, 2, 2, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 38, 32, -10.0F, -34.0F, -23.5F, 2, 2, 2, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 133, 60, -12.0F, -44.75F, -17.0F, 4, 1, 4, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 112, 63, -11.0F, -43.75F, -16.0F, 2, 1, 2, 0.0F, false));
        bb_main.cubeList.add(new ModelBox(bb_main, 84, 170, 11.0F, -45.0F, 13.0F, 3, 2, 3, 0.0F, false));
    }

    public void render(double tick, float size, boolean on, TextureManager manager, boolean isEnableGlow) {

        GlStateManager.pushMatrix();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        doRender(size);
        GlStateManager.popMatrix();

        if (isEnableGlow) {
            int animationFrame = getTick(tick);
            GlStateManager.pushMatrix();
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
            GlStateManager.disableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            manager.bindTexture(on ? OVERLAY_ON : OVERLAY_OFF);
            GlStateManager.scale(1.001F, 1.001F, 1.001F);
            GlStateManager.translate(-0.0011F, -0.0011F, -0.0011F);
            MekanismRenderer.GlowInfo glowInfo = MekanismRenderer.enableGlow();
            doRender(size);
            if (on) {
                manager.bindTexture(LED_ON[animationFrame]);
                doRender(size);
            }
            MekanismRenderer.disableGlow(glowInfo);
            GlStateManager.disableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GlStateManager.popMatrix();
        }
    }

    public void renderBloom(double tick, float size, boolean on, TextureManager manager) {
        int animationFrame = getTick(tick);
        GlStateManager.pushMatrix();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        manager.bindTexture(on ? OVERLAY_ON : OVERLAY_OFF);
        GlStateManager.scale(1.0011F, 1.0011F, 1.0011F);
        GlStateManager.translate(-0.0012F, -0.0012F, -0.0012F);
        MekanismRenderer.GlowInfo glowInfo = MekanismRenderer.enableGlow();
        doRender(size);
        if (on) {
            manager.bindTexture(LED_ON[animationFrame]);
            doRender(size);
        }
        MekanismRenderer.disableGlow(glowInfo);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.popMatrix();
    }

    public void doRender(float size) {
        tube.render(size);
        up_2.render(size);
        bb_main.render(size);
    }

    public void setRotationAngle(ModelRenderer modelRenderer, float x, float y, float z) {
        modelRenderer.rotateAngleX = x;
        modelRenderer.rotateAngleY = y;
        modelRenderer.rotateAngleZ = z;
    }

    public int getTick(double tick) {
        if (tick >= 0.1F && tick < 0.2F || tick >= 0.9F && tick < 1.0F) {
            return 0;
        } else if (tick >= 0.2F && tick < 0.3F || tick >= 0.8F && tick < 0.9F) {
            return 1;
        } else if (tick >= 0.3F && tick < 0.4F || tick >= 0.7F && tick < 0.8F) {
            return 2;
        } else if (tick >= 0.4F && tick < 0.5F || tick >= 0.6F && tick < 0.7F) {
            return 3;
        } else if (tick >= 0.5F && tick < 0.6F) {
            return 4;
        }
        return 0;
    }

    private static ResourceLocation[] createIndexedTextures(String prefix, int count) {
        ResourceLocation[] textures = new ResourceLocation[count];
        for (int i = 0; i < count; i++) {
            textures[i] = MekanismUtils.getResource(MekanismMultiblockMachine.MODID, ResourceType.RENDER_MACHINE, prefix + i + ".png");
        }
        return textures;
    }
}
