package net.minecraft.client.render.entity;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.client.render.entity.layer.EndermanCarriedBlockLayer;
import net.minecraft.client.render.entity.layer.EndermanEyesLayer;
import net.minecraft.client.render.model.entity.EndermanModel;
import net.minecraft.entity.living.mob.monster.EndermanEntity;
import net.minecraft.resource.Identifier;

public class EndermanRenderer extends MobRenderer<EndermanEntity> {
    private static final Identifier ENDERMAN_LOCATION = new Identifier("textures/entity/enderman/enderman.png");
    private EndermanModel model;
    private Random random = new Random();

    public EndermanRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher, new EndermanModel(0.0F), 0.5F);
        this.model = (EndermanModel)super.model;
        this.addLayer(new EndermanEyesLayer(this));
        this.addLayer(new EndermanCarriedBlockLayer(this));
    }

    public void render(EndermanEntity endermanEntity, double d, double e, double f, float g, float h) {
        this.model.carryingBlock = endermanEntity.getCarriedBlock().getBlock().getMaterial() != Material.AIR;
        this.model.angry = endermanEntity.isAngry();
        if (endermanEntity.isAngry()) {
            double d0 = 0.02;
            d += this.random.nextGaussian() * d0;
            f += this.random.nextGaussian() * d0;
        }

        super.render(endermanEntity, d, e, f, g, h);
    }

    protected Identifier getTextureLocation(EndermanEntity endermanEntity) {
        return ENDERMAN_LOCATION;
    }
}
