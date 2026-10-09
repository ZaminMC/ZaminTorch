package net.minecraft.client.render.model.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;

public class LeashModel extends Model {
    public ModelPart leash;

    public LeashModel() {
        this(0, 0, 32, 32);
    }

    public LeashModel(int textureOffsetU, int textureOffsetV, int textureWidth, int textureHeight) {
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.leash = new ModelPart(this, textureOffsetU, textureOffsetV);
        this.leash.addBox(-3.0F, -6.0F, -3.0F, 6, 8, 6, 0.0F);
        this.leash.setPos(0.0F, 0.0F, 0.0F);
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        this.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        this.leash.render(scale);
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        super.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        this.leash.rotationY = yaw / (180.0F / (float)Math.PI);
        this.leash.rotationX = pitch / (180.0F / (float)Math.PI);
    }
}
