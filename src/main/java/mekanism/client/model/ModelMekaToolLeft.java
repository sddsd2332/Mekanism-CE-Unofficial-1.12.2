package mekanism.client.model;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;

public class ModelMekaToolLeft extends ModelBase {
    ModelRenderer mekatool_left;
    ModelRenderer mekatool_left_arm_barrel4_r1;
    ModelRenderer mekatool_left_arm_barrel3_r1;
    ModelRenderer mekatool_left_arm_barrel_sight_led_r1;
    ModelRenderer mekatool_left_arm_barrel_scope_r1;
    ModelRenderer mekatool_left_arm_barrel_heatsink_r1;
    ModelRenderer mekatool_left_arm_barrel2_r1;
    ModelRenderer mekatool_left_arm_cartridge2_r1;
    ModelRenderer mekatool_left_arm_cartridge1_r1;
    ModelRenderer override_mekatool_chest_left_arm_exo_brace4_r1;

    public ModelMekaToolLeft() {
        textureWidth = 64;
        textureHeight = 64;

        mekatool_left = new ModelRenderer(this);
        mekatool_left.setRotationPoint(7.9F, 2.5F, 0.0F);
        mekatool_left.cubeList.add(new ModelBox(mekatool_left, 0, 40, -2.05F, 9.0F, 0.0F, 4, 2, 6, 0.0F, false));
        mekatool_left.cubeList.add(new ModelBox(mekatool_left, 44, 24, -2.05F, 7.0F, 2.0F, 4, 2, 4, 0.0F, false));
        mekatool_left.cubeList.add(new ModelBox(mekatool_left, 4, 44, -2.05F, 5.0F, 2.0F, 4, 2, 2, 0.0F, false));
        mekatool_left.cubeList.add(new ModelBox(mekatool_left, 46, 26, -2.05F, 3.0F, 2.0F, 4, 2, 2, 0.0F, false));
        mekatool_left.cubeList.add(new ModelBox(mekatool_left, 0, 0, -5.2398F, 1.01F, -6.0F, 6, 8, 8, 0.0F, false));
        mekatool_left.cubeList.add(new ModelBox(mekatool_left, 24, 28, -3.2398F, -1.0F, -4.0F, 6, 8, 4, 0.0F, false));
        mekatool_left.cubeList.add(new ModelBox(mekatool_left, 0, 16, -7.2398F, 9.0F, -3.0F, 8, 4, 8, 0.0F, false));
        mekatool_left.cubeList.add(new ModelBox(mekatool_left, 8, 48, -4.25F, 13.0F, 0.0F, 2, 10, 2, 0.0F, false));
        mekatool_left.cubeList.add(new ModelBox(mekatool_left, 44, 30, -4.75F, 13.0F, -0.5F, 3, 4, 3, 0.0F, false));
        mekatool_left.cubeList.add(new ModelBox(mekatool_left, 48, 10, -4.75F, 19.0F, -0.5F, 3, 2, 3, 0.0F, false));

        mekatool_left_arm_barrel4_r1 = new ModelRenderer(this);
        mekatool_left_arm_barrel4_r1.setRotationPoint(0.1042F, 19.8551F, 3.3947F);
        mekatool_left.addChild(mekatool_left_arm_barrel4_r1);
        setRotationAngle(mekatool_left_arm_barrel4_r1, -0.0873F, 0.0349F, 0.0873F);
        mekatool_left_arm_barrel4_r1.cubeList.add(new ModelBox(mekatool_left_arm_barrel4_r1, 34, 40, -2.0F, -7.0F, -1.0F, 2, 14, 2, 0.0F, false));

        mekatool_left_arm_barrel3_r1 = new ModelRenderer(this);
        mekatool_left_arm_barrel3_r1.setRotationPoint(-5.5882F, 19.768F, 3.4296F);
        mekatool_left.addChild(mekatool_left_arm_barrel3_r1);
        setRotationAngle(mekatool_left_arm_barrel3_r1, -0.0873F, -0.0349F, -0.0873F);
        mekatool_left_arm_barrel3_r1.cubeList.add(new ModelBox(mekatool_left_arm_barrel3_r1, 34, 40, -1.0F, -7.0F, -1.0F, 2, 14, 2, 0.0F, false));

        mekatool_left_arm_barrel_sight_led_r1 = new ModelRenderer(this);
        mekatool_left_arm_barrel_sight_led_r1.setRotationPoint(-15.425F, 16.0F, -3.75F);
        mekatool_left.addChild(mekatool_left_arm_barrel_sight_led_r1);
        setRotationAngle(mekatool_left_arm_barrel_sight_led_r1, 0.1745F, 0.0F, 0.0F);
        mekatool_left_arm_barrel_sight_led_r1.cubeList.add(new ModelBox(mekatool_left_arm_barrel_sight_led_r1, 28, 10, 11.825F, 4.18F, -0.225F, 1, 2, 1, 0.0F, false));
        mekatool_left_arm_barrel_sight_led_r1.cubeList.add(new ModelBox(mekatool_left_arm_barrel_sight_led_r1, 28, 10, 11.825F, 4.23F, -0.225F, 1, 2, 1, 0.0F, false));

        mekatool_left_arm_barrel_scope_r1 = new ModelRenderer(this);
        mekatool_left_arm_barrel_scope_r1.setRotationPoint(-1.85F, 15.45F, -4.5F);
        mekatool_left.addChild(mekatool_left_arm_barrel_scope_r1);
        setRotationAngle(mekatool_left_arm_barrel_scope_r1, 0.1745F, 0.0F, 0.0F);
        mekatool_left_arm_barrel_scope_r1.cubeList.add(new ModelBox(mekatool_left_arm_barrel_scope_r1, 48, 15, -2.75F, 4.8685F, -0.5607F, 3, 2, 3, 0.0F, false));

        mekatool_left_arm_barrel_heatsink_r1 = new ModelRenderer(this);
        mekatool_left_arm_barrel_heatsink_r1.setRotationPoint(-2.6F, 8.5F, -3.0F);
        mekatool_left.addChild(mekatool_left_arm_barrel_heatsink_r1);
        setRotationAngle(mekatool_left_arm_barrel_heatsink_r1, 0.2269F, 0.0F, 0.0F);
        mekatool_left_arm_barrel_heatsink_r1.cubeList.add(new ModelBox(mekatool_left_arm_barrel_heatsink_r1, 0, 48, -1.55F, 0.0F, -2.0F, 2, 12, 2, 0.0F, false));

        mekatool_left_arm_barrel2_r1 = new ModelRenderer(this);
        mekatool_left_arm_barrel2_r1.setRotationPoint(-0.6898F, 11.9166F, -2.091F);
        mekatool_left.addChild(mekatool_left_arm_barrel2_r1);
        setRotationAngle(mekatool_left_arm_barrel2_r1, 0.0873F, 0.0F, 0.0F);
        mekatool_left_arm_barrel2_r1.cubeList.add(new ModelBox(mekatool_left_arm_barrel2_r1, 20, 40, -5.0602F, 1.0F, -1.0F, 5, 12, 2, 0.0F, false));

        mekatool_left_arm_cartridge2_r1 = new ModelRenderer(this);
        mekatool_left_arm_cartridge2_r1.setRotationPoint(1.55F, 10.1038F, 4.1765F);
        mekatool_left.addChild(mekatool_left_arm_cartridge2_r1);
        setRotationAngle(mekatool_left_arm_cartridge2_r1, -0.6981F, 0.0F, 0.0F);
        mekatool_left_arm_cartridge2_r1.cubeList.add(new ModelBox(mekatool_left_arm_cartridge2_r1, 28, 0, -9.6F, -3.1038F, 3.8235F, 8, 6, 4, 0.0F, false));

        mekatool_left_arm_cartridge1_r1 = new ModelRenderer(this);
        mekatool_left_arm_cartridge1_r1.setRotationPoint(-0.45F, 11.253F, 0.9395F);
        mekatool_left.addChild(mekatool_left_arm_cartridge1_r1);
        setRotationAngle(mekatool_left_arm_cartridge1_r1, -0.6981F, 0.0F, 0.0F);
        mekatool_left_arm_cartridge1_r1.cubeList.add(new ModelBox(mekatool_left_arm_cartridge1_r1, 0, 28, -5.6F, -5.1506F, -1.4401F, 4, 4, 8, 0.0F, false));

        override_mekatool_chest_left_arm_exo_brace4_r1 = new ModelRenderer(this);
        override_mekatool_chest_left_arm_exo_brace4_r1.setRotationPoint(-2.57F, 8.3457F, 0.7085F);
        mekatool_left.addChild(override_mekatool_chest_left_arm_exo_brace4_r1);
        setRotationAngle(override_mekatool_chest_left_arm_exo_brace4_r1, -0.2618F, 0.0F, 0.0F);
        override_mekatool_chest_left_arm_exo_brace4_r1.cubeList.add(new ModelBox(override_mekatool_chest_left_arm_exo_brace4_r1, 32, 10, -1.46F, -9.3863F, 0.1727F, 4, 10, 4, 0.0F, false));

    }


    public void render(float size) {
        mekatool_left.render(size);
    }

    public void setRotationAngle(ModelRenderer modelRenderer, float x, float y, float z) {
        modelRenderer.rotateAngleX = x;
        modelRenderer.rotateAngleY = y;
        modelRenderer.rotateAngleZ = z;
    }
}
