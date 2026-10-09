package net.minecraft.client.render.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.passive.animal.tameable.OcelotEntity;
import net.minecraft.resource.Identifier;

public class OcelotRenderer extends MobRenderer<OcelotEntity> {
    private static final Identifier BLACK_CAT_LOCATION = new Identifier("textures/entity/cat/black.png");
    private static final Identifier OCELOT_LOCATION = new Identifier("textures/entity/cat/ocelot.png");
    private static final Identifier RED_CAT_LOCATION = new Identifier("textures/entity/cat/red.png");
    private static final Identifier SIAMESE_CAT_LOCATION = new Identifier("textures/entity/cat/siamese.png");

    public OcelotRenderer(EntityRenderDispatcher entityRenderDispatcher, Model model, float f) {
        super(entityRenderDispatcher, model, f);
    }

    protected Identifier getTextureLocation(OcelotEntity ocelotEntity) {
        switch (ocelotEntity.getVariant()) {
            case 0:
            default:
                return OCELOT_LOCATION;
            case 1:
                return BLACK_CAT_LOCATION;
            case 2:
                return RED_CAT_LOCATION;
            case 3:
                return SIAMESE_CAT_LOCATION;
        }
    }

    protected void applyScale(OcelotEntity ocelotEntity, float f) {
        super.applyScale(ocelotEntity, f);
        if (ocelotEntity.isTamed()) {
            GlStateManager.scalef(0.8F, 0.8F, 0.8F);
        }
    }
}
