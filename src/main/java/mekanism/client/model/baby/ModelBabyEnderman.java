package mekanism.client.model.baby;

import net.minecraft.client.model.ModelEnderman;
import net.minecraft.entity.Entity;

public class ModelBabyEnderman extends ModelEnderman {

    public ModelBabyEnderman() {
        super(0);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scaleFactor, Entity entityIn) {
        super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor, entityIn);
        bipedHead.rotationPointY += 5.0F;
        if (isAttacking) {
            //Shift the head when angry to only the third the distance it goes up when it is an adult
            bipedHead.rotationPointY += 1.67F;
        }
    }
}
