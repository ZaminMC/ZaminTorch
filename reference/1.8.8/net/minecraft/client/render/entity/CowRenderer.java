package net.minecraft.client.render.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.entity.living.mob.passive.animal.CowEntity;
import net.minecraft.resource.Identifier;

public class CowRenderer extends MobRenderer<CowEntity> {
    private static final Identifier COW_LOCATION = new Identifier("textures/entity/cow/cow.png");

    public CowRenderer(EntityRenderDispatcher entityRenderDispatcher, Model model, float f) {
        super(entityRenderDispatcher, model, f);
    }

    protected Identifier getTextureLocation(CowEntity cowEntity) {
        return COW_LOCATION;
    }
}
