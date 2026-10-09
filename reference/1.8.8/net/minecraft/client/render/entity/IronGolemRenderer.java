package net.minecraft.client.render.entity;

import net.minecraft.client.render.entity.layer.IronGolemFlowerInHandLayer;
import net.minecraft.client.render.model.entity.IronGolemModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.IronGolemEntity;
import net.minecraft.resource.Identifier;

public class IronGolemRenderer extends MobRenderer<IronGolemEntity> {
    private static final Identifier IRON_GOLEM_LOCATION = new Identifier("textures/entity/iron_golem.png");

    public IronGolemRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher, new IronGolemModel(), 0.5F);
        this.addLayer(new IronGolemFlowerInHandLayer(this));
    }

    protected Identifier getTextureLocation(IronGolemEntity ironGolemEntity) {
        return IRON_GOLEM_LOCATION;
    }

    protected void applyRotation(IronGolemEntity ironGolemEntity, float f, float g, float h) {
        super.applyRotation(ironGolemEntity, f, g, h);
        if (!(ironGolemEntity.walkAnimationSpeed < 0.01)) {
            float fx = 13.0F;
            float f1 = ironGolemEntity.walkAnimationProgress - ironGolemEntity.walkAnimationSpeed * (1.0F - h) + 6.0F;
            float f2 = (Math.abs(f1 % fx - fx * 0.5F) - fx * 0.25F) / (fx * 0.25F);
            GlStateManager.rotatef(6.5F * f2, 0.0F, 0.0F, 1.0F);
        }
    }
}
