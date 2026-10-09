package net.minecraft.client.render.model.entity;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public class ZombieModel extends HumanoidModel {
    public ZombieModel() {
        this(0.0F, false);
    }

    protected ZombieModel(float f, float g, int i, int j) {
        super(f, g, i, j);
    }

    public ZombieModel(float reduction, boolean pivot) {
        super(reduction, 0.0F, 64, pivot ? 32 : 64);
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
