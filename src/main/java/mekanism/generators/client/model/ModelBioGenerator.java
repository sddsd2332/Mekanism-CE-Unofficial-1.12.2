package mekanism.generators.client.model;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class ModelBioGenerator extends ModelBase {

    ModelRenderer base;
    ModelRenderer sideRight;
    ModelRenderer back;
    ModelRenderer bar;
    ModelRenderer glass;
    ModelRenderer sideLeft;

    public ModelBioGenerator() {
        textureWidth = 64;
        textureHeight = 64;

        base = new ModelRenderer(this, 0, 0);
        base.addBox(0F, 0F, 0F, 16, 7, 16);
        base.setRotationPoint(-8F, 17F, -8F);
        base.setTextureSize(64, 64);
        base.mirror = true;
        setRotation(base, 0F, 0F, 0F);
        sideRight = new ModelRenderer(this, 0, 40);
        sideRight.mirror = true;
        sideRight.addBox(0F, 0F, 0F, 3, 9, 8);
        sideRight.setRotationPoint(5F, 8F, -8F);
        sideRight.setTextureSize(64, 64);
        setRotation(sideRight, 0F, 0F, 0F);
        back = new ModelRenderer(this, 0, 23);
        back.addBox(0F, 0F, 0F, 16, 9, 8);
        back.setRotationPoint(-8F, 8F, 0F);
        back.setTextureSize(64, 64);
        back.mirror = true;
        setRotation(back, 0F, 0F, 0F);
        bar = new ModelRenderer(this, 0, 57);
        bar.addBox(0F, 0F, 0F, 10, 1, 1);
        bar.setRotationPoint(-5F, 8.5F, -7.5F);
        bar.setTextureSize(64, 64);
        bar.mirror = true;
        setRotation(bar, 0F, 0F, 0F);
        glass = new ModelRenderer(this, 22, 40);
        glass.addBox(0F, 0F, 0F, 12, 8, 7);
        glass.setRotationPoint(-6F, 9F, -7F);
        glass.setTextureSize(64, 64);
        glass.mirror = true;
        setRotation(glass, 0F, 0F, 0F);
        sideLeft = new ModelRenderer(this, 0, 40);
        sideLeft.addBox(0F, 0F, 0F, 3, 9, 8);
        sideLeft.setRotationPoint(-8F, 8F, -8F);
        sideLeft.setTextureSize(64, 64);
        sideLeft.mirror = true;
        setRotation(sideLeft, 0F, 0F, 0F);
    }

    public void doRender(float size) {
        base.render(size);
        sideRight.render(size);
        sideLeft.render(size);
        back.render(size);
        bar.render(size);
    }

    public void renderItem(float size) {
        GlStateManager.pushMatrix();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        doRender(size);
        glass.render(size);
        GlStateManager.popMatrix();
    }

    public void render(float size) {
        GlStateManager.pushMatrix();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        doRender(size);
        GlStateManager.popMatrix();
    }

    public void renderGlass(float size) {
        GlStateManager.pushMatrix();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        glass.render(size);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.popMatrix();
    }

    private void setRotation(ModelRenderer model, float x, float y, float z) {
        model.rotateAngleX = x;
        model.rotateAngleY = y;
        model.rotateAngleZ = z;
    }
}
