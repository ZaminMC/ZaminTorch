package net.minecraft.client.render.entity;

import net.minecraft.client.render.model.entity.GhastModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.monster.GhastEntity;
import net.minecraft.resource.Identifier;

public class GhastRenderer extends MobRenderer<GhastEntity> {
    private static final Identifier GHAST_LOCATION = new Identifier("textures/entity/ghast/ghast.png");
    private static final Identifier GHAST_SHOOTING_LOCATION = new Identifier("textures/entity/ghast/ghast_shooting.png");

    public GhastRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher, new GhastModel(), 0.5F);
    }

    protected Identifier getTextureLocation(GhastEntity ghastEntity) {
        return ghastEntity.isCharging() ? GHAST_SHOOTING_LOCATION : GHAST_LOCATION;
    }

    protected void applyScale(GhastEntity ghastEntity, float f) {
        float fx = 1.0F;
        float f1 = (8.0F + fx) / 2.0F;
        float f2 = (8.0F + 1.0F / fx) / 2.0F;
        GlStateManager.scalef(f2, f1, f2);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
