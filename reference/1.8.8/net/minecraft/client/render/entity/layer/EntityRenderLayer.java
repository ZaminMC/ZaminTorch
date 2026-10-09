package net.minecraft.client.render.entity.layer;

import net.minecraft.entity.living.LivingEntity;

public interface EntityRenderLayer<E extends LivingEntity> {
    void render(E entity, float walkAnimationProgress, float walkAnimationSpeed, float tickDelta, float bob, float yaw, float pitch, float scale);

    boolean colorsWhenDamaged();
}
