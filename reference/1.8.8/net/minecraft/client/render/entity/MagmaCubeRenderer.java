package net.minecraft.client.render.entity;

import net.minecraft.client.render.model.entity.MagmaCubeModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.monster.MagmaCubeEntity;
import net.minecraft.resource.Identifier;

public class MagmaCubeRenderer extends MobRenderer<MagmaCubeEntity> {
    private static final Identifier MAGMA_CUBE_LOCATION = new Identifier("textures/entity/slime/magmacube.png");

    public MagmaCubeRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher, new MagmaCubeModel(), 0.25F);
    }

    protected Identifier getTextureLocation(MagmaCubeEntity magmaCubeEntity) {
        return MAGMA_CUBE_LOCATION;
    }

    protected void applyScale(MagmaCubeEntity magmaCubeEntity, float f) {
        int i = magmaCubeEntity.getSize();
        float fx = (magmaCubeEntity.lastStretch + (magmaCubeEntity.stretch - magmaCubeEntity.lastStretch) * f) / (i * 0.5F + 1.0F);
        float f1 = 1.0F / (fx + 1.0F);
        float f2 = i;
        GlStateManager.scalef(f1 * f2, 1.0F / f1 * f2, f1 * f2);
    }
}
