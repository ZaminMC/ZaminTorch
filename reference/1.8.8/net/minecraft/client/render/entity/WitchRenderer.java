package net.minecraft.client.render.entity;

import net.minecraft.client.render.entity.layer.WitchItemInHandLayer;
import net.minecraft.client.render.model.entity.WitchModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.monster.WitchEntity;
import net.minecraft.resource.Identifier;

public class WitchRenderer extends MobRenderer<WitchEntity> {
    private static final Identifier WITCH_LOCATION = new Identifier("textures/entity/witch.png");

    public WitchRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher, new WitchModel(0.0F), 0.5F);
        this.addLayer(new WitchItemInHandLayer(this));
    }

    public void render(WitchEntity witchEntity, double d, double e, double f, float g, float h) {
        ((WitchModel)this.model).itemInHand = witchEntity.getDisplayItemInHand() != null;
        super.render(witchEntity, d, e, f, g, h);
    }

    protected Identifier getTextureLocation(WitchEntity witchEntity) {
        return WITCH_LOCATION;
    }

    @Override
    public void glTranslate() {
        GlStateManager.translatef(0.0F, 0.1875F, 0.0F);
    }

    protected void applyScale(WitchEntity witchEntity, float f) {
        float fx = 0.9375F;
        GlStateManager.scalef(fx, fx, fx);
    }
}
