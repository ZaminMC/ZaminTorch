package net.minecraft.client.render.entity;

import net.minecraft.client.render.entity.layer.SpiderEyesLayer;
import net.minecraft.client.render.model.entity.SpiderModel;
import net.minecraft.entity.living.mob.monster.SpiderEntity;
import net.minecraft.resource.Identifier;

public class SpiderRenderer<T extends SpiderEntity> extends MobRenderer<T> {
    private static final Identifier SPIDER_LOCATION = new Identifier("textures/entity/spider/spider.png");

    public SpiderRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher, new SpiderModel(), 1.0F);
        this.addLayer(new SpiderEyesLayer(this));
    }

    protected float getDeathYaw(T spiderEntity) {
        return 180.0F;
    }

    protected Identifier getTextureLocation(T spiderEntity) {
        return SPIDER_LOCATION;
    }
}
