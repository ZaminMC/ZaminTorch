package net.minecraft.client.render.entity;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.client.render.entity.layer.ArmorLayer;
import net.minecraft.client.render.entity.layer.EntityRenderLayer;
import net.minecraft.client.render.entity.layer.ItemInHandLayer;
import net.minecraft.client.render.entity.layer.WornSkullLayer;
import net.minecraft.client.render.entity.layer.ZombieVillagerArmorLayer;
import net.minecraft.client.render.model.entity.HumanoidModel;
import net.minecraft.client.render.model.entity.ZombieModel;
import net.minecraft.client.render.model.entity.ZombieVillagerModel;
import net.minecraft.entity.living.mob.monster.ZombieEntity;
import net.minecraft.resource.Identifier;

public class ZombieRenderer extends UndeadMobRenderer<ZombieEntity> {
    private static final Identifier ZOMBIE_LOCATION = new Identifier("textures/entity/zombie/zombie.png");
    private static final Identifier ZOMBIE_VILLAGER_LOCATION = new Identifier("textures/entity/zombie/zombie_villager.png");
    private final HumanoidModel zombieModel;
    private final ZombieVillagerModel zombieVillagerModel;
    private final List<EntityRenderLayer<ZombieEntity>> zombieVillagerModel2;
    private final List<EntityRenderLayer<ZombieEntity>> zombieVillagerModel3;

    public ZombieRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher, new ZombieModel(), 0.5F, 1.0F);
        EntityRenderLayer entityrenderlayer = this.layers.get(0);
        this.zombieModel = this.model;
        this.zombieVillagerModel = new ZombieVillagerModel();
        this.addLayer(new ItemInHandLayer(this));
        ArmorLayer armorlayer = new ArmorLayer(this) {
            @Override
            protected void hideAll() {
                this.innerModel = new ZombieModel(0.5F, true);
                this.outerModel = new ZombieModel(1.0F, true);
            }
        };
        this.addLayer(armorlayer);
        this.zombieVillagerModel3 = Lists.newArrayList(this.layers);
        if (entityrenderlayer instanceof WornSkullLayer) {
            this.removeLayer(entityrenderlayer);
            this.addLayer(new WornSkullLayer(this.zombieVillagerModel.head));
        }

        this.removeLayer(armorlayer);
        this.addLayer(new ZombieVillagerArmorLayer(this));
        this.zombieVillagerModel2 = Lists.newArrayList(this.layers);
    }

    public void render(ZombieEntity zombieEntity, double d, double e, double f, float g, float h) {
        this.updateModels(zombieEntity);
        super.render(zombieEntity, d, e, f, g, h);
    }

    protected Identifier getTextureLocation(ZombieEntity zombieEntity) {
        return zombieEntity.isVillager() ? ZOMBIE_VILLAGER_LOCATION : ZOMBIE_LOCATION;
    }

    private void updateModels(ZombieEntity zombie) {
        if (zombie.isVillager()) {
            this.model = this.zombieVillagerModel;
            this.layers = this.zombieVillagerModel2;
        } else {
            this.model = this.zombieModel;
            this.layers = this.zombieVillagerModel3;
        }

        this.model = (HumanoidModel)this.model;
    }

    protected void applyRotation(ZombieEntity zombieEntity, float f, float g, float h) {
        if (zombieEntity.isConverting()) {
            g += (float)(Math.cos(zombieEntity.ticks * 3.25) * Math.PI * 0.25);
        }

        super.applyRotation(zombieEntity, f, g, h);
    }
}
