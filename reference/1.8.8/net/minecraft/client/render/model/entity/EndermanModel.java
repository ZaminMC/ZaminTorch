package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;

public class EndermanModel extends HumanoidModel {
    public boolean carryingBlock;
    public boolean angry;

    public EndermanModel(float f) {
        super(0.0F, -14.0F, 64, 32);
        float fx = -14.0F;
        this.hat = new ModelPart(this, 0, 16);
        this.hat.addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, f - 0.5F);
        this.hat.setPos(0.0F, 0.0F + fx, 0.0F);
        this.body = new ModelPart(this, 32, 16);
        this.body.addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4, f);
        this.body.setPos(0.0F, 0.0F + fx, 0.0F);
        this.rightArm = new ModelPart(this, 56, 0);
        this.rightArm.addBox(-1.0F, -2.0F, -1.0F, 2, 30, 2, f);
        this.rightArm.setPos(-3.0F, 2.0F + fx, 0.0F);
        this.leftArm = new ModelPart(this, 56, 0);
        this.leftArm.flipped = true;
        this.leftArm.addBox(-1.0F, -2.0F, -1.0F, 2, 30, 2, f);
        this.leftArm.setPos(5.0F, 2.0F + fx, 0.0F);
        this.rightLeg = new ModelPart(this, 56, 0);
        this.rightLeg.addBox(-1.0F, 0.0F, -1.0F, 2, 30, 2, f);
        this.rightLeg.setPos(-2.0F, 12.0F + fx, 0.0F);
        this.leftLeg = new ModelPart(this, 56, 0);
        this.leftLeg.flipped = true;
        this.leftLeg.addBox(-1.0F, 0.0F, -1.0F, 2, 30, 2, f);
        this.leftLeg.setPos(2.0F, 12.0F + fx, 0.0F);
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        super.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        this.head.visible = true;
        float f = -14.0F;
        this.body.rotationX = 0.0F;
        this.body.y = f;
        this.body.z = -0.0F;
        this.rightLeg.rotationX -= 0.0F;
        this.leftLeg.rotationX -= 0.0F;
        this.rightArm.rotationX = (float)(this.rightArm.rotationX * 0.5);
        this.leftArm.rotationX = (float)(this.leftArm.rotationX * 0.5);
        this.rightLeg.rotationX = (float)(this.rightLeg.rotationX * 0.5);
        this.leftLeg.rotationX = (float)(this.leftLeg.rotationX * 0.5);
        float f1 = 0.4F;
        if (this.rightArm.rotationX > f1) {
            this.rightArm.rotationX = f1;
        }

        if (this.leftArm.rotationX > f1) {
            this.leftArm.rotationX = f1;
        }

        if (this.rightArm.rotationX < -f1) {
            this.rightArm.rotationX = -f1;
        }

        if (this.leftArm.rotationX < -f1) {
            this.leftArm.rotationX = -f1;
        }

        if (this.rightLeg.rotationX > f1) {
            this.rightLeg.rotationX = f1;
        }

        if (this.leftLeg.rotationX > f1) {
            this.leftLeg.rotationX = f1;
        }

        if (this.rightLeg.rotationX < -f1) {
            this.rightLeg.rotationX = -f1;
        }

        if (this.leftLeg.rotationX < -f1) {
            this.leftLeg.rotationX = -f1;
        }

        if (this.carryingBlock) {
            this.rightArm.rotationX = -0.5F;
            this.leftArm.rotationX = -0.5F;
            this.rightArm.rotationZ = 0.05F;
            this.leftArm.rotationZ = -0.05F;
        }

        this.rightArm.z = 0.0F;
        this.leftArm.z = 0.0F;
        this.rightLeg.z = 0.0F;
        this.leftLeg.z = 0.0F;
        this.rightLeg.y = 9.0F + f;
        this.leftLeg.y = 9.0F + f;
        this.head.z = -0.0F;
        this.head.y = f + 1.0F;
        this.hat.x = this.head.x;
        this.hat.y = this.head.y;
        this.hat.z = this.head.z;
        this.hat.rotationX = this.head.rotationX;
        this.hat.rotationY = this.head.rotationY;
        this.hat.rotationZ = this.head.rotationZ;
        if (this.angry) {
            float f2 = 1.0F;
            this.head.y -= f2 * 5.0F;
        }
    }
}
