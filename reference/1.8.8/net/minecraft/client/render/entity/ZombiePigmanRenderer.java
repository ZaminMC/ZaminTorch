package net.minecraft.client.render.entity;

import net.minecraft.client.render.entity.layer.ArmorLayer;
import net.minecraft.client.render.entity.layer.ItemInHandLayer;
import net.minecraft.client.render.model.entity.ZombieModel;
import net.minecraft.entity.living.mob.monster.ZombiePigmanEntity;
import net.minecraft.resource.Identifier;

public class ZombiePigmanRenderer extends UndeadMobRenderer<ZombiePigmanEntity> {
    private static final Identifier ZOMBIE_PIGMAN_LOCATION = new Identifier("textures/entity/zombie_pigman.png");

    public ZombiePigmanRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher, new ZombieModel(), 0.5F, 1.0F);
        this.addLayer(new ItemInHandLayer(this));
        this.addLayer(new ArmorLayer(this) {
            @Override
            protected void hideAll() {
                this.innerModel = new ZombieModel(0.5F, true);
                this.outerModel = new ZombieModel(1.0F, true);
            }
        });
    }

    protected Identifier getTextureLocation(ZombiePigmanEntity zombiePigmanEntity) {
        return ZOMBIE_PIGMAN_LOCATION;
    }
}
