package mekanism.client.model;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class ModelNutritionalLiquifier extends ModelBase {
    ModelRenderer base;
    ModelRenderer glass1;
    ModelRenderer glass2;
    ModelRenderer glass3;
    ModelRenderer glass4;
    ModelRenderer top;
    ModelRenderer pillar;
    ModelRenderer pillar2;
    ModelRenderer pillar3;
    ModelRenderer pillar4;
    ModelRenderer blade;
    ModelRenderer support;
    ModelRenderer support1;
    ModelRenderer support2;
    ModelRenderer support3;
    ModelRenderer support4;
    ModelRenderer blade4;
    ModelRenderer blade3;
    ModelRenderer blade2;
    ModelRenderer blade1;

    public ModelNutritionalLiquifier() {
        textureWidth = 128;
        textureHeight = 128;

        base = new ModelRenderer(this);
        base.setRotationPoint(0.0F, 24.0F, 0.0F);
        base.cubeList.add(new ModelBox(base, 0, 0, -8.0F, -5.0F, -8.0F, 16, 5, 16, 0.0F, true));

        glass1 = new ModelRenderer(this);
        glass1.setRotationPoint(0.0F, 24.0F, 0.0F);
        glass1.cubeList.add(new ModelBox(glass1, 31, 40, 7.0F, -15.0F, -7.0F, 1, 10, 14, 0.0F, true));

        glass2 = new ModelRenderer(this);
        glass2.setRotationPoint(-1.0F, 24.0F, -1.0F);
        glass2.cubeList.add(new ModelBox(glass2, 49, 0, -6.0F, -15.0F, -7.0F, 14, 10, 1, 0.0F, true));

        glass3 = new ModelRenderer(this);
        glass3.setRotationPoint(-1.0F, 24.0F, 14.0F);
        glass3.cubeList.add(new ModelBox(glass3, 48, 40, -6.0F, -15.0F, -7.0F, 14, 10, 1, 0.0F, true));

        glass4 = new ModelRenderer(this);
        glass4.setRotationPoint(-15.0F, 24.0F, 0.0F);
        glass4.cubeList.add(new ModelBox(glass4, 0, 40, 7.0F, -15.0F, -7.0F, 1, 10, 14, 0.0F, true));

        top = new ModelRenderer(this);
        top.setRotationPoint(0.0F, 24.0F, 0.0F);
        top.cubeList.add(new ModelBox(top, 0, 22, -8.0F, -16.0F, -8.0F, 16, 1, 16, 0.0F, true));

        pillar = new ModelRenderer(this);
        pillar.setRotationPoint(15.0F, 24.0F, 0.0F);
        pillar.cubeList.add(new ModelBox(pillar, 0, 22, -8.0F, -15.0F, 7.0F, 1, 10, 1, 0.0F, true));

        pillar2 = new ModelRenderer(this);
        pillar2.setRotationPoint(-30.0F, 24.0F, 0.0F);
        pillar2.cubeList.add(new ModelBox(pillar2, 10, 0, 22.0F, -15.0F, 7.0F, 1, 10, 1, 0.0F, true));

        pillar3 = new ModelRenderer(this);
        pillar3.setRotationPoint(0.0F, 24.0F, -15.0F);
        pillar3.cubeList.add(new ModelBox(pillar3, 5, 0, -8.0F, -15.0F, 7.0F, 1, 10, 1, 0.0F, true));

        pillar4 = new ModelRenderer(this);
        pillar4.setRotationPoint(15.0F, 24.0F, -15.0F);
        pillar4.cubeList.add(new ModelBox(pillar4, 0, 0, -8.0F, -15.0F, 7.0F, 1, 10, 1, 0.0F, true));

        blade = new ModelRenderer(this);
        blade.setRotationPoint(0.0F, 24.0F, 0.0F);


        support = new ModelRenderer(this);
        support.setRotationPoint(0.0F, 0.0F, 0.0F);
        blade.addChild(support);
        support.cubeList.add(new ModelBox(support, 5, 22, -1.0F, -7.0F, -1.0F, 2, 2, 2, 0.0F, true));

        support1 = new ModelRenderer(this);
        support1.setRotationPoint(0.0F, 0.0F, 0.0F);
        blade.addChild(support1);
        support1.cubeList.add(new ModelBox(support1, 26, 40, 1.0F, -7.0F, -1.0F, 2, 0, 2, 0.0F, true));

        support2 = new ModelRenderer(this);
        support2.setRotationPoint(0.0F, 0.0F, 0.0F);
        blade.addChild(support2);
        support2.cubeList.add(new ModelBox(support2, 17, 40, -3.0F, -7.0F, -1.0F, 2, 0, 2, 0.0F, true));

        support3 = new ModelRenderer(this);
        support3.setRotationPoint(0.0F, 0.0F, 0.0F);
        blade.addChild(support3);
        support3.cubeList.add(new ModelBox(support3, 0, 40, -1.0F, -7.0F, -3.0F, 2, 0, 2, 0.0F, true));

        support4 = new ModelRenderer(this);
        support4.setRotationPoint(0.0F, 0.0F, 0.0F);
        blade.addChild(support4);
        support4.cubeList.add(new ModelBox(support4, 3, 33, -1.0F, -7.0F, 1.0F, 2, 0, 2, 0.0F, true));

        blade4 = new ModelRenderer(this);
        blade4.setRotationPoint(0.0F, 0.0F, 0.0F);
        blade.addChild(blade4);
        setRotation(blade4, 0.0F, 0.0F, -0.7854F);
        blade4.cubeList.add(new ModelBox(blade4, 5, 30, 7.075F, -2.825F, -1.0F, 2, 0, 2, 0.0F, true));

        blade3 = new ModelRenderer(this);
        blade3.setRotationPoint(0.0F, 0.0F, 0.0F);
        blade.addChild(blade3);
        setRotation(blade3, 0.0F, 0.0F, 0.7854F);
        blade3.cubeList.add(new ModelBox(blade3, 5, 27, -9.075F, -2.825F, -1.0F, 2, 0, 2, 0.0F, true));

        blade2 = new ModelRenderer(this);
        blade2.setRotationPoint(0.0F, 0.0F, 0.0F);
        blade.addChild(blade2);
        setRotation(blade2, 0.7854F, 0.0F, 0.0F);
        blade2.cubeList.add(new ModelBox(blade2, 7, 13, -1.0F, -7.075F, 0.825F, 2, 0, 2, 0.0F, true));

        blade1 = new ModelRenderer(this);
        blade1.setRotationPoint(0.0F, 0.0F, 0.0F);
        blade.addChild(blade1);
        setRotation(blade1, -0.7854F, 0.0F, 0.0F);
        blade1.cubeList.add(new ModelBox(blade1, 0, 12, -1.0F, -7.075F, -2.825F, 2, 0, 2, 0.0F, true));
    }

    public void render(float size, boolean isActive) {
        GlStateManager.pushMatrix();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        base.render(size);
        top.render(size);
        pillar.render(size);
        pillar2.render(size);
        pillar3.render(size);
        pillar4.render(size);
        if (!isActive) {
            blade.render(size);
        }
        GlStateManager.popMatrix();
    }

    public void renderBlade(float size) {
        GlStateManager.pushMatrix();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        blade.render(size);
        GlStateManager.popMatrix();
    }

    public void renderGlass(float size, boolean isEnableGlow) {
        if (isEnableGlow) {
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
            GlStateManager.disableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            glass1.render(size);
            glass2.render(size);
            glass3.render(size);
            glass4.render(size);
            GlStateManager.disableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.shadeModel(GL11.GL_FLAT);
        } else {
            glass1.render(size);
            glass2.render(size);
            glass3.render(size);
            glass4.render(size);
        }
    }

    private void setRotation(ModelRenderer model, float x, float y, float z) {
        model.rotateAngleX = x;
        model.rotateAngleY = y;
        model.rotateAngleZ = z;
    }
}
