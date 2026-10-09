package net.minecraft.client.render.entity;

import net.minecraft.client.render.model.entity.SilverfishModel;
import net.minecraft.entity.living.mob.monster.SilverfishEntity;
import net.minecraft.resource.Identifier;

public class SilverfishRenderer extends MobRenderer<SilverfishEntity> {
    private static final Identifier SILVERFISH_LOCATION = new Identifier("textures/entity/silverfish.png");

    public SilverfishRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher, new SilverfishModel(), 0.3F);
    }

    protected float getDeathYaw(SilverfishEntity silverfishEntity) {
        return 180.0F;
    }

    protected Identifier getTextureLocation(SilverfishEntity silverfishEntity) {
        return SILVERFISH_LOCATION;
    }
}
