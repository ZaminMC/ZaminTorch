package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.monster.SkeletonEntity;

public class SkeletonModel extends ZombieModel {
    public SkeletonModel() {
        this(0.0F, false);
    }

    public SkeletonModel(float reduction, boolean stray) {
        super(reduction, 0.0F, 64, 32);
        if (!stray) {
            this.rightArm = new ModelPart(this, 40, 16);
            this.rightArm.addBox(-1.0F, -2.0F, -1.0F, 2, 12, 2, reduction);
            this.rightArm.setPos(-5.0F, 2.0F, 0.0F);
            this.leftArm = new ModelPart(this, 40, 16);
            this.leftArm.flipped = true;
            this.leftArm.addBox(-1.0F, -2.0F, -1.0F, 2, 12, 2, reduction);
            this.leftArm.setPos(5.0F, 2.0F, 0.0F);
            this.rightLeg = new ModelPart(this, 0, 16);
            this.rightLeg.addBox(-1.0F, 0.0F, -1.0F, 2, 12, 2, reduction);
            this.rightLeg.setPos(-2.0F, 12.0F, 0.0F);
            this.leftLeg = new ModelPart(this, 0, 16);
            this.leftLeg.flipped = true;
            this.leftLeg.addBox(-1.0F, 0.0F, -1.0F, 2, 12, 2, reduction);
            this.leftLeg.setPos(2.0F, 12.0F, 0.0F);
        }
    }

    @Override
    public void prepare(LivingEntity mob, float walkAnimationProgress, float walkAnimationSpeed, float tickDelta) {
        this.aimingBow = ((SkeletonEntity)mob).getType() == 1;
        super.prepare(mob, walkAnimationProgress, walkAnimationSpeed, tickDelta);
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        super.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
    }
}
