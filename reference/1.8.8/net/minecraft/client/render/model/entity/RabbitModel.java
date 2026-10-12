package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.passive.animal.RabbitEntity;
import net.minecraft.util.math.MathHelper;

public class RabbitModel extends Model {
    ModelPart backLeftLeg;
    ModelPart backRightLeg;
    ModelPart leftHaunch;
    ModelPart rightHaunch;
    ModelPart body;
    ModelPart frontLeftLeg;
    ModelPart frontRightLeg;
    ModelPart head;
    ModelPart rightEar;
    ModelPart leftEar;
    ModelPart tail;
    ModelPart nose;
    private float jumpRotation = 0.0F;
    private float f_9633975 = 0.0F;

    public RabbitModel() {
        this.setTexturePos("head.main", 0, 0);
        this.setTexturePos("head.nose", 0, 24);
        this.setTexturePos("head.ear1", 0, 10);
        this.setTexturePos("head.ear2", 6, 10);
        this.backLeftLeg = new ModelPart(this, 26, 24);
        this.backLeftLeg.addBox(-1.0F, 5.5F, -3.7F, 2, 1, 7);
        this.backLeftLeg.setPos(3.0F, 17.5F, 3.7F);
        this.backLeftLeg.flipped = true;
        this.setRotation(this.backLeftLeg, 0.0F, 0.0F, 0.0F);
        this.backRightLeg = new ModelPart(this, 8, 24);
        this.backRightLeg.addBox(-1.0F, 5.5F, -3.7F, 2, 1, 7);
        this.backRightLeg.setPos(-3.0F, 17.5F, 3.7F);
        this.backRightLeg.flipped = true;
        this.setRotation(this.backRightLeg, 0.0F, 0.0F, 0.0F);
        this.leftHaunch = new ModelPart(this, 30, 15);
        this.leftHaunch.addBox(-1.0F, 0.0F, 0.0F, 2, 4, 5);
        this.leftHaunch.setPos(3.0F, 17.5F, 3.7F);
        this.leftHaunch.flipped = true;
        this.setRotation(this.leftHaunch, (float) (-Math.PI / 9), 0.0F, 0.0F);
        this.rightHaunch = new ModelPart(this, 16, 15);
        this.rightHaunch.addBox(-1.0F, 0.0F, 0.0F, 2, 4, 5);
        this.rightHaunch.setPos(-3.0F, 17.5F, 3.7F);
        this.rightHaunch.flipped = true;
        this.setRotation(this.rightHaunch, (float) (-Math.PI / 9), 0.0F, 0.0F);
        this.body = new ModelPart(this, 0, 0);
        this.body.addBox(-3.0F, -2.0F, -10.0F, 6, 5, 10);
        this.body.setPos(0.0F, 19.0F, 8.0F);
        this.body.flipped = true;
        this.setRotation(this.body, (float) (-Math.PI / 9), 0.0F, 0.0F);
        this.frontLeftLeg = new ModelPart(this, 8, 15);
        this.frontLeftLeg.addBox(-1.0F, 0.0F, -1.0F, 2, 7, 2);
        this.frontLeftLeg.setPos(3.0F, 17.0F, -1.0F);
        this.frontLeftLeg.flipped = true;
        this.setRotation(this.frontLeftLeg, (float) (-Math.PI / 18), 0.0F, 0.0F);
        this.frontRightLeg = new ModelPart(this, 0, 15);
        this.frontRightLeg.addBox(-1.0F, 0.0F, -1.0F, 2, 7, 2);
        this.frontRightLeg.setPos(-3.0F, 17.0F, -1.0F);
        this.frontRightLeg.flipped = true;
        this.setRotation(this.frontRightLeg, (float) (-Math.PI / 18), 0.0F, 0.0F);
        this.head = new ModelPart(this, 32, 0);
        this.head.addBox(-2.5F, -4.0F, -5.0F, 5, 4, 5);
        this.head.setPos(0.0F, 16.0F, -1.0F);
        this.head.flipped = true;
        this.setRotation(this.head, 0.0F, 0.0F, 0.0F);
        this.rightEar = new ModelPart(this, 52, 0);
        this.rightEar.addBox(-2.5F, -9.0F, -1.0F, 2, 5, 1);
        this.rightEar.setPos(0.0F, 16.0F, -1.0F);
        this.rightEar.flipped = true;
        this.setRotation(this.rightEar, 0.0F, (float) (-Math.PI / 12), 0.0F);
        this.leftEar = new ModelPart(this, 58, 0);
        this.leftEar.addBox(0.5F, -9.0F, -1.0F, 2, 5, 1);
        this.leftEar.setPos(0.0F, 16.0F, -1.0F);
        this.leftEar.flipped = true;
        this.setRotation(this.leftEar, 0.0F, (float) (Math.PI / 12), 0.0F);
        this.tail = new ModelPart(this, 52, 6);
        this.tail.addBox(-1.5F, -1.5F, 0.0F, 3, 3, 2);
        this.tail.setPos(0.0F, 20.0F, 7.0F);
        this.tail.flipped = true;
        this.setRotation(this.tail, -0.3490659F, 0.0F, 0.0F);
        this.nose = new ModelPart(this, 32, 9);
        this.nose.addBox(-0.5F, -2.5F, -5.5F, 1, 1, 1);
        this.nose.setPos(0.0F, 16.0F, -1.0F);
        this.nose.flipped = true;
        this.setRotation(this.nose, 0.0F, 0.0F, 0.0F);
    }

