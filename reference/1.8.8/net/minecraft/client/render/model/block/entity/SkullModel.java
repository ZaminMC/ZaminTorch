package net.minecraft.client.render.model.block.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;

public class SkullModel extends Model {
    public ModelPart skull;

    public SkullModel() {
        this(0, 35, 64, 64);
    }

    public SkullModel(int textureOffsetU, int textureOffsetV, int textureWidth, int textureHeight) {
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.skull = new ModelPart(this, textureOffsetU, textureOffsetV);
        this.skull.addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, 0.0F);
        this.skull.setPos(0.0F, 0.0F, 0.0F);
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        this.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        this.skull.render(scale);
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        super.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        this.skull.rotationY = yaw / (180.0F / (float)Math.PI);
        this.skull.rotationX = pitch / (180.0F / (float)Math.PI);
    }
}
