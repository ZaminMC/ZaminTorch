package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.WolfEntity;
import net.minecraft.util.math.MathHelper;

public class WolfModel extends Model {
    public ModelPart head;
    public ModelPart body;
    public ModelPart backRightLeg;
    public ModelPart backLeftLeg;
    public ModelPart frontRightLeg;
    public ModelPart frontLeftLeg;
    ModelPart tail;
    ModelPart neck;

    public WolfModel() {
        float f = 0.0F;
        float f1 = 13.5F;
        this.head = new ModelPart(this, 0, 0);
        this.head.addBox(-3.0F, -3.0F, -2.0F, 6, 6, 4, f);
        this.head.setPos(-1.0F, f1, -7.0F);
        this.body = new ModelPart(this, 18, 14);
        this.body.addBox(-4.0F, -2.0F, -3.0F, 6, 9, 6, f);
        this.body.setPos(0.0F, 14.0F, 2.0F);
        this.neck = new ModelPart(this, 21, 0);
        this.neck.addBox(-4.0F, -3.0F, -3.0F, 8, 6, 7, f);
        this.neck.setPos(-1.0F, 14.0F, 2.0F);
        this.backRightLeg = new ModelPart(this, 0, 18);
        this.backRightLeg.addBox(-1.0F, 0.0F, -1.0F, 2, 8, 2, f);
        this.backRightLeg.setPos(-2.5F, 16.0F, 7.0F);
        this.backLeftLeg = new ModelPart(this, 0, 18);
        this.backLeftLeg.addBox(-1.0F, 0.0F, -1.0F, 2, 8, 2, f);
        this.backLeftLeg.setPos(0.5F, 16.0F, 7.0F);
        this.frontRightLeg = new ModelPart(this, 0, 18);
        this.frontRightLeg.addBox(-1.0F, 0.0F, -1.0F, 2, 8, 2, f);
        this.frontRightLeg.setPos(-2.5F, 16.0F, -4.0F);
        this.frontLeftLeg = new ModelPart(this, 0, 18);
        this.frontLeftLeg.addBox(-1.0F, 0.0F, -1.0F, 2, 8, 2, f);
        this.frontLeftLeg.setPos(0.5F, 16.0F, -4.0F);
        this.tail = new ModelPart(this, 9, 18);
        this.tail.addBox(-1.0F, 0.0F, -1.0F, 2, 8, 2, f);
        this.tail.setPos(-1.0F, 12.0F, 8.0F);
        this.head.setTextureCoords(16, 14).addBox(-3.0F, -5.0F, 0.0F, 2, 2, 1, f);
        this.head.setTextureCoords(16, 14).addBox(1.0F, -5.0F, 0.0F, 2, 2, 1, f);
        this.head.setTextureCoords(0, 10).addBox(-1.5F, 0.0F, -5.0F, 3, 3, 4, f);
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        super.render(entity, walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale);
        this.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        if (this.isBaby) {
            float f = 2.0F;
            GlStateManager.pushMatrix();
            GlStateManager.translatef(0.0F, 5.0F * scale, 2.0F * scale);
            this.head.renderForceTransform(scale);
            GlStateManager.popMatrix();
            GlStateManager.pushMatrix();
            GlStateManager.scalef(1.0F / f, 1.0F / f, 1.0F / f);
            GlStateManager.translatef(0.0F, 24.0F * scale, 0.0F);
            this.body.render(scale);
            this.backRightLeg.render(scale);
            this.backLeftLeg.render(scale);
            this.frontRightLeg.render(scale);
            this.frontLeftLeg.render(scale);
            this.tail.renderForceTransform(scale);
            this.neck.render(scale);
            GlStateManager.popMatrix();
        } else {
            this.head.renderForceTransform(scale);
            this.body.render(scale);
            this.backRightLeg.render(scale);
            this.backLeftLeg.render(scale);
            this.frontRightLeg.render(scale);
            this.frontLeftLeg.render(scale);
            this.tail.renderForceTransform(scale);
            this.neck.render(scale);
        }
    }

    @Override
    public void prepare(LivingEntity mob, float walkAnimationProgress, float walkAnimationSpeed, float tickDelta) {
        WolfEntity wolfentity = (WolfEntity)mob;
        if (wolfentity.isAngry()) {
            this.tail.rotationY = 0.0F;
        } else {
            this.tail.rotationY = MathHelper.cos(walkAnimationProgress * 0.6662F) * 1.4F * walkAnimationSpeed;
        }

        if (wolfentity.isSitting()) {
            this.neck.setPos(-1.0F, 16.0F, -3.0F);
            this.neck.rotationX = (float) (Math.PI * 2.0 / 5.0);
            this.neck.rotationY = 0.0F;
            this.body.setPos(0.0F, 18.0F, 0.0F);
            this.body.rotationX = (float) (Math.PI / 4);
            this.tail.setPos(-1.0F, 21.0F, 6.0F);
            this.backRightLeg.setPos(-2.5F, 22.0F, 2.0F);
            this.backRightLeg.rotationX = (float) (Math.PI * 3.0 / 2.0);
            this.backLeftLeg.setPos(0.5F, 22.0F, 2.0F);
            this.backLeftLeg.rotationX = (float) (Math.PI * 3.0 / 2.0);
            this.frontRightLeg.rotationX = 5.811947F;
            this.frontRightLeg.setPos(-2.49F, 17.0F, -4.0F);
            this.frontLeftLeg.rotationX = 5.811947F;
            this.frontLeftLeg.setPos(0.51F, 17.0F, -4.0F);
        } else {
            this.body.setPos(0.0F, 14.0F, 2.0F);
            this.body.rotationX = (float) (Math.PI / 2);
            this.neck.setPos(-1.0F, 14.0F, -3.0F);
            this.neck.rotationX = this.body.rotationX;
            this.tail.setPos(-1.0F, 12.0F, 8.0F);
            this.backRightLeg.setPos(-2.5F, 16.0F, 7.0F);
            this.backLeftLeg.setPos(0.5F, 16.0F, 7.0F);
            this.frontRightLeg.setPos(-2.5F, 16.0F, -4.0F);
            this.frontLeftLeg.setPos(0.5F, 16.0F, -4.0F);
            this.backRightLeg.rotationX = MathHelper.cos(walkAnimationProgress * 0.6662F) * 1.4F * walkAnimationSpeed;
            this.backLeftLeg.rotationX = MathHelper.cos(walkAnimationProgress * 0.6662F + (float) Math.PI) * 1.4F * walkAnimationSpeed;
            this.frontRightLeg.rotationX = MathHelper.cos(walkAnimationProgress * 0.6662F + (float) Math.PI) * 1.4F * walkAnimationSpeed;
            this.frontLeftLeg.rotationX = MathHelper.cos(walkAnimationProgress * 0.6662F) * 1.4F * walkAnimationSpeed;
        }

        this.head.rotationZ = wolfentity.getHeadRollAngle(tickDelta) + wolfentity.getShakeAngle(tickDelta, 0.0F);
        this.neck.rotationZ = wolfentity.getShakeAngle(tickDelta, -0.08F);
        this.body.rotationZ = wolfentity.getShakeAngle(tickDelta, -0.16F);
        this.tail.rotationZ = wolfentity.getShakeAngle(tickDelta, -0.2F);
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        super.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        this.head.rotationX = pitch / (180.0F / (float)Math.PI);
        this.head.rotationY = yaw / (180.0F / (float)Math.PI);
        this.tail.rotationX = bob;
    }
}
