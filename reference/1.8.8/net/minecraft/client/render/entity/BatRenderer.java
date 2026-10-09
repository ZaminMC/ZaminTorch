package net.minecraft.client.render.entity;

import net.minecraft.client.render.model.entity.BatModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.ambient.BatEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;

public class BatRenderer extends MobRenderer<BatEntity> {
    private static final Identifier BAT_LOCATION = new Identifier("textures/entity/bat.png");

    public BatRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher, new BatModel(), 0.25F);
    }

    protected Identifier getTextureLocation(BatEntity batEntity) {
        return BAT_LOCATION;
    }

    protected void applyScale(BatEntity batEntity, float f) {
        GlStateManager.scalef(0.35F, 0.35F, 0.35F);
    }

    protected void applyRotation(BatEntity batEntity, float f, float g, float h) {
        if (!batEntity.isRoosting()) {
            GlStateManager.translatef(0.0F, MathHelper.cos(f * 0.3F) * 0.1F, 0.0F);
        } else {
            GlStateManager.translatef(0.0F, -0.1F, 0.0F);
        }

        super.applyRotation(batEntity, f, g, h);
    }
}
