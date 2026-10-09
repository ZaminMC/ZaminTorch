package net.minecraft.client.render.entity;

import net.minecraft.client.render.entity.layer.SnowGolemHeadLayer;
import net.minecraft.client.render.model.entity.SnowGolemModel;
import net.minecraft.entity.living.mob.SnowGolemEntity;
import net.minecraft.resource.Identifier;

public class SnowGolemRenderer extends MobRenderer<SnowGolemEntity> {
    private static final Identifier SNOW_GOLEM_LOCATION = new Identifier("textures/entity/snowman.png");

    public SnowGolemRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher, new SnowGolemModel(), 0.5F);
        this.addLayer(new SnowGolemHeadLayer(this));
    }

    protected Identifier getTextureLocation(SnowGolemEntity snowGolemEntity) {
        return SNOW_GOLEM_LOCATION;
    }

    public SnowGolemModel getModel() {
        return (SnowGolemModel)super.getModel();
    }
}
