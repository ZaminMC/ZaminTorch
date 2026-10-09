package net.minecraft.client.render.entity;

import net.minecraft.client.render.entity.layer.WolfCollarLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.passive.animal.tameable.WolfEntity;
import net.minecraft.resource.Identifier;

public class WolfRenderer extends MobRenderer<WolfEntity> {
    private static final Identifier WOLF_LOCATION = new Identifier("textures/entity/wolf/wolf.png");
    private static final Identifier TAME_WOLF_LOCATION = new Identifier("textures/entity/wolf/wolf_tame.png");
    private static final Identifier ANGRY_WOLF_LOCATION = new Identifier("textures/entity/wolf/wolf_angry.png");

    public WolfRenderer(EntityRenderDispatcher entityRenderDispatcher, Model model, float f) {
        super(entityRenderDispatcher, model, f);
        this.addLayer(new WolfCollarLayer(this));
    }

    protected float getBob(WolfEntity wolfEntity, float f) {
        return wolfEntity.getTailBob();
    }

    public void render(WolfEntity wolfEntity, double d, double e, double f, float g, float h) {
        if (wolfEntity.isWet()) {
            float fx = wolfEntity.getBrightness(h) * wolfEntity.getShakeProgress(h);
            GlStateManager.color3f(fx, fx, fx);
        }

        super.render(wolfEntity, d, e, f, g, h);
    }

    protected Identifier getTextureLocation(WolfEntity wolfEntity) {
        if (wolfEntity.isTamed()) {
            return TAME_WOLF_LOCATION;
        } else {
            return wolfEntity.isAngry() ? ANGRY_WOLF_LOCATION : WOLF_LOCATION;
        }
    }
}