    private void setRotation(ModelPart part, float rotationX, float rotationY, float rotationZ) {
        part.rotationX = rotationX;
        part.rotationY = rotationY;
        part.rotationZ = rotationZ;
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        this.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        if (this.isBaby) {
            float f = 2.0F;
            GlStateManager.pushMatrix();
            GlStateManager.translatef(0.0F, 5.0F * scale, 2.0F * scale);
            this.head.render(scale);
            this.leftEar.render(scale);
            this.rightEar.render(scale);
            this.nose.render(scale);
            GlStateManager.popMatrix();
            GlStateManager.pushMatrix();
            GlStateManager.scalef(1.0F / f, 1.0F / f, 1.0F / f);
            GlStateManager.translatef(0.0F, 24.0F * scale, 0.0F);
            this.backLeftLeg.render(scale);
            this.backRightLeg.render(scale);
            this.leftHaunch.render(scale);
            this.rightHaunch.render(scale);
            this.body.render(scale);
            this.frontLeftLeg.render(scale);
            this.frontRightLeg.render(scale);
            this.tail.render(scale);
            GlStateManager.popMatrix();
        } else {
            this.backLeftLeg.render(scale);
            this.backRightLeg.render(scale);
            this.leftHaunch.render(scale);
            this.rightHaunch.render(scale);
            this.body.render(scale);
            this.frontLeftLeg.render(scale);
            this.frontRightLeg.render(scale);
            this.head.render(scale);
            this.rightEar.render(scale);
            this.leftEar.render(scale);
            this.tail.render(scale);
            this.nose.render(scale);
        }
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        float f = bob - entity.ticks;
        RabbitEntity rabbitentity = (RabbitEntity)entity;
        this.nose.rotationX = this.head.rotationX = this.rightEar.rotationX = this.leftEar.rotationX = pitch * (float) (Math.PI / 180.0);
        this.nose.rotationY = this.head.rotationY = yaw * (float) (Math.PI / 180.0);
        this.rightEar.rotationY = this.nose.rotationY - (float) (Math.PI / 12);
        this.leftEar.rotationY = this.nose.rotationY + (float) (Math.PI / 12);
        this.jumpRotation = MathHelper.sin(rabbitentity.getJumpCompletion(f) * (float) Math.PI);
        this.leftHaunch.rotationX = this.rightHaunch.rotationX = (this.jumpRotation * 50.0F - 21.0F) * (float) (Math.PI / 180.0);
        this.backLeftLeg.rotationX = this.backRightLeg.rotationX = this.jumpRotation * 50.0F * (float) (Math.PI / 180.0);
        this.frontLeftLeg.rotationX = this.frontRightLeg.rotationX = (this.jumpRotation * -40.0F - 11.0F) * (float) (Math.PI / 180.0);
    }

    @Override
    public void prepare(LivingEntity mob, float walkAnimationProgress, float walkAnimationSpeed, float tickDelta) {
    }
}
