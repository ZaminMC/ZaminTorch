package net.minecraft.client.render.entity;

import net.minecraft.client.render.model.entity.EndermiteModel;
import net.minecraft.entity.living.mob.monster.EndermiteEntity;
import net.minecraft.resource.Identifier;

public class EndermiteRenderer extends MobRenderer<EndermiteEntity> {
    private static final Identifier TEXTURE = new Identifier("textures/entity/endermite.png");

    public EndermiteRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher, new EndermiteModel(), 0.3F);
    }

    protected float getDeathYaw(EndermiteEntity endermiteEntity) {
        return 180.0F;
    }

    protected Identifier getTextureLocation(EndermiteEntity endermiteEntity) {
        return TEXTURE;
    }
}
