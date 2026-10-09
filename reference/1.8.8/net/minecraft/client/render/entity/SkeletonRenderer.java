package net.minecraft.client.render.entity;

import net.minecraft.client.render.entity.layer.ArmorLayer;
import net.minecraft.client.render.entity.layer.ItemInHandLayer;
import net.minecraft.client.render.model.entity.SkeletonModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.monster.SkeletonEntity;
import net.minecraft.resource.Identifier;

public class SkeletonRenderer extends UndeadMobRenderer<SkeletonEntity> {
    private static final Identifier SKELETON_LOCATION = new Identifier("textures/entity/skeleton/skeleton.png");
    private static final Identifier WITHER_SKELETON_LOCATION = new Identifier("textures/entity/skeleton/wither_skeleton.png");

    public SkeletonRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher, new SkeletonModel(), 0.5F);
        this.addLayer(new ItemInHandLayer(this));
        this.addLayer(new ArmorLayer(this) {
            @Override
            protected void hideAll() {
                this.innerModel = new SkeletonModel(0.5F, true);
                this.outerModel = new SkeletonModel(1.0F, true);
            }
        });
    }

    protected void applyScale(SkeletonEntity skeletonEntity, float f) {
        if (skeletonEntity.getType() == 1) {
            GlStateManager.scalef(1.2F, 1.2F, 1.2F);
        }
    }

    @Override
    public void glTranslate() {
        GlStateManager.translatef(0.09375F, 0.1875F, 0.0F);
    }

    protected Identifier getTextureLocation(SkeletonEntity skeletonEntity) {
        return skeletonEntity.getType() == 1 ? WITHER_SKELETON_LOCATION : SKELETON_LOCATION;
    }
}
