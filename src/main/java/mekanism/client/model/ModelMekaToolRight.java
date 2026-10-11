package mekanism.client.model;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;

public class ModelMekaToolRight extends ModelBase {
    ModelRenderer mekatool_right;
    ModelRenderer mekatool_right_arm_barrel4_r1;
    ModelRenderer mekatool_right_arm_barrel3_r1;
    ModelRenderer mekatool_right_arm_barrel_sight_led_r1;
    ModelRenderer mekatool_right_arm_barrel_scope_r1;
    ModelRenderer mekatool_right_arm_barrel_heatsink_r1;
    ModelRenderer mekatool_right_arm_barrel2_r1;
    ModelRenderer mekatool_right_arm_cartridge2_r1;
    ModelRenderer mekatool_right_arm_cartridge1_r1;
    ModelRenderer override_mekatool_chest_right_arm_exo_brace4_r1;

    public ModelMekaToolRight() {
        textureWidth = 64;
        textureHeight = 64;

        mekatool_right = new ModelRenderer(this);
        mekatool_right.setRotationPoint(-7.9F, 2.5F, 0.0F);
        mekatool_right.cubeList.add(new ModelBox(mekatool_right, 0, 40, -1.95F, 9.0F, 0.0F, 4, 2, 6, 0.0F, true));
        mekatool_right.cubeList.add(new ModelBox(mekatool_right, 44, 24, -1.95F, 7.0F, 2.0F, 4, 2, 4, 0.0F, true));
        mekatool_right.cubeList.add(new ModelBox(mekatool_right, 4, 44, -1.95F, 5.0F, 2.0F, 4, 2, 2, 0.0F, true));
        mekatool_right.cubeList.add(new ModelBox(mekatool_right, 46, 26, -1.95F, 3.0F, 2.0F, 4, 2, 2, 0.0F, true));
        mekatool_right.cubeList.add(new ModelBox(mekatool_right, 0, 0, -0.7602F, 1.01F, -6.0F, 6, 8, 8, 0.0F, true));
        mekatool_right.cubeList.add(new ModelBox(mekatool_right, 24, 28, -2.7602F, -1.0F, -4.0F, 6, 8, 4, 0.0F, true));
        mekatool_right.cubeList.add(new ModelBox(mekatool_right, 0, 16, -0.7602F, 9.0F, -3.0F, 8, 4, 8, 0.0F, true));
        mekatool_right.cubeList.add(new ModelBox(mekatool_right, 8, 48, 2.25F, 13.0F, 0.0F, 2, 10, 2, 0.0F, true));
        mekatool_right.cubeList.add(new ModelBox(mekatool_right, 44, 30, 1.75F, 13.0F, -0.5F, 3, 4, 3, 0.0F, true));
        mekatool_right.cubeList.add(new ModelBox(mekatool_right, 48, 10, 1.75F, 19.0F, -0.5F, 3, 2, 3, 0.0F, true));

        mekatool_right_arm_barrel4_r1 = new ModelRenderer(this);
        mekatool_right_arm_barrel4_r1.setRotationPoint(-0.1042F, 19.8551F, 3.3947F);
        mekatool_right.addChild(mekatool_right_arm_barrel4_r1);
        setRotationAngle(mekatool_right_arm_barrel4_r1, -0.0873F, -0.0349F, -0.0873F);
        mekatool_right_arm_barrel4_r1.cubeList.add(new ModelBox(mekatool_right_arm_barrel4_r1, 34, 40, 0.0F, -7.0F, -1.0F, 2, 14, 2, 0.0F, true));

        mekatool_right_arm_barrel3_r1 = new ModelRenderer(this);
        mekatool_right_arm_barrel3_r1.setRotationPoint(5.5882F, 19.768F, 3.4296F);
        mekatool_right.addChild(mekatool_right_arm_barrel3_r1);
        setRotationAngle(mekatool_right_arm_barrel3_r1, -0.0873F, 0.0349F, 0.0873F);
        mekatool_right_arm_barrel3_r1.cubeList.add(new ModelBox(mekatool_right_arm_barrel3_r1, 34, 40, -1.0F, -7.0F, -1.0F, 2, 14, 2, 0.0F, true));

        mekatool_right_arm_barrel_sight_led_r1 = new ModelRenderer(this);
        mekatool_right_arm_barrel_sight_led_r1.setRotationPoint(15.425F, 16.0F, -3.75F);
        mekatool_right.addChild(mekatool_right_arm_barrel_sight_led_r1);
        setRotationAngle(mekatool_right_arm_barrel_sight_led_r1, 0.1745F, 0.0F, 0.0F);
        mekatool_right_arm_barrel_sight_led_r1.cubeList.add(new ModelBox(mekatool_right_arm_barrel_sight_led_r1, 28, 10, -12.825F, 4.18F, -0.225F, 1, 2, 1, 0.0F, true));
        mekatool_right_arm_barrel_sight_led_r1.cubeList.add(new ModelBox(mekatool_right_arm_barrel_sight_led_r1, 28, 10, -12.825F, 4.23F, -0.225F, 1, 2, 1, 0.0F, true));

        mekatool_right_arm_barrel_scope_r1 = new ModelRenderer(this);
        mekatool_right_arm_barrel_scope_r1.setRotationPoint(1.85F, 15.45F, -4.5F);
        mekatool_right.addChild(mekatool_right_arm_barrel_scope_r1);
        setRotationAngle(mekatool_right_arm_barrel_scope_r1, 0.1745F, 0.0F, 0.0F);
        mekatool_right_arm_barrel_scope_r1.cubeList.add(new ModelBox(mekatool_right_arm_barrel_scope_r1, 48, 15, -0.25F, 4.8685F, -0.5607F, 3, 2, 3, 0.0F, true));

        mekatool_right_arm_barrel_heatsink_r1 = new ModelRenderer(this);
        mekatool_right_arm_barrel_heatsink_r1.setRotationPoint(2.6F, 8.5F, -3.0F);
        mekatool_right.addChild(mekatool_right_arm_barrel_heatsink_r1);
        setRotationAngle(mekatool_right_arm_barrel_heatsink_r1, 0.2269F, 0.0F, 0.0F);
        mekatool_right_arm_barrel_heatsink_r1.cubeList.add(new ModelBox(mekatool_right_arm_barrel_heatsink_r1, 0, 48, -0.45F, 0.0F, -2.0F, 2, 12, 2, 0.0F, true));

        mekatool_right_arm_barrel2_r1 = new ModelRenderer(this);
        mekatool_right_arm_barrel2_r1.setRotationPoint(0.6898F, 11.9166F, -2.091F);
        mekatool_right.addChild(mekatool_right_arm_barrel2_r1);
        setRotationAngle(mekatool_right_arm_barrel2_r1, 0.0873F, 0.0F, 0.0F);
        mekatool_right_arm_barrel2_r1.cubeList.add(new ModelBox(mekatool_right_arm_barrel2_r1, 20, 40, 0.0602F, 1.0F, -1.0F, 5, 12, 2, 0.0F, true));

        mekatool_right_arm_cartridge2_r1 = new ModelRenderer(this);
        mekatool_right_arm_cartridge2_r1.setRotationPoint(-1.55F, 10.1038F, 4.1765F);
        mekatool_right.addChild(mekatool_right_arm_cartridge2_r1);
        setRotationAngle(mekatool_right_arm_cartridge2_r1, -0.6981F, 0.0F, 0.0F);
        mekatool_right_arm_cartridge2_r1.cubeList.add(new ModelBox(mekatool_right_arm_cartridge2_r1, 28, 0, 1.6F, -3.1038F, 3.8235F, 8, 6, 4, 0.0F, true));

        mekatool_right_arm_cartridge1_r1 = new ModelRenderer(this);
        mekatool_right_arm_cartridge1_r1.setRotationPoint(0.45F, 11.253F, 0.9395F);
        mekatool_right.addChild(mekatool_right_arm_cartridge1_r1);
        setRotationAngle(mekatool_right_arm_cartridge1_r1, -0.6981F, 0.0F, 0.0F);
        mekatool_right_arm_cartridge1_r1.cubeList.add(new ModelBox(mekatool_right_arm_cartridge1_r1, 0, 28, 1.6F, -5.1506F, -1.4401F, 4, 4, 8, 0.0F, true));

        override_mekatool_chest_right_arm_exo_brace4_r1 = new ModelRenderer(this);
        override_mekatool_chest_right_arm_exo_brace4_r1.setRotationPoint(2.57F, 8.3457F, 0.7085F);
        mekatool_right.addChild(override_mekatool_chest_right_arm_exo_brace4_r1);
        setRotationAngle(override_mekatool_chest_right_arm_exo_brace4_r1, -0.2618F, 0.0F, 0.0F);
        override_mekatool_chest_right_arm_exo_brace4_r1.cubeList.add(new ModelBox(override_mekatool_chest_right_arm_exo_brace4_r1, 32, 10, -2.54F, -9.3863F, 0.1727F, 4, 10, 4, 0.0F, true));

    }


    public void render(float size) {
        mekatool_right.render(size);
    }

    public void setRotationAngle(ModelRenderer modelRenderer, float x, float y, float z) {
        modelRenderer.rotateAngleX = x;
        modelRenderer.rotateAngleY = y;
        modelRenderer.rotateAngleZ = z;
    }
}
