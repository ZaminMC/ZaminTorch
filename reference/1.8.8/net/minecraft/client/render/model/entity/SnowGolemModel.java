package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public class SnowGolemModel extends Model {
    public ModelPart top;
    public ModelPart bottom;
    public ModelPart head;
    public ModelPart rightArm;
    public ModelPart leftArm;

    public SnowGolemModel() {
        float f = 4.0F;
        float f1 = 0.0F;
        this.head = new ModelPart(this, 0, 0).setTextureSize(64, 64);
        this.head.addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, f1 - 0.5F);
        this.head.setPos(0.0F, 0.0F + f, 0.0F);
        this.rightArm = new ModelPart(this, 32, 0).setTextureSize(64, 64);
        this.rightArm.addBox(-1.0F, 0.0F, -1.0F, 12, 2, 2, f1 - 0.5F);
        this.rightArm.setPos(0.0F, 0.0F + f + 9.0F - 7.0F, 0.0F);
        this.leftArm = new ModelPart(this, 32, 0).setTextureSize(64, 64);
        this.leftArm.addBox(-1.0F, 0.0F, -1.0F, 12, 2, 2, f1 - 0.5F);
        this.leftArm.setPos(0.0F, 0.0F + f + 9.0F - 7.0F, 0.0F);
        this.top = new ModelPart(this, 0, 16).setTextureSize(64, 64);
        this.top.addBox(-5.0F, -10.0F, -5.0F, 10, 10, 10, f1 - 0.5F);
        this.top.setPos(0.0F, 0.0F + f + 9.0F, 0.0F);
        this.bottom = new ModelPart(this, 0, 36).setTextureSize(64, 64);
        this.bottom.addBox(-6.0F, -12.0F, -6.0F, 12, 12, 12, f1 - 0.5F);
        this.bottom.setPos(0.0F, 0.0F + f + 20.0F, 0.0F);
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        super.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        this.head.rotationY = yaw / (180.0F / (float)Math.PI);
        this.head.rotationX = pitch / (180.0F / (float)Math.PI);
        this.top.rotationY = yaw / (180.0F / (float)Math.PI) * 0.25F;
        float f = MathHelper.sin(this.top.rotationY);
        float f1 = MathHelper.cos(this.top.rotationY);
        this.rightArm.rotationZ = 1.0F;
        this.leftArm.rotationZ = -1.0F;
        this.rightArm.rotationY = 0.0F + this.top.rotationY;
        this.leftArm.rotationY = (float) Math.PI + this.top.rotationY;
        this.rightArm.x = f1 * 5.0F;
        this.rightArm.z = -f * 5.0F;
        this.leftArm.x = -f1 * 5.0F;
        this.leftArm.z = f * 5.0F;
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        this.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        this.top.render(scale);
        this.bottom.render(scale);
        this.head.render(scale);
        this.rightArm.render(scale);
        this.leftArm.render(scale);
    }
}
