package net.minecraft.client.render.model.block.entity;

import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;

public class HumanoidSkullModel extends SkullModel {
    private final ModelPart hat = new ModelPart(this, 32, 0);

    public HumanoidSkullModel() {
        super(0, 0, 64, 64);
        this.hat.addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, 0.25F);
        this.hat.setPos(0.0F, 0.0F, 0.0F);
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        super.render(entity, walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale);
        this.hat.render(scale);
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        super.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        this.hat.rotationY = this.skull.rotationY;
        this.hat.rotationX = this.skull.rotationX;
    }
}
