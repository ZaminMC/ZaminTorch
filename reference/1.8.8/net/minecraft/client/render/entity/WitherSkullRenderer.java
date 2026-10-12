package net.minecraft.client.render.entity;

import net.minecraft.client.render.model.block.entity.SkullModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.resource.Identifier;

public class WitherSkullRenderer extends EntityRenderer<WitherSkullEntity> {
    private static final Identifier WITHER_INVULNERABLE_LOCATION = new Identifier("textures/entity/wither/wither_invulnerable.png");
    private static final Identifier WITHER_LOCATION = new Identifier("textures/entity/wither/wither.png");
    private final SkullModel model = new SkullModel();

    public WitherSkullRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher);
    }

    private float getYaw(float prevYaw, float yaw, float tickDelta) {
        float f = yaw - prevYaw;

        while (f < -180.0F) {
            f += 360.0F;
        }

        while (f >= 180.0F) {
            f -= 360.0F;
        }

        return prevYaw + tickDelta * f;
    }

    public void render(WitherSkullEntity witherSkullEntity, double d, double e, double f, float g, float h) {
        GlStateManager.pushMatrix();
        GlStateManager.disableCull();
        float fx = this.getYaw(witherSkullEntity.lastYaw, witherSkullEntity.yaw, h);
        float f1 = witherSkullEntity.lastPitch + (witherSkullEntity.pitch - witherSkullEntity.lastPitch) * h;
        GlStateManager.translatef((float)d, (float)e, (float)f);
        float f2 = 0.0625F;
        GlStateManager.enableRescaleNormal();
        GlStateManager.scalef(-1.0F, -1.0F, 1.0F);
        GlStateManager.enableAlphaTest();
        this.bindTexture(witherSkullEntity);
        this.model.render(witherSkullEntity, 0.0F, 0.0F, 0.0F, fx, f1, f2);
        GlStateManager.popMatrix();
        super.render(witherSkullEntity, d, e, f, g, h);
    }

    protected Identifier getTextureLocation(WitherSkullEntity witherSkullEntity) {
        return witherSkullEntity.isCharged() ? WITHER_INVULNERABLE_LOCATION : WITHER_LOCATION;
    }
}
