package mekanism.client.model.mekasuitarmour;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ModuleGravitational extends ModelBase {
    public static final ModuleGravitational gravitational = new ModuleGravitational();

    public final ModelRenderer gravitational_modulator;
    ModelRenderer modulator_arm_left;
    ModelRenderer modulator_arm_right;

    public ModuleGravitational() {
        textureWidth = 128;
        textureHeight = 128;

        gravitational_modulator = new ModelRenderer(this);
        gravitational_modulator.setRotationPoint(0.0F, 8.3299F, 2.683F);
        gravitational_modulator.cubeList.add(new ModelBox(gravitational_modulator, 91, 64, -3.0F, -9.3299F, 0.837F, 6, 7, 2, 0.0F, false));
        gravitational_modulator.cubeList.add(new ModelBox(gravitational_modulator, 107, 64, -4.0F, -7.3299F, 0.817F, 8, 5, 1, 0.0F, false));
        gravitational_modulator.cubeList.add(new ModelBox(gravitational_modulator, 109, 56, -3.0F, -9.3299F, 2.817F, 6, 2, 2, 0.0F, false));
        gravitational_modulator.cubeList.add(new ModelBox(gravitational_modulator, 91, 56, -3.0F, -2.3299F, -1.183F, 6, 2, 6, 0.0F, false));
        gravitational_modulator.cubeList.add(new ModelBox(gravitational_modulator, 91, 56, -1.0F, -3.3299F, 2.817F, 2, 4, 1, 0.0F, false));
        gravitational_modulator.cubeList.add(new ModelBox(gravitational_modulator, 93, 75, -1.0F, -7.3299F, 2.817F, 2, 1, 1, 0.0F, false));
        gravitational_modulator.cubeList.add(new ModelBox(gravitational_modulator, 91, 73, -1.0F, -0.3299F, -0.183F, 2, 1, 3, 0.0F, false));

        modulator_arm_left = new ModelRenderer(this);
        modulator_arm_left.setRotationPoint(1.0F, 4.1701F, 3.817F);
        gravitational_modulator.addChild(modulator_arm_left);
        modulator_arm_left.cubeList.add(new ModelBox(modulator_arm_left, 115, 60, 1.5F, -7.5F, -2.0F, 1, 1, 3, 0.0F, false));
        modulator_arm_left.cubeList.add(new ModelBox(modulator_arm_left, 109, 60, 0.5F, -7.5F, 0.5F, 2, 1, 1, 0.0F, false));
        modulator_arm_left.cubeList.add(new ModelBox(modulator_arm_left, 115, 60, 1.5F, -9.5F, -2.0F, 1, 1, 3, 0.0F, false));
        modulator_arm_left.cubeList.add(new ModelBox(modulator_arm_left, 109, 60, 0.5F, -9.5F, 0.5F, 2, 1, 1, 0.0F, false));
        modulator_arm_left.cubeList.add(new ModelBox(modulator_arm_left, 115, 60, 1.5F, -11.5F, -2.0F, 1, 1, 3, 0.0F, false));
        modulator_arm_left.cubeList.add(new ModelBox(modulator_arm_left, 109, 60, 0.5F, -11.5F, 0.5F, 2, 1, 1, 0.0F, false));

        modulator_arm_right = new ModelRenderer(this);
        modulator_arm_right.setRotationPoint(-2.875F, -4.8299F, 4.067F);
        gravitational_modulator.addChild(modulator_arm_right);
        modulator_arm_right.cubeList.add(new ModelBox(modulator_arm_right, 115, 60, -0.625F, 1.5F, -2.25F, 1, 1, 3, 0.0F, false));
        modulator_arm_right.cubeList.add(new ModelBox(modulator_arm_right, 109, 60, -0.625F, 1.5F, 0.25F, 2, 1, 1, 0.0F, false));
        modulator_arm_right.cubeList.add(new ModelBox(modulator_arm_right, 115, 60, -0.625F, -0.5F, -2.25F, 1, 1, 3, 0.0F, false));
        modulator_arm_right.cubeList.add(new ModelBox(modulator_arm_right, 109, 60, -0.625F, -0.5F, 0.25F, 2, 1, 1, 0.0F, false));
        modulator_arm_right.cubeList.add(new ModelBox(modulator_arm_right, 115, 60, -0.625F, -2.5F, -2.25F, 1, 1, 3, 0.0F, false));
        modulator_arm_right.cubeList.add(new ModelBox(modulator_arm_right, 109, 60, -0.625F, -2.5F, 0.25F, 2, 1, 1, 0.0F, false));
    }


    public void render(float size) {
        gravitational_modulator.render(size);
    }

    public void setRotationAngle(ModelRenderer modelRenderer, float x, float y, float z) {
        modelRenderer.rotateAngleX = x;
        modelRenderer.rotateAngleY = y;
        modelRenderer.rotateAngleZ = z;
    }
}