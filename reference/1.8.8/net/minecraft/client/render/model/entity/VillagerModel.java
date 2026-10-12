package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public class VillagerModel extends Model {
    public ModelPart head;
    public ModelPart body;
    public ModelPart arms;
    public ModelPart rightLeg;
    public ModelPart leftLeg;
    public ModelPart nose;

    public VillagerModel(float reduction) {
        this(reduction, 0.0F, 64, 64);
    }

    public VillagerModel(float reduction, float pivotPoint, int textureWidth, int textureHeight) {
        this.head = new ModelPart(this).setTextureSize(textureWidth, textureHeight);
        this.head.setPos(0.0F, 0.0F + pivotPoint, 0.0F);
        this.head.setTextureCoords(0, 0).addBox(-4.0F, -10.0F, -4.0F, 8, 10, 8, reduction);
        this.nose = new ModelPart(this).setTextureSize(textureWidth, textureHeight);
        this.nose.setPos(0.0F, pivotPoint - 2.0F, 0.0F);
        this.nose.setTextureCoords(24, 0).addBox(-1.0F, -1.0F, -6.0F, 2, 4, 2, reduction);
        this.head.addChild(this.nose);
        this.body = new ModelPart(this).setTextureSize(textureWidth, textureHeight);
        this.body.setPos(0.0F, 0.0F + pivotPoint, 0.0F);
        this.body.setTextureCoords(16, 20).addBox(-4.0F, 0.0F, -3.0F, 8, 12, 6, reduction);
        this.body.setTextureCoords(0, 38).addBox(-4.0F, 0.0F, -3.0F, 8, 18, 6, reduction + 0.5F);
        this.arms = new ModelPart(this).setTextureSize(textureWidth, textureHeight);
        this.arms.setPos(0.0F, 0.0F + pivotPoint + 2.0F, 0.0F);
        this.arms.setTextureCoords(44, 22).addBox(-8.0F, -2.0F, -2.0F, 4, 8, 4, reduction);
        this.arms.setTextureCoords(44, 22).addBox(4.0F, -2.0F, -2.0F, 4, 8, 4, reduction);
        this.arms.setTextureCoords(40, 38).addBox(-4.0F, 2.0F, -2.0F, 8, 4, 4, reduction);
        this.rightLeg = new ModelPart(this, 0, 22).setTextureSize(textureWidth, textureHeight);
        this.rightLeg.setPos(-2.0F, 12.0F + pivotPoint, 0.0F);
        this.rightLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, reduction);
        this.leftLeg = new ModelPart(this, 0, 22).setTextureSize(textureWidth, textureHeight);
        this.leftLeg.flipped = true;
        this.leftLeg.setPos(2.0F, 12.0F + pivotPoint, 0.0F);
        this.leftLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, reduction);
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        this.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        this.head.render(scale);
        this.body.render(scale);
        this.rightLeg.render(scale);
        this.leftLeg.render(scale);
        this.arms.render(scale);
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        this.head.rotationY = yaw / (180.0F / (float)Math.PI);
        this.head.rotationX = pitch / (180.0F / (float)Math.PI);
        this.arms.y = 3.0F;
        this.arms.z = -1.0F;
        this.arms.rotationX = -0.75F;
        this.rightLeg.rotationX = MathHelper.cos(walkAnimationProgress * 0.6662F) * 1.4F * walkAnimationSpeed * 0.5F;
        this.leftLeg.rotationX = MathHelper.cos(walkAnimationProgress * 0.6662F + (float) Math.PI) * 1.4F * walkAnimationSpeed * 0.5F;
        this.rightLeg.rotationY = 0.0F;
        this.leftLeg.rotationY = 0.0F;
    }
}
