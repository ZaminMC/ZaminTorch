package net.minecraft.client.render.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.entity.living.mob.passive.animal.ChickenEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;

public class ChickenRenderer extends MobRenderer<ChickenEntity> {
    private static final Identifier CHICKEN_LOCATION = new Identifier("textures/entity/chicken.png");

    public ChickenRenderer(EntityRenderDispatcher entityRenderDispatcher, Model model, float f) {
        super(entityRenderDispatcher, model, f);
    }

    protected Identifier getTextureLocation(ChickenEntity chickenEntity) {
        return CHICKEN_LOCATION;
    }

    protected float getBob(ChickenEntity chickenEntity, float f) {
        float fx = chickenEntity.lastFlapProgress + (chickenEntity.flapProgress - chickenEntity.lastFlapProgress) * f;
        float f1 = chickenEntity.lastFlapSpeed + (chickenEntity.flapSpeed - chickenEntity.lastFlapSpeed) * f;
        return (MathHelper.sin(fx) + 1.0F) * f1;
    }
}
