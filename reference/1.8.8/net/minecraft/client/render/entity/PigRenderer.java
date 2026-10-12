package net.minecraft.client.render.entity;

import net.minecraft.client.render.entity.layer.PigSaddleLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.entity.living.mob.passive.animal.PigEntity;
import net.minecraft.resource.Identifier;

public class PigRenderer extends MobRenderer<PigEntity> {
    private static final Identifier PIG_LOCATION = new Identifier("textures/entity/pig/pig.png");

    public PigRenderer(EntityRenderDispatcher entityRenderDispatcher, Model model, float f) {
        super(entityRenderDispatcher, model, f);
        this.addLayer(new PigSaddleLayer(this));
    }

    protected Identifier getTextureLocation(PigEntity pigEntity) {
        return PIG_LOCATION;
    }
}
