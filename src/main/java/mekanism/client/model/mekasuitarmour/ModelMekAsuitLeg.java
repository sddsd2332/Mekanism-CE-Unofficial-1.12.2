package mekanism.client.model.mekasuitarmour;

import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.MekanismRenderer.GlowInfo;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ModelMekAsuitLeg extends ModelBiped implements IMekaSuitBloomModel {

    public static final ModelMekAsuitLeg leg = new ModelMekAsuitLeg();
    private static final ResourceLocation GLOW_TEXTURE = MekanismUtils.getResource(ResourceType.RENDER, "mekasuitpants_glow.png");

    ModelRenderer right_leg_armor;
    ModelRenderer leggings_right_leg_exo_brace3_r1;
    ModelRenderer leggings_right_leg_exo_frame1_r1;
    ModelRenderer left_leg_armor;
    ModelRenderer leggings_left_leg_exo_brace3_r1;
    ModelRenderer leggings_left_leg_exo_frame1_r1;

    public ModelMekAsuitLeg() {
        //   super(0, 0, 64, 64);
        textureWidth = 64;
        textureHeight = 64;

        right_leg_armor = new ModelRenderer(this);
        right_leg_armor.setRotationPoint(-2.0F, 12.5F, 0.0F);
        right_leg_armor.cubeList.add(new ModelBox(right_leg_armor, 17, 0, -0.5F, -8.0F, -1.5F, 5, 4, 4, 0.0F, false));
        right_leg_armor.cubeList.add(new ModelBox(right_leg_armor, 18, 35, 0.505F, -8.0F, -2.5F, 3, 2, 1, 0.0F, false));
        right_leg_armor.cubeList.add(new ModelBox(right_leg_armor, 17, 9, -0.5F, -13.0F, -2.5F, 2, 4, 5, 0.0F, false));
        right_leg_armor.cubeList.add(new ModelBox(right_leg_armor, 32, 9, 1.5F, -13.0F, -2.5F, 1, 2, 4, 0.0F, false));
        right_leg_armor.cubeList.add(new ModelBox(right_leg_armor, 36, 0, -0.25F, -7.5F, -2.25F, 1, 1, 1, 0.0F, false));
        right_leg_armor.cubeList.add(new ModelBox(right_leg_armor, 7, 35, 0.5F, -7.5F, 2.0F, 3, 2, 2, 0.0F, false));
        right_leg_armor.cubeList.add(new ModelBox(right_leg_armor, 17, 29, -1.0F, -7.9772F, 0.0F, 3, 2, 3, 0.0F, false));
        right_leg_armor.cubeList.add(new ModelBox(right_leg_armor, 0, 0, -0.01F, -12.5F, -2.01F, 4, 12, 4, 0.0F, false));

        leggings_right_leg_exo_brace3_r1 = new ModelRenderer(this);
        leggings_right_leg_exo_brace3_r1.setRotationPoint(2.0F, 3.5F, 0.0F);
        right_leg_armor.addChild(leggings_right_leg_exo_brace3_r1);
        setRotationAngle(leggings_right_leg_exo_brace3_r1, 0.0873F, 0.0F, 0.0873F);
        leggings_right_leg_exo_brace3_r1.cubeList.add(new ModelBox(leggings_right_leg_exo_brace3_r1, 30, 29, -4.0975F, -17.0787F, 0.0009F, 1, 6, 3, 0.0F, false));

        leggings_right_leg_exo_frame1_r1 = new ModelRenderer(this);
        leggings_right_leg_exo_frame1_r1.setRotationPoint(0.0F, 6.0F, 3.0F);
        right_leg_armor.addChild(leggings_right_leg_exo_frame1_r1);
        setRotationAngle(leggings_right_leg_exo_frame1_r1, 0.0873F, 0.0F, 0.0F);
        leggings_right_leg_exo_frame1_r1.cubeList.add(new ModelBox(leggings_right_leg_exo_frame1_r1, 32, 16, 1.0F, -21.4524F, 0.5899F, 2, 9, 1, 0.0F, false));

        left_leg_armor = new ModelRenderer(this);
        left_leg_armor.setRotationPoint(2.0F, 12.5F, 0.0F);
        left_leg_armor.cubeList.add(new ModelBox(left_leg_armor, 17, 0, -4.5F, -8.0F, -1.5F, 5, 4, 4, 0.0F, false));
        left_leg_armor.cubeList.add(new ModelBox(left_leg_armor, 18, 35, -3.495F, -8.0F, -2.5F, 3, 2, 1, 0.0F, false));
        left_leg_armor.cubeList.add(new ModelBox(left_leg_armor, 17, 19, -1.5F, -13.0F, -2.5F, 2, 4, 5, 0.0F, false));
        left_leg_armor.cubeList.add(new ModelBox(left_leg_armor, 32, 9, -2.5F, -13.0F, -2.5F, 1, 2, 4, 0.0F, false));
        left_leg_armor.cubeList.add(new ModelBox(left_leg_armor, 36, 0, -0.75F, -7.5F, -2.25F, 1, 1, 1, 0.0F, false));
        left_leg_armor.cubeList.add(new ModelBox(left_leg_armor, 7, 35, -3.5F, -7.5F, 2.0F, 3, 2, 2, 0.0F, false));
        left_leg_armor.cubeList.add(new ModelBox(left_leg_armor, 17, 29, -2.0F, -7.9772F, 0.0F, 3, 2, 3, 0.0F, false));
        left_leg_armor.cubeList.add(new ModelBox(left_leg_armor, 0, 17, -4.01F, -12.5F, -2.01F, 4, 12, 4, 0.0F, false));

        leggings_left_leg_exo_brace3_r1 = new ModelRenderer(this);
        leggings_left_leg_exo_brace3_r1.setRotationPoint(-2.0F, 3.5F, 0.0F);
        left_leg_armor.addChild(leggings_left_leg_exo_brace3_r1);
        setRotationAngle(leggings_left_leg_exo_brace3_r1, 0.0873F, 0.0F, -0.0873F);
        leggings_left_leg_exo_brace3_r1.cubeList.add(new ModelBox(leggings_left_leg_exo_brace3_r1, 30, 29, 3.0975F, -17.0787F, 0.0009F, 1, 6, 3, 0.0F, false));

        leggings_left_leg_exo_frame1_r1 = new ModelRenderer(this);
        leggings_left_leg_exo_frame1_r1.setRotationPoint(0.0F, 6.0F, 3.0F);
        left_leg_armor.addChild(leggings_left_leg_exo_frame1_r1);
        setRotationAngle(leggings_left_leg_exo_frame1_r1, 0.0873F, 0.0F, 0.0F);
        leggings_left_leg_exo_frame1_r1.cubeList.add(new ModelBox(leggings_left_leg_exo_frame1_r1, 0, 34, -3.0F, -21.4524F, 0.5899F, 2, 9, 1, 0.0F, false));

        bipedLeftLeg.cubeList.clear();
        bipedLeftLeg.addChild(left_leg_armor);
        bipedRightLeg.cubeList.clear();
        bipedRightLeg.addChild(right_leg_armor);


    }


    public void render(float size) {
        right_leg_armor.render(size);
        left_leg_armor.render(size);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        super.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        if (IMekaSuitBloomModel.shouldRenderDirectGlow()) {
            renderBloom(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        }
    }

    @Override
    public void renderBloom(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.scale(1.001F, 1.001F, 1.001F);
        MekanismRenderer.bindTexture(GLOW_TEXTURE);
        GlowInfo glowInfo = IMekaSuitBloomModel.prepareGlowRender();
        super.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        IMekaSuitBloomModel.finishGlowRender(glowInfo);
        GlStateManager.popMatrix();
    }

    public void renderLeft(float size) {
        left_leg_armor.render(size);
    }

    public void renderRight(float size) {
        right_leg_armor.render(size);
    }

    public void setRotationAngle(ModelRenderer modelRenderer, float x, float y, float z) {
        modelRenderer.rotateAngleX = x;
        modelRenderer.rotateAngleY = y;
        modelRenderer.rotateAngleZ = z;
    }
}
