package net.minecraft.client.render.model.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.living.ArmorStandEntity;

public class ArmorStandArmorModel extends HumanoidModel {
    public ArmorStandArmorModel() {
        this(0.0F);
    }

    public ArmorStandArmorModel(float f) {
        this(f, 64, 32);
    }

    protected ArmorStandArmorModel(float reduction, int textureWidth, int textureHeight) {
        super(reduction, 0.0F, textureWidth, textureHeight);
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        if (entity instanceof ArmorStandEntity) {
            ArmorStandEntity armorstandentity = (ArmorStandEntity)entity;
            this.head.rotationX = (float) (Math.PI / 180.0) * armorstandentity.getHeadRotation().getPitch();
            this.head.rotationY = (float) (Math.PI / 180.0) * armorstandentity.getHeadRotation().getYaw();
            this.head.rotationZ = (float) (Math.PI / 180.0) * armorstandentity.getHeadRotation().getRoll();
            this.head.setPos(0.0F, 1.0F, 0.0F);
            this.body.rotationX = (float) (Math.PI / 180.0) * armorstandentity.getBodyRotation().getPitch();
            this.body.rotationY = (float) (Math.PI / 180.0) * armorstandentity.getBodyRotation().getYaw();
            this.body.rotationZ = (float) (Math.PI / 180.0) * armorstandentity.getBodyRotation().getRoll();
            this.leftArm.rotationX = (float) (Math.PI / 180.0) * armorstandentity.getLeftArmRotation().getPitch();
            this.leftArm.rotationY = (float) (Math.PI / 180.0) * armorstandentity.getLeftArmRotation().getYaw();
            this.leftArm.rotationZ = (float) (Math.PI / 180.0) * armorstandentity.getLeftArmRotation().getRoll();
            this.rightArm.rotationX = (float) (Math.PI / 180.0) * armorstandentity.getRightArmRotation().getPitch();
            this.rightArm.rotationY = (float) (Math.PI / 180.0) * armorstandentity.getRightArmRotation().getYaw();
            this.rightArm.rotationZ = (float) (Math.PI / 180.0) * armorstandentity.getRightArmRotation().getRoll();
            this.leftLeg.rotationX = (float) (Math.PI / 180.0) * armorstandentity.getLeftLegRotation().getPitch();
            this.leftLeg.rotationY = (float) (Math.PI / 180.0) * armorstandentity.getLeftLegRotation().getYaw();
            this.leftLeg.rotationZ = (float) (Math.PI / 180.0) * armorstandentity.getLeftLegRotation().getRoll();
            this.leftLeg.setPos(1.9F, 11.0F, 0.0F);
            this.rightLeg.rotationX = (float) (Math.PI / 180.0) * armorstandentity.getRightLegRotation().getPitch();
            this.rightLeg.rotationY = (float) (Math.PI / 180.0) * armorstandentity.getRightLegRotation().getYaw();
            this.rightLeg.rotationZ = (float) (Math.PI / 180.0) * armorstandentity.getRightLegRotation().getRoll();
            this.rightLeg.setPos(-1.9F, 11.0F, 0.0F);
            copyRotation(this.head, this.hat);
        }
    }
}
