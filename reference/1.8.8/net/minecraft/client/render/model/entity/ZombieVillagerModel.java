package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public class ZombieVillagerModel extends HumanoidModel {
    public ZombieVillagerModel() {
        this(0.0F, 0.0F, false);
    }

    public ZombieVillagerModel(float reduction, float rotationAngle, boolean texture) {
        super(reduction, 0.0F, 64, texture ? 32 : 64);
        if (texture) {
            this.head = new ModelPart(this, 0, 0);
            this.head.addBox(-4.0F, -10.0F, -4.0F, 8, 8, 8, reduction);
            this.head.setPos(0.0F, 0.0F + rotationAngle, 0.0F);
        } else {
            this.head = new ModelPart(this);
            this.head.setPos(0.0F, 0.0F + rotationAngle, 0.0F);
            this.head.setTextureCoords(0, 32).addBox(-4.0F, -10.0F, -4.0F, 8, 10, 8, reduction);
            this.head.setTextureCoords(24, 32).addBox(-1.0F, -3.0F, -6.0F, 2, 4, 2, reduction);
        }
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        super.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        float f = MathHelper.sin(this.attackAnimationProgress * (float) Math.PI);
        float f1 = MathHelper.sin((1.0F - (1.0F - this.attackAnimationProgress) * (1.0F - this.attackAnimationProgress)) * (float) Math.PI);
        this.rightArm.rotationZ = 0.0F;
        this.leftArm.rotationZ = 0.0F;
        this.rightArm.rotationY = -(0.1F - f * 0.6F);
        this.leftArm.rotationY = 0.1F - f * 0.6F;
        this.rightArm.rotationX = (float) (-Math.PI / 2);
        this.leftArm.rotationX = (float) (-Math.PI / 2);
        this.rightArm.rotationX -= f * 1.2F - f1 * 0.4F;
        this.leftArm.rotationX -= f * 1.2F - f1 * 0.4F;
        this.rightArm.rotationZ = this.rightArm.rotationZ + (MathHelper.cos(bob * 0.09F) * 0.05F + 0.05F);
        this.leftArm.rotationZ = this.leftArm.rotationZ - (MathHelper.cos(bob * 0.09F) * 0.05F + 0.05F);
        this.rightArm.rotationX = this.rightArm.rotationX + MathHelper.sin(bob * 0.067F) * 0.05F;
        this.leftArm.rotationX = this.leftArm.rotationX - MathHelper.sin(bob * 0.067F) * 0.05F;
    }
}
