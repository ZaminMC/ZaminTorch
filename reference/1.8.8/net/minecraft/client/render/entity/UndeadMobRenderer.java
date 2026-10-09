package net.minecraft.client.render.entity;

import net.minecraft.client.render.entity.layer.ItemInHandLayer;
import net.minecraft.client.render.entity.layer.WornSkullLayer;
import net.minecraft.client.render.model.entity.HumanoidModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.resource.Identifier;

public class UndeadMobRenderer<T extends MobEntity> extends MobRenderer<T> {
    private static final Identifier STEVE_LOCATION = new Identifier("textures/entity/steve.png");
    protected HumanoidModel model;
    protected float size;

    public UndeadMobRenderer(EntityRenderDispatcher dispatcher, HumanoidModel model, float shadowSize) {
        this(dispatcher, model, shadowSize, 1.0F);
        this.addLayer(new ItemInHandLayer(this));
    }

    public UndeadMobRenderer(EntityRenderDispatcher dispatcher, HumanoidModel model, float shadowSize, float size) {
        super(dispatcher, model, shadowSize);
        this.model = model;
        this.size = size;
        this.addLayer(new WornSkullLayer(model.head));
    }

    protected Identifier getTextureLocation(T mobEntity) {
        return STEVE_LOCATION;
    }

    @Override
    public void glTranslate() {
        GlStateManager.translatef(0.0F, 0.1875F, 0.0F);
    }
}
