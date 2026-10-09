package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.passive.animal.SheepEntity;

public class SheepFurModel extends QuadrupedModel {
    private float headAngle;

    public SheepFurModel() {
        super(12, 0.0F);
        this.head = new ModelPart(this, 0, 0);
        this.head.addBox(-3.0F, -4.0F, -4.0F, 6, 6, 6, 0.6F);
        this.head.setPos(0.0F, 6.0F, -8.0F);
        this.body = new ModelPart(this, 28, 8);
        this.body.addBox(-4.0F, -10.0F, -7.0F, 8, 16, 6, 1.75F);
        this.body.setPos(0.0F, 5.0F, 2.0F);
        float f = 0.5F;
        this.backRightLeg = new ModelPart(this, 0, 16);
        this.backRightLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 6, 4, f);
        this.backRightLeg.setPos(-3.0F, 12.0F, 7.0F);
        this.backLeftLeg = new ModelPart(this, 0, 16);
        this.backLeftLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 6, 4, f);
        this.backLeftLeg.setPos(3.0F, 12.0F, 7.0F);
        this.frontRightLeg = new ModelPart(this, 0, 16);
        this.frontRightLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 6, 4, f);
        this.frontRightLeg.setPos(-3.0F, 12.0F, -5.0F);
        this.frontLeftLeg = new ModelPart(this, 0, 16);
        this.frontLeftLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 6, 4, f);
        this.frontLeftLeg.setPos(3.0F, 12.0F, -5.0F);
    }

    @Override
    public void prepare(LivingEntity mob, float walkAnimationProgress, float walkAnimationSpeed, float tickDelta) {
        super.prepare(mob, walkAnimationProgress, walkAnimationSpeed, tickDelta);
        this.head.y = 6.0F + ((SheepEntity)mob).getNeckAngle(tickDelta) * 9.0F;
        this.headAngle = ((SheepEntity)mob).getHeadAngle(tickDelta);
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        super.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        this.head.rotationX = this.headAngle;
    }
}
