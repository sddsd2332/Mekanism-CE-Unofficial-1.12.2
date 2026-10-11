package mekanism.client.model.mekasuitarmour;


import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ModuleSolarHelmet extends ModelBase {

    public static final ModuleSolarHelmet solar = new ModuleSolarHelmet();

    public final ModelRenderer solar_helmet;
    ModelRenderer solar_helmet_head_visor_right_back_r2;
    ModelRenderer override_solar_helmet_helmet_head_visor_right_r2;
    ModelRenderer override_solar_helmet_helmet_head_center3_r2;
    ModelRenderer solar_helmet_head_center_front_r2;
    ModelRenderer override_solar_helmet_helmet_head_center1_r2;

    public ModuleSolarHelmet() {
        textureWidth = 128;
        textureHeight = 128;

        solar_helmet = new ModelRenderer(this);
        solar_helmet.setRotationPoint(0.0F, -2.5F, 1.0F);
        solar_helmet.cubeList.add(new ModelBox(solar_helmet, 89, 16, -0.995F, -5.0F, -4.0F, 2, 1, 5, 0.0F, false));
        solar_helmet.cubeList.add(new ModelBox(solar_helmet, 100, 9, 3.01F, -5.0F, -4.5F, 1, 1, 7, 0.0F, false));
        solar_helmet.cubeList.add(new ModelBox(solar_helmet, 100, 1, -4.0F, -5.0F, -4.5F, 1, 1, 7, 0.0F, false));
        solar_helmet.cubeList.add(new ModelBox(solar_helmet, 100, 9, -2.0F, -0.5F, 1.5F, 1, 1, 1, 0.0F, false));
        solar_helmet.cubeList.add(new ModelBox(solar_helmet, 100, 4, 1.0F, -0.5F, 1.5F, 1, 1, 1, 0.0F, false));
        solar_helmet.cubeList.add(new ModelBox(solar_helmet, 92, 3, 3.01F, -4.0F, 1.5F, 1, 3, 1, 0.0F, false));
        solar_helmet.cubeList.add(new ModelBox(solar_helmet, 89, 0, -4.0F, -4.0F, 1.5F, 1, 3, 1, 0.0F, false));
        solar_helmet.cubeList.add(new ModelBox(solar_helmet, 89, 8, -3.0F, -4.5F, -5.0F, 2, 1, 7, 0.0F, false));
        solar_helmet.cubeList.add(new ModelBox(solar_helmet, 89, 0, 1.0F, -4.5F, -5.0F, 2, 1, 7, 0.0F, false));

        solar_helmet_head_visor_right_back_r2 = new ModelRenderer(this);
        solar_helmet_head_visor_right_back_r2.setRotationPoint(-2.005F, -3.5168F, 2.5324F);
        solar_helmet.addChild(solar_helmet_head_visor_right_back_r2);
        setRotationAngle(solar_helmet_head_visor_right_back_r2, -0.6109F, 0.0F, 0.0F);
        solar_helmet_head_visor_right_back_r2.cubeList.add(new ModelBox(solar_helmet_head_visor_right_back_r2, 105, 17, -1.0F, -0.5F, -1.0F, 2, 1, 2, 0.0F, false));
        solar_helmet_head_visor_right_back_r2.cubeList.add(new ModelBox(solar_helmet_head_visor_right_back_r2, 109, 0, 3.01F, -0.5F, -1.0F, 2, 1, 2, 0.0F, false));

        override_solar_helmet_helmet_head_visor_right_r2 = new ModelRenderer(this);
        override_solar_helmet_helmet_head_visor_right_r2.setRotationPoint(-2.005F, -3.8355F, -5.2418F);
        solar_helmet.addChild(override_solar_helmet_helmet_head_visor_right_r2);
        setRotationAngle(override_solar_helmet_helmet_head_visor_right_r2, 0.4363F, 0.0F, 0.0F);
        override_solar_helmet_helmet_head_visor_right_r2.cubeList.add(new ModelBox(override_solar_helmet_helmet_head_visor_right_r2, 89, 8, -1.0F, -0.5F, -0.5F, 2, 1, 1, 0.0F, false));
        override_solar_helmet_helmet_head_visor_right_r2.cubeList.add(new ModelBox(override_solar_helmet_helmet_head_visor_right_r2, 89, 10, 3.01F, -0.5F, -0.5F, 2, 1, 1, 0.0F, false));

        override_solar_helmet_helmet_head_center3_r2 = new ModelRenderer(this);
        override_solar_helmet_helmet_head_center3_r2.setRotationPoint(0.0F, -3.8735F, 1.7371F);
        solar_helmet.addChild(override_solar_helmet_helmet_head_center3_r2);
        setRotationAngle(override_solar_helmet_helmet_head_center3_r2, -0.6109F, 0.0F, 0.0F);
        override_solar_helmet_helmet_head_center3_r2.cubeList.add(new ModelBox(override_solar_helmet_helmet_head_center3_r2, 98, 17, -1.0F, -0.5F, -1.25F, 2, 1, 3, 0.0F, false));

        solar_helmet_head_center_front_r2 = new ModelRenderer(this);
        solar_helmet_head_center_front_r2.setRotationPoint(0.0F, -3.873F, -4.8447F);
        solar_helmet.addChild(solar_helmet_head_center_front_r2);
        setRotationAngle(solar_helmet_head_center_front_r2, 0.8727F, 0.0F, 0.0F);
        solar_helmet_head_center_front_r2.cubeList.add(new ModelBox(solar_helmet_head_center_front_r2, 100, 0, -1.0F, -0.5F, -0.5F, 2, 1, 1, 0.0F, false));

        override_solar_helmet_helmet_head_center1_r2 = new ModelRenderer(this);
        override_solar_helmet_helmet_head_center1_r2.setRotationPoint(0.0F, -4.3355F, -4.2418F);
        solar_helmet.addChild(override_solar_helmet_helmet_head_center1_r2);
        setRotationAngle(override_solar_helmet_helmet_head_center1_r2, 0.4363F, 0.0F, 0.0F);
        override_solar_helmet_helmet_head_center1_r2.cubeList.add(new ModelBox(override_solar_helmet_helmet_head_center1_r2, 100, 2, -1.0F, -0.5F, -0.5F, 2, 1, 1, 0.0F, false));
    }


    public void render(float size) {
        solar_helmet.render(size);
    }

    public void setRotationAngle(ModelRenderer modelRenderer, float x, float y, float z) {
        modelRenderer.rotateAngleX = x;
        modelRenderer.rotateAngleY = y;
        modelRenderer.rotateAngleZ = z;
    }
}