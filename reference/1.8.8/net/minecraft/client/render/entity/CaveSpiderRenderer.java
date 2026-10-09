package net.minecraft.client.render.entity;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.monster.CaveSpiderEntity;
import net.minecraft.resource.Identifier;

public class CaveSpiderRenderer extends SpiderRenderer<CaveSpiderEntity> {
    private static final Identifier CAVE_SPIDER_LOCATION = new Identifier("textures/entity/spider/cave_spider.png");

    public CaveSpiderRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher);
        this.shadowSize *= 0.7F;
    }

    protected void applyScale(CaveSpiderEntity caveSpiderEntity, float f) {
        GlStateManager.scalef(0.7F, 0.7F, 0.7F);
    }

    protected Identifier getTextureLocation(CaveSpiderEntity caveSpiderEntity) {
        return CAVE_SPIDER_LOCATION;
    }
}
