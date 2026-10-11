package mekanism.client.model.mekasuitarmour;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ModuleJetpack extends ModelBase {

    public static final ModuleJetpack jetpacks = new ModuleJetpack();

    public final ModelRenderer jetpack;
    ModelRenderer jetpack_body_rod2_r1;

    public ModuleJetpack() {
        textureWidth = 128;
        textureHeight = 128;

        jetpack = new ModelRenderer(this);
        jetpack.setRotationPoint(0.0F, 5.3299F, 2.683F);
        jetpack.cubeList.add(new ModelBox(jetpack, 93, 41, -2.0F, -9.3299F, 0.817F, 4, 7, 2, 0.0F, false));
        jetpack.cubeList.add(new ModelBox(jetpack, 93, 23, -3.0F, -5.3149F, -1.1792F, 6, 5, 3, 0.0F, false));
        jetpack.cubeList.add(new ModelBox(jetpack, 105, 31, 0.5F, -8.3299F, 0.817F, 3, 7, 3, 0.0F, false));
        jetpack.cubeList.add(new ModelBox(jetpack, 93, 31, -3.5F, -8.3299F, 0.817F, 3, 7, 3, 0.0F, false));
        jetpack.cubeList.add(new ModelBox(jetpack, 117, 35, -3.005F, -1.3299F, 1.317F, 2, 2, 2, 0.0F, false));
        jetpack.cubeList.add(new ModelBox(jetpack, 117, 31, 1.005F, -1.3299F, 1.317F, 2, 2, 2, 0.0F, false));
        jetpack.cubeList.add(new ModelBox(jetpack, 117, 23, 2.5F, -7.3299F, 1.317F, 2, 7, 1, 0.0F, false));
        jetpack.cubeList.add(new ModelBox(jetpack, 111, 23, -4.5F, -7.3299F, 1.317F, 2, 7, 1, 0.0F, false));
        jetpack.cubeList.add(new ModelBox(jetpack, 113, 41, 4.0F, -7.8299F, 0.817F, 2, 8, 2, 0.0F, false));
        jetpack.cubeList.add(new ModelBox(jetpack, 105, 41, -6.0F, -7.8299F, 0.817F, 2, 8, 2, 0.0F, false));
        jetpack.cubeList.add(new ModelBox(jetpack, 117, 39, -0.995F, -6.3299F, 2.312F, 2, 1, 1, 0.0F, false));
        jetpack.cubeList.add(new ModelBox(jetpack, 102, 31, -0.995F, -4.3299F, 2.312F, 2, 1, 1, 0.0F, false));

        jetpack_body_rod2_r1 = new ModelRenderer(this);
        jetpack_body_rod2_r1.setRotationPoint(0.0F, -7.3299F, 3.817F);
        jetpack.addChild(jetpack_body_rod2_r1);
        setRotationAngle(jetpack_body_rod2_r1, -0.1745F, 0.0F, 0.0F);
        jetpack_body_rod2_r1.cubeList.add(new ModelBox(jetpack_body_rod2_r1, 93, 50, -2.5F, 0.0F, -0.13F, 1, 5, 1, 0.0F, false));
        jetpack_body_rod2_r1.cubeList.add(new ModelBox(jetpack_body_rod2_r1, 97, 50, 1.5F, 0.0F, -0.13F, 1, 5, 1, 0.0F, false));
    }

    public void render(float size) {
        jetpack.render(size);
    }

    public void setRotationAngle(ModelRenderer modelRenderer, float x, float y, float z) {
        modelRenderer.rotateAngleX = x;
        modelRenderer.rotateAngleY = y;
        modelRenderer.rotateAngleZ = z;
    }
}