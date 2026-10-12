package net.minecraft.client.render.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.water.SquidEntity;
import net.minecraft.resource.Identifier;

public class SquidRenderer extends MobRenderer<SquidEntity> {
    private static final Identifier SQUID_LOCATION = new Identifier("textures/entity/squid.png");

    public SquidRenderer(EntityRenderDispatcher entityRenderDispatcher, Model model, float f) {
        super(entityRenderDispatcher, model, f);
    }

    protected Identifier getTextureLocation(SquidEntity squidEntity) {
        return SQUID_LOCATION;
    }

    protected void applyRotation(SquidEntity squidEntity, float f, float g, float h) {
        float fx = squidEntity.lastBodyPitch + (squidEntity.bodyPitch - squidEntity.lastBodyPitch) * h;
        float f1 = squidEntity.lastBodyRoll + (squidEntity.bodyRoll - squidEntity.lastBodyRoll) * h;
        GlStateManager.translatef(0.0F, 0.5F, 0.0F);
        GlStateManager.rotatef(180.0F - g, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(fx, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotatef(f1, 0.0F, 1.0F, 0.0F);
        GlStateManager.translatef(0.0F, -1.2F, 0.0F);
    }

    protected float getBob(SquidEntity squidEntity, float f) {
        return squidEntity.lastTentacleRotation + (squidEntity.tentacleRotation - squidEntity.lastTentacleRotation) * f;
    }
}
