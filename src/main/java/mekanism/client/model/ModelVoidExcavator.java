package mekanism.client.model;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
    public class ModelVoidExcavator extends ModelBase {

    ModelRenderer plate3;
    ModelRenderer baseBack;
    ModelRenderer motor;
    ModelRenderer port;
    ModelRenderer pole4;
    ModelRenderer shaft2;
    ModelRenderer shaft1;
    ModelRenderer arm3;
    ModelRenderer plate2;
    ModelRenderer arm2;
    ModelRenderer arm1;
    ModelRenderer top;
    ModelRenderer frameBack5;
    ModelRenderer pole3;
    ModelRenderer frameRight5;
    ModelRenderer baseRight;
    ModelRenderer baseFront;
    ModelRenderer baseLeft;
    ModelRenderer frameRight3;
    ModelRenderer pole1;
    ModelRenderer frameRight4;
    ModelRenderer frameRight1;
    ModelRenderer frameRight2;
    ModelRenderer frameLeft5;
    ModelRenderer frameLeft4;
    ModelRenderer frameBack3;
    ModelRenderer frameLeft2;
    ModelRenderer frameLeft1;
    ModelRenderer pole2;
    ModelRenderer frameBack1;
    ModelRenderer frameBack2;
    ModelRenderer frameBack4;
    ModelRenderer frameLeft3;
    ModelRenderer conduit;
    ModelRenderer plate1;
    ModelRenderer rivet10;
    ModelRenderer rivet5;
    ModelRenderer rivet1;
    ModelRenderer rivet6;
    ModelRenderer rivet2;
    ModelRenderer rivet7;
    ModelRenderer rivet3;
    ModelRenderer rivet8;
    ModelRenderer rivet4;
    ModelRenderer rivet9;

    public ModelVoidExcavator() {
        textureWidth = 128;
        textureHeight = 64;

        plate3 = new ModelRenderer(this, 36, 42);
        plate3.addBox(0F, 0F, 0F, 8, 2, 8);
        plate3.setRotationPoint(-4F, 22F, -4F);
        plate3.setTextureSize(128, 64);
        plate3.mirror = true;
        setRotation(plate3, 0F, 0F, 0F);
        baseBack = new ModelRenderer(this, 0, 26);
        baseBack.addBox(0F, 0F, 0F, 16, 5, 3);
        baseBack.setRotationPoint(-8F, 19F, 5F);
        baseBack.setTextureSize(128, 64);
        baseBack.mirror = true;
        setRotation(baseBack, 0F, 0F, 0F);
        motor = new ModelRenderer(this, 76, 13);
        motor.addBox(0F, 0F, 0F, 6, 4, 10);
        motor.setRotationPoint(-3F, -5F, -3F);
        motor.setTextureSize(128, 64);
        motor.mirror = true;
        setRotation(motor, 0F, 0F, 0F);
        port = new ModelRenderer(this, 38, 33);
        port.addBox(0F, 0F, 0F, 8, 8, 1);
        port.setRotationPoint(-4F, 12F, 7.01F);
        port.setTextureSize(128, 64);
        port.mirror = true;
        setRotation(port, 0F, 0F, 0F);
        pole4 = new ModelRenderer(this, 0, 34);
        pole4.addBox(0F, 0F, 0F, 1, 25, 1);
        pole4.setRotationPoint(6.5F, -6F, 6.5F);
        pole4.setTextureSize(128, 64);
        pole4.mirror = true;
        setRotation(pole4, 0F, 0F, 0F);
        shaft2 = new ModelRenderer(this, 16, 34);
        shaft2.addBox(0F, 0F, 0F, 3, 11, 3);
        shaft2.setRotationPoint(-1.5F, -5F, -1.5F);
        shaft2.setTextureSize(128, 64);
        shaft2.mirror = true;
        setRotation(shaft2, 0F, 0F, 0F);
        shaft1 = new ModelRenderer(this, 8, 34);
        shaft1.addBox(0F, 0F, 0F, 2, 15, 2);
        shaft1.setRotationPoint(-1F, 6F, -1F);
        shaft1.setTextureSize(128, 64);
        shaft1.mirror = true;
        setRotation(shaft1, 0F, 0F, 0F);
        arm3 = new ModelRenderer(this, 0, 6);
        arm3.addBox(0F, 0F, 0F, 2, 2, 4);
        arm3.setRotationPoint(-1F, 7F, 3F);
        arm3.setTextureSize(128, 64);
        arm3.mirror = true;
        setRotation(arm3, -0.3665191F, 0F, 0F);
        plate2 = new ModelRenderer(this, 48, 0);
        plate2.addBox(0F, 0F, 0F, 4, 2, 4);
        plate2.setRotationPoint(-2F, 21F, -2F);
        plate2.setTextureSize(128, 64);
        plate2.mirror = true;
        setRotation(plate2, 0F, 0F, 0F);
        arm2 = new ModelRenderer(this, 48, 6);
        arm2.addBox(0F, 0F, 0F, 4, 2, 4);
        arm2.setRotationPoint(-2F, 7F, -2F);
        arm2.setTextureSize(128, 64);
        arm2.mirror = true;
        setRotation(arm2, 0F, 0F, 0F);
        arm1 = new ModelRenderer(this, 56, 33);
        arm1.addBox(0F, 0F, 0F, 3, 2, 4);
        arm1.setRotationPoint(-1.5F, 7F, 2F);
        arm1.setTextureSize(128, 64);
        arm1.mirror = true;
        setRotation(arm1, 0F, 0F, 0F);
        top = new ModelRenderer(this, 0, 0);
        top.addBox(0F, 0F, 0F, 16, 2, 16);
        top.setRotationPoint(-8F, -8F, -8F);
        top.setTextureSize(128, 64);
        top.mirror = true;
        setRotation(top, 0F, 0F, 0F);
        frameBack5 = new ModelRenderer(this, 4, 34);
        frameBack5.addBox(-1F, 0F, 0F, 1, 19, 1);
        frameBack5.setRotationPoint(7.5F, 7F, 6.49F);
        frameBack5.setTextureSize(128, 64);
        frameBack5.mirror = true;
        setRotation(frameBack5, 0F, 0F, 0.837758F);
        pole3 = new ModelRenderer(this, 0, 34);
        pole3.addBox(0F, 0F, 0F, 1, 25, 1);
        pole3.setRotationPoint(6.5F, -6F, -7.5F);
        pole3.setTextureSize(128, 64);
        pole3.mirror = true;
        setRotation(pole3, 0F, 0F, 0F);
        frameRight5 = new ModelRenderer(this, 4, 34);
        frameRight5.addBox(0F, 0F, 0F, 1, 19, 1);
        frameRight5.setRotationPoint(6.485F, 7F, -7.5F);
        frameRight5.setTextureSize(128, 64);
        frameRight5.mirror = true;
        setRotation(frameRight5, 0.837758F, 0F, 0F);
        baseRight = new ModelRenderer(this, 38, 18);
        baseRight.mirror = true;
        baseRight.addBox(0F, 0F, 0F, 3, 5, 10);
        baseRight.setRotationPoint(5F, 19F, -5F);
        baseRight.setTextureSize(128, 64);
        setRotation(baseRight, 0F, 0F, 0F);
        baseFront = new ModelRenderer(this, 0, 18);
        baseFront.addBox(0F, 0F, 0F, 16, 5, 3);
        baseFront.setRotationPoint(-8F, 19F, -8F);
        baseFront.setTextureSize(128, 64);
        baseFront.mirror = true;
        setRotation(baseFront, 0F, 0F, 0F);
        baseLeft = new ModelRenderer(this, 38, 18);
        baseLeft.addBox(0F, 0F, 0F, 3, 5, 10);
        baseLeft.setRotationPoint(-8F, 19F, -5F);
        baseLeft.setTextureSize(128, 64);
        baseLeft.mirror = true;
        setRotation(baseLeft, 0F, 0F, 0F);
        frameRight3 = new ModelRenderer(this, 64, 27);
        frameRight3.addBox(0F, 0F, 0F, 1, 1, 13);
        frameRight3.setRotationPoint(6.5F, 6F, -6.5F);
        frameRight3.setTextureSize(128, 64);
        frameRight3.mirror = true;
        setRotation(frameRight3, 0F, 0F, 0F);
        pole1 = new ModelRenderer(this, 0, 34);
        pole1.addBox(0F, 0F, 0F, 1, 25, 1);
        pole1.setRotationPoint(-7.5F, -6F, -7.5F);
        pole1.setTextureSize(128, 64);
        pole1.mirror = true;
        setRotation(pole1, 0F, 0F, 0F);
        frameRight4 = new ModelRenderer(this, 4, 34);
        frameRight4.addBox(0F, 0F, -1F, 1, 19, 1);
        frameRight4.setRotationPoint(6.49F, 7F, 7.5F);
        frameRight4.setTextureSize(128, 64);
        frameRight4.mirror = true;
        setRotation(frameRight4, -0.837758F, 0F, 0F);
        frameRight1 = new ModelRenderer(this, 4, 34);
        frameRight1.addBox(0F, 0F, 0F, 1, 19, 1);
        frameRight1.setRotationPoint(6.485F, -6F, -7.5F);
        frameRight1.setTextureSize(128, 64);
        frameRight1.mirror = true;
        setRotation(frameRight1, 0.837758F, 0F, 0F);
        frameRight2 = new ModelRenderer(this, 4, 34);
        frameRight2.addBox(0F, 0F, -1F, 1, 19, 1);
        frameRight2.setRotationPoint(6.49F, -6F, 7.5F);
        frameRight2.setTextureSize(128, 64);
        frameRight2.mirror = true;
        setRotation(frameRight2, -0.837758F, 0F, 0F);
        frameLeft5 = new ModelRenderer(this, 4, 34);
        frameLeft5.addBox(0F, 0F, 0F, 1, 19, 1);
        frameLeft5.setRotationPoint(-7.485F, 7F, -7.5F);
        frameLeft5.setTextureSize(128, 64);
        frameLeft5.mirror = true;
        setRotation(frameLeft5, 0.837758F, 0F, 0F);
        frameLeft4 = new ModelRenderer(this, 4, 34);
        frameLeft4.addBox(0F, 0F, -1F, 1, 19, 1);
        frameLeft4.setRotationPoint(-7.49F, 7F, 7.5F);
        frameLeft4.setTextureSize(128, 64);
        frameLeft4.mirror = true;
        setRotation(frameLeft4, -0.837758F, 0F, 0F);
        frameBack3 = new ModelRenderer(this, 36, 52);
        frameBack3.addBox(0F, 0F, 0F, 13, 1, 1);
        frameBack3.setRotationPoint(-6.5F, 6F, 6.5F);
        frameBack3.setTextureSize(128, 64);
        frameBack3.mirror = true;
        setRotation(frameBack3, 0F, 0F, 0F);
        frameLeft2 = new ModelRenderer(this, 4, 34);
        frameLeft2.addBox(0F, 0F, 0F, 1, 19, 1);
        frameLeft2.setRotationPoint(-7.485F, -6F, -7.5F);
        frameLeft2.setTextureSize(128, 64);
        frameLeft2.mirror = true;
        setRotation(frameLeft2, 0.837758F, 0F, 0F);
        frameLeft1 = new ModelRenderer(this, 4, 34);
        frameLeft1.addBox(0F, 0F, -1F, 1, 19, 1);
        frameLeft1.setRotationPoint(-7.49F, -6F, 7.5F);
        frameLeft1.setTextureSize(128, 64);
        frameLeft1.mirror = true;
        setRotation(frameLeft1, -0.837758F, 0F, 0F);
        pole2 = new ModelRenderer(this, 0, 34);
        pole2.addBox(0F, 0F, 0F, 1, 25, 1);
        pole2.setRotationPoint(-7.5F, -6F, 6.5F);
        pole2.setTextureSize(128, 64);
        pole2.mirror = true;
        setRotation(pole2, 0F, 0F, 0F);
        frameBack1 = new ModelRenderer(this, 4, 34);
        frameBack1.addBox(-1F, 0F, 0F, 1, 19, 1);
        frameBack1.setRotationPoint(7.5F, -6F, 6.49F);
        frameBack1.setTextureSize(128, 64);
        frameBack1.mirror = true;
        setRotation(frameBack1, 0F, 0F, 0.837758F);
        frameBack2 = new ModelRenderer(this, 4, 34);
        frameBack2.addBox(0F, 0F, 0F, 1, 19, 1);
        frameBack2.setRotationPoint(-7.5F, -6F, 6.49F);
        frameBack2.setTextureSize(128, 64);
        frameBack2.mirror = true;
        setRotation(frameBack2, 0F, 0F, -0.837758F);
        frameBack4 = new ModelRenderer(this, 4, 34);
        frameBack4.addBox(0F, 0F, 0F, 1, 19, 1);
        frameBack4.setRotationPoint(-7.5F, 7F, 6.49F);
        frameBack4.setTextureSize(128, 64);
        frameBack4.mirror = true;
        setRotation(frameBack4, 0F, 0F, -0.837758F);
        frameLeft3 = new ModelRenderer(this, 64, 27);
        frameLeft3.addBox(0F, 0F, 0F, 1, 1, 13);
        frameLeft3.setRotationPoint(-7.5F, 6F, -6.5F);
        frameLeft3.setTextureSize(128, 64);
        frameLeft3.mirror = true;
        setRotation(frameLeft3, 0F, 0F, 0F);
        conduit = new ModelRenderer(this, 64, 0);
        conduit.addBox(0F, 0F, 0F, 4, 25, 2);
        conduit.setRotationPoint(-2F, -6F, 6F);
        conduit.setTextureSize(128, 64);
        conduit.mirror = true;
        setRotation(conduit, 0F, 0F, 0F);
        plate1 = new ModelRenderer(this, 76, 0);
        plate1.addBox(0F, 0F, 0F, 10, 1, 12);
        plate1.setRotationPoint(-5F, -6F, -5F);
        plate1.setTextureSize(128, 64);
        plate1.mirror = true;
        setRotation(plate1, 0F, 0F, 0F);
        rivet10 = new ModelRenderer(this, 0, 0);
        rivet10.addBox(0F, 0F, 0F, 1, 1, 1);
        rivet10.setRotationPoint(3.5F, -5.5F, 3.5F);
        rivet10.setTextureSize(128, 64);
        rivet10.mirror = true;
        setRotation(rivet10, 0F, 0F, 0F);
        rivet5 = new ModelRenderer(this, 0, 0);
        rivet5.addBox(0F, 0F, 0F, 1, 1, 1);
        rivet5.setRotationPoint(-4.5F, -5.5F, 3.5F);
        rivet5.setTextureSize(128, 64);
        rivet5.mirror = true;
        setRotation(rivet5, 0F, 0F, 0F);
        rivet1 = new ModelRenderer(this, 0, 0);
        rivet1.addBox(0F, 0F, 0F, 1, 1, 1);
        rivet1.setRotationPoint(-4.5F, -5.5F, -4.5F);
        rivet1.setTextureSize(128, 64);
        rivet1.mirror = true;
        setRotation(rivet1, 0F, 0F, 0F);
        rivet6 = new ModelRenderer(this, 0, 0);
        rivet6.addBox(0F, 0F, 0F, 1, 1, 1);
        rivet6.setRotationPoint(3.5F, -5.5F, -4.5F);
        rivet6.setTextureSize(128, 64);
        rivet6.mirror = true;
        setRotation(rivet6, 0F, 0F, 0F);
        rivet2 = new ModelRenderer(this, 0, 0);
        rivet2.addBox(0F, 0F, 0F, 1, 1, 1);
        rivet2.setRotationPoint(-4.5F, -5.5F, -2.5F);
        rivet2.setTextureSize(128, 64);
        rivet2.mirror = true;
        setRotation(rivet2, 0F, 0F, 0F);
        rivet7 = new ModelRenderer(this, 0, 0);
        rivet7.addBox(0F, 0F, 0F, 1, 1, 1);
        rivet7.setRotationPoint(3.5F, -5.5F, -2.5F);
        rivet7.setTextureSize(128, 64);
        rivet7.mirror = true;
        setRotation(rivet7, 0F, 0F, 0F);
        rivet3 = new ModelRenderer(this, 0, 0);
        rivet3.addBox(0F, 0F, 0F, 1, 1, 1);
        rivet3.setRotationPoint(-4.5F, -5.5F, -0.5F);
        rivet3.setTextureSize(128, 64);
        rivet3.mirror = true;
        setRotation(rivet3, 0F, 0F, 0F);
        rivet8 = new ModelRenderer(this, 0, 0);
        rivet8.addBox(0F, 0F, 0F, 1, 1, 1);
        rivet8.setRotationPoint(3.5F, -5.5F, -0.5F);
        rivet8.setTextureSize(128, 64);
        rivet8.mirror = true;
        setRotation(rivet8, 0F, 0F, 0F);
        rivet4 = new ModelRenderer(this, 0, 0);
        rivet4.addBox(0F, 0F, 0F, 1, 1, 1);
        rivet4.setRotationPoint(-4.5F, -5.5F, 1.5F);
        rivet4.setTextureSize(128, 64);
        rivet4.mirror = true;
        setRotation(rivet4, 0F, 0F, 0F);
        rivet9 = new ModelRenderer(this, 0, 0);
        rivet9.addBox(0F, 0F, 0F, 1, 1, 1);
        rivet9.setRotationPoint(3.5F, -5.5F, 1.5F);
        rivet9.setTextureSize(128, 64);
        rivet9.mirror = true;
        setRotation(rivet9, 0F, 0F, 0F);
    }

    public void render(float size) {
        plate3.render(size);
        baseBack.render(size);
        motor.render(size);
        port.render(size);
        pole4.render(size);
        shaft2.render(size);
        shaft1.render(size);
        arm3.render(size);
        plate2.render(size);
        arm2.render(size);
        arm1.render(size);
        top.render(size);
        frameBack5.render(size);
        pole3.render(size);
        frameRight5.render(size);
        baseRight.render(size);
        baseFront.render(size);
        baseLeft.render(size);
        frameRight3.render(size);
        pole1.render(size);
        frameRight4.render(size);
        frameRight1.render(size);
        frameRight2.render(size);
        frameLeft5.render(size);
        frameLeft4.render(size);
        frameBack3.render(size);
        frameLeft2.render(size);
        frameLeft1.render(size);
        pole2.render(size);
        frameBack1.render(size);
        frameBack2.render(size);
        frameBack4.render(size);
        frameLeft3.render(size);
        conduit.render(size);
        plate1.render(size);
        rivet10.render(size);
        rivet5.render(size);
        rivet1.render(size);
        rivet6.render(size);
        rivet2.render(size);
        rivet7.render(size);
        rivet3.render(size);
        rivet8.render(size);
        rivet4.render(size);
        rivet9.render(size);
    }

    public void renderWithPiston(float piston, float size) {
        shaft1.rotationPointY = 6 - (piston * 12);
        plate2.rotationPointY = 21 - (piston * 12);
        plate3.rotationPointY = 22 - (piston * 12);

        render(size);
    }

    private void setRotation(ModelRenderer model, float x, float y, float z) {
        model.rotateAngleX = x;
        model.rotateAngleY = y;
        model.rotateAngleZ = z;
    }
}
