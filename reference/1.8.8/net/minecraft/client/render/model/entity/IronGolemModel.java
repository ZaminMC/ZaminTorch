package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.IronGolemEntity;

public class IronGolemModel extends Model {
    public ModelPart head;
    public ModelPart body;
    public ModelPart rightArm;
    public ModelPart leftArm;
    public ModelPart rightLeg;
    public ModelPart leftLeg;

    public IronGolemModel() {
        this(0.0F);
    }

    public IronGolemModel(float reduction) {
        this(reduction, -7.0F);
    }

    public IronGolemModel(float pivotPoint, float reduction) {
        int i = 128;
        int j = 128;
        this.head = new ModelPart(this).setTextureSize(i, j);
        this.head.setPos(0.0F, 0.0F + reduction, -2.0F);
        this.head.setTextureCoords(0, 0).addBox(-4.0F, -12.0F, -5.5F, 8, 10, 8, pivotPoint);
        this.head.setTextureCoords(24, 0).addBox(-1.0F, -5.0F, -7.5F, 2, 4, 2, pivotPoint);
        this.body = new ModelPart(this).setTextureSize(i, j);
        this.body.setPos(0.0F, 0.0F + reduction, 0.0F);
        this.body.setTextureCoords(0, 40).addBox(-9.0F, -2.0F, -6.0F, 18, 12, 11, pivotPoint);
        this.body.setTextureCoords(0, 70).addBox(-4.5F, 10.0F, -3.0F, 9, 5, 6, pivotPoint + 0.5F);
        this.rightArm = new ModelPart(this).setTextureSize(i, j);
        this.rightArm.setPos(0.0F, -7.0F, 0.0F);
        this.rightArm.setTextureCoords(60, 21).addBox(-13.0F, -2.5F, -3.0F, 4, 30, 6, pivotPoint);
        this.leftArm = new ModelPart(this).setTextureSize(i, j);
        this.leftArm.setPos(0.0F, -7.0F, 0.0F);
        this.leftArm.setTextureCoords(60, 58).addBox(9.0F, -2.5F, -3.0F, 4, 30, 6, pivotPoint);
        this.rightLeg = new ModelPart(this, 0, 22).setTextureSize(i, j);
        this.rightLeg.setPos(-4.0F, 18.0F + reduction, 0.0F);
        this.rightLeg.setTextureCoords(37, 0).addBox(-3.5F, -3.0F, -3.0F, 6, 16, 5, pivotPoint);
        this.leftLeg = new ModelPart(this, 0, 22).setTextureSize(i, j);
        this.leftLeg.flipped = true;
        this.leftLeg.setTextureCoords(60, 0).setPos(5.0F, 18.0F + reduction, 0.0F);
        this.leftLeg.addBox(-3.5F, -3.0F, -3.0F, 6, 16, 5, pivotPoint);
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        this.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        this.head.render(scale);
        this.body.render(scale);
        this.rightLeg.render(scale);
        this.leftLeg.render(scale);
        this.rightArm.render(scale);
        this.leftArm.render(scale);
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        this.head.rotationY = yaw / (180.0F / (float)Math.PI);
        this.head.rotationX = pitch / (180.0F / (float)Math.PI);
        this.rightLeg.rotationX = -1.5F * this.getRotation(walkAnimationProgress, 13.0F) * walkAnimationSpeed;
        this.leftLeg.rotationX = 1.5F * this.getRotation(walkAnimationProgress, 13.0F) * walkAnimationSpeed;
        this.rightLeg.rotationY = 0.0F;
        this.leftLeg.rotationY = 0.0F;
    }

    @Override
    public void prepare(LivingEntity mob, float walkAnimationProgress, float walkAnimationSpeed, float tickDelta) {
        IronGolemEntity irongolementity = (IronGolemEntity)mob;
        int i = irongolementity.getAttackTicksLeft();
        if (i > 0) {
            this.rightArm.rotationX = -2.0F + 1.5F * this.getRotation(i - tickDelta, 10.0F);
            this.leftArm.rotationX = -2.0F + 1.5F * this.getRotation(i - tickDelta, 10.0F);
        } else {
            int j = irongolementity.getLookingAtVillagerTicks();
            if (j > 0) {
                this.rightArm.rotationX = -0.8F + 0.025F * this.getRotation(j, 70.0F);
                this.leftArm.rotationX = 0.0F;
            } else {
                this.rightArm.rotationX = (-0.2F + 1.5F * this.getRotation(walkAnimationProgress, 13.0F)) * walkAnimationSpeed;
                this.leftArm.rotationX = (-0.2F - 1.5F * this.getRotation(walkAnimationProgress, 13.0F)) * walkAnimationSpeed;
            }
        }
    }

    private float getRotation(float rotation, float tickDelta) {
        return (Math.abs(rotation % tickDelta - tickDelta * 0.5F) - tickDelta * 0.25F) / (tickDelta * 0.25F);
    }
}
