package net.minecraft.client.render.entity;

import net.minecraft.client.render.entity.layer.SheepFurLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.entity.living.mob.passive.animal.SheepEntity;
import net.minecraft.resource.Identifier;

public class SheepRenderer extends MobRenderer<SheepEntity> {
    private static final Identifier SHEEP_LOCATION = new Identifier("textures/entity/sheep/sheep.png");

    public SheepRenderer(EntityRenderDispatcher entityRenderDispatcher, Model model, float f) {
        super(entityRenderDispatcher, model, f);
        this.addLayer(new SheepFurLayer(this));
    }

    protected Identifier getTextureLocation(SheepEntity sheepEntity) {
        return SHEEP_LOCATION;
    }
}
