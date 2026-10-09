package net.minecraft.client.render.entity;

import net.minecraft.client.render.entity.layer.SlimeOuterLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.monster.SlimeEntity;
import net.minecraft.resource.Identifier;

public class SlimeRenderer extends MobRenderer<SlimeEntity> {
    private static final Identifier SLIME_LOCATION = new Identifier("textures/entity/slime/slime.png");

    public SlimeRenderer(EntityRenderDispatcher entityRenderDispatcher, Model model, float f) {
        super(entityRenderDispatcher, model, f);
        this.addLayer(new SlimeOuterLayer(this));
    }

    public void render(SlimeEntity slimeEntity, double d, double e, double f, float g, float h) {
        this.shadowSize = 0.25F * slimeEntity.getSize();
        super.render(slimeEntity, d, e, f, g, h);
    }

    protected void applyScale(SlimeEntity slimeEntity, float f) {
        float fx = slimeEntity.getSize();
        float f1 = (slimeEntity.lastStretch + (slimeEntity.stretch - slimeEntity.lastStretch) * f) / (fx * 0.5F + 1.0F);
        float f2 = 1.0F / (f1 + 1.0F);
        GlStateManager.scalef(f2 * fx, 1.0F / f2 * fx, f2 * fx);
    }

    protected Identifier getTextureLocation(SlimeEntity slimeEntity) {
        return SLIME_LOCATION;
    }
}
