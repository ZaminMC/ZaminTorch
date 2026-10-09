package net.minecraft.client.render.entity;

import net.minecraft.client.render.entity.layer.CreeperChargeLayer;
import net.minecraft.client.render.model.entity.CreeperModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.monster.CreeperEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;

public class CreeperRenderer extends MobRenderer<CreeperEntity> {
    private static final Identifier CREEPER_LOCATION = new Identifier("textures/entity/creeper/creeper.png");

    public CreeperRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher, new CreeperModel(), 0.5F);
        this.addLayer(new CreeperChargeLayer(this));
    }

    protected void applyScale(CreeperEntity creeperEntity, float f) {
        float fx = creeperEntity.getFuse(f);
        float f1 = 1.0F + MathHelper.sin(fx * 100.0F) * fx * 0.01F;
        fx = MathHelper.clamp(fx, 0.0F, 1.0F);
        fx *= fx;
        fx *= fx;
        float f2 = (1.0F + fx * 0.4F) * f1;
        float f3 = (1.0F + fx * 0.1F) / f1;
        GlStateManager.scalef(f2, f3, f2);
    }

    protected int getOverlayColor(CreeperEntity creeperEntity, float f, float g) {
        float fx = creeperEntity.getFuse(g);
        if ((int)(fx * 10.0F) % 2 == 0) {
            return 0;
        }

        int i = (int)(fx * 0.2F * 255.0F);
        i = MathHelper.clamp(i, 0, 255);
        return i << 24 | 16777215;
    }

    protected Identifier getTextureLocation(CreeperEntity creeperEntity) {
        return CREEPER_LOCATION;
    }
}
