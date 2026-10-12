package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public class QuadrupedModel extends Model {
    public ModelPart head;
    public ModelPart body;
    public ModelPart backRightLeg;
    public ModelPart backLeftLeg;
    public ModelPart frontRightLeg;
    public ModelPart frontLeftLeg;
    protected float babyHeadHeightOffset = 8.0F;
    protected float babyHeadOffset = 4.0F;

    public QuadrupedModel(int pivotPoint, float reduction) {
        this.head = new ModelPart(this, 0, 0);
        this.head.addBox(-4.0F, -4.0F, -8.0F, 8, 8, 8, reduction);
        this.head.setPos(0.0F, 18 - pivotPoint, -6.0F);
        this.body = new ModelPart(this, 28, 8);
        this.body.addBox(-5.0F, -10.0F, -7.0F, 10, 16, 8, reduction);
        this.body.setPos(0.0F, 17 - pivotPoint, 2.0F);
        this.backRightLeg = new ModelPart(this, 0, 16);
        this.backRightLeg.addBox(-2.0F, 0.0F, -2.0F, 4, pivotPoint, 4, reduction);
        this.backRightLeg.setPos(-3.0F, 24 - pivotPoint, 7.0F);
        this.backLeftLeg = new ModelPart(this, 0, 16);
        this.backLeftLeg.addBox(-2.0F, 0.0F, -2.0F, 4, pivotPoint, 4, reduction);
        this.backLeftLeg.setPos(3.0F, 24 - pivotPoint, 7.0F);
        this.frontRightLeg = new ModelPart(this, 0, 16);
        this.frontRightLeg.addBox(-2.0F, 0.0F, -2.0F, 4, pivotPoint, 4, reduction);
        this.frontRightLeg.setPos(-3.0F, 24 - pivotPoint, -5.0F);
        this.frontLeftLeg = new ModelPart(this, 0, 16);
        this.frontLeftLeg.addBox(-2.0F, 0.0F, -2.0F, 4, pivotPoint, 4, reduction);
        this.frontLeftLeg.setPos(3.0F, 24 - pivotPoint, -5.0F);
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        this.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        if (this.isBaby) {
            float f = 2.0F;
            GlStateManager.pushMatrix();
            GlStateManager.translatef(0.0F, this.babyHeadHeightOffset * scale, this.babyHeadOffset * scale);
            this.head.render(scale);
            GlStateManager.popMatrix();
            GlStateManager.pushMatrix();
            GlStateManager.scalef(1.0F / f, 1.0F / f, 1.0F / f);
            GlStateManager.translatef(0.0F, 24.0F * scale, 0.0F);
            this.body.render(scale);
            this.backRightLeg.render(scale);
            this.backLeftLeg.render(scale);
            this.frontRightLeg.render(scale);
            this.frontLeftLeg.render(scale);
            GlStateManager.popMatrix();
        } else {
            this.head.render(scale);
            this.body.render(scale);
            this.backRightLeg.render(scale);
            this.backLeftLeg.render(scale);
            this.frontRightLeg.render(scale);
            this.frontLeftLeg.render(scale);
        }
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        float f = 180.0F / (float)Math.PI;
        this.head.rotationX = pitch / (180.0F / (float)Math.PI);
        this.head.rotationY = yaw / (180.0F / (float)Math.PI);
        this.body.rotationX = (float) (Math.PI / 2);
        this.backRightLeg.rotationX = MathHelper.cos(walkAnimationProgress * 0.6662F) * 1.4F * walkAnimationSpeed;
        this.backLeftLeg.rotationX = MathHelper.cos(walkAnimationProgress * 0.6662F + (float) Math.PI) * 1.4F * walkAnimationSpeed;
        this.frontRightLeg.rotationX = MathHelper.cos(walkAnimationProgress * 0.6662F + (float) Math.PI) * 1.4F * walkAnimationSpeed;
        this.frontLeftLeg.rotationX = MathHelper.cos(walkAnimationProgress * 0.6662F) * 1.4F * walkAnimationSpeed;
    }
}
