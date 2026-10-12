package net.minecraft.client.render.entity;

import net.minecraft.client.render.entity.layer.MushroomLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.entity.living.mob.passive.animal.MooshroomEntity;
import net.minecraft.resource.Identifier;

public class MooshroomRenderer extends MobRenderer<MooshroomEntity> {
    private static final Identifier MOOSHROOM_LOCATION = new Identifier("textures/entity/cow/mooshroom.png");

    public MooshroomRenderer(EntityRenderDispatcher entityRenderDispatcher, Model model, float f) {
        super(entityRenderDispatcher, model, f);
        this.addLayer(new MushroomLayer(this));
    }

    protected Identifier getTextureLocation(MooshroomEntity mooshroomEntity) {
        return MOOSHROOM_LOCATION;
    }
}
