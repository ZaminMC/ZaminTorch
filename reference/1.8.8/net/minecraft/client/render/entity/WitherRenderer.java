package net.minecraft.client.render.entity;

import net.minecraft.client.gui.BossBar;
import net.minecraft.client.render.entity.layer.WitherChargeLayer;
import net.minecraft.client.render.model.entity.WitherModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.monster.boss.WitherEntity;
import net.minecraft.resource.Identifier;

public class WitherRenderer extends MobRenderer<WitherEntity> {
    private static final Identifier WITHER_INVULNERABLE_LOCATION = new Identifier("textures/entity/wither/wither_invulnerable.png");
    private static final Identifier WITHER_LOCATION = new Identifier("textures/entity/wither/wither.png");

    public WitherRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher, new WitherModel(0.0F), 1.0F);
        this.addLayer(new WitherChargeLayer(this));
    }

    public void render(WitherEntity witherEntity, double d, double e, double f, float g, float h) {
        BossBar.update(witherEntity, true);
        super.render(witherEntity, d, e, f, g, h);
    }

    protected Identifier getTextureLocation(WitherEntity witherEntity) {
        int i = witherEntity.getInvulnerabilityTimer();
        return i > 0 && (i > 80 || i / 5 % 2 != 1) ? WITHER_INVULNERABLE_LOCATION : WITHER_LOCATION;
    }

    protected void applyScale(WitherEntity witherEntity, float f) {
        float fx = 2.0F;
        int i = witherEntity.getInvulnerabilityTimer();
        if (i > 0) {
            fx -= (i - f) / 220.0F * 0.5F;
        }

        GlStateManager.scalef(fx, fx, fx);
    }
}
