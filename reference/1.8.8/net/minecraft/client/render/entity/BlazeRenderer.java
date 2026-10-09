package net.minecraft.client.render.entity;

import net.minecraft.client.render.model.entity.BlazeModel;
import net.minecraft.entity.living.mob.monster.BlazeEntity;
import net.minecraft.resource.Identifier;

public class BlazeRenderer extends MobRenderer<BlazeEntity> {
    private static final Identifier BLAZE_LOCATION = new Identifier("textures/entity/blaze.png");

    public BlazeRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher, new BlazeModel(), 0.5F);
    }

    protected Identifier getTextureLocation(BlazeEntity blazeEntity) {
        return BLAZE_LOCATION;
    }
}
