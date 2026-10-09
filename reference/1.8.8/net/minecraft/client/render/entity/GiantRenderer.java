package net.minecraft.client.render.entity;

import net.minecraft.client.render.entity.layer.ArmorLayer;
import net.minecraft.client.render.entity.layer.ItemInHandLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.entity.ZombieModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.monster.GiantEntity;
import net.minecraft.resource.Identifier;

public class GiantRenderer extends MobRenderer<GiantEntity> {
    private static final Identifier ZOMBIE_LOCATION = new Identifier("textures/entity/zombie/zombie.png");
    private float scale;

    public GiantRenderer(EntityRenderDispatcher model, Model shadowSize, float size, float f) {
        super(model, shadowSize, size * f);
        this.scale = f;
        this.addLayer(new ItemInHandLayer(this));
        this.addLayer(new ArmorLayer(this) {
            @Override
            protected void hideAll() {
                this.innerModel = new ZombieModel(0.5F, true);
                this.outerModel = new ZombieModel(1.0F, true);
            }
        });
    }

    @Override
    public void glTranslate() {
        GlStateManager.translatef(0.0F, 0.1875F, 0.0F);
    }

    protected void applyScale(GiantEntity giantEntity, float f) {
        GlStateManager.scalef(this.scale, this.scale, this.scale);
    }

    protected Identifier getTextureLocation(GiantEntity giantEntity) {
        return ZOMBIE_LOCATION;
    }
}
