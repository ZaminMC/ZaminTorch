package net.minecraft.client.render.entity;

import net.minecraft.client.render.entity.layer.ArmorLayer;
import net.minecraft.client.render.entity.layer.ItemInHandLayer;
import net.minecraft.client.render.entity.layer.WornSkullLayer;
import net.minecraft.client.render.model.entity.ArmorStandArmorModel;
import net.minecraft.client.render.model.entity.ArmorStandModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.resource.Identifier;

public class ArmorStandRenderer extends LivingEntityRenderer<ArmorStandEntity> {
    public static final Identifier TEXTURE = new Identifier("textures/entity/armorstand/wood.png");

    public ArmorStandRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher, new ArmorStandModel(), 0.0F);
        ArmorLayer armorlayer = new ArmorLayer(this) {
            @Override
            protected void hideAll() {
                this.innerModel = new ArmorStandArmorModel(0.5F);
                this.outerModel = new ArmorStandArmorModel(1.0F);
            }
        };
        this.addLayer(armorlayer);
        this.addLayer(new ItemInHandLayer(this));
        this.addLayer(new WornSkullLayer(this.getModel().head));
    }

    protected Identifier getTextureLocation(ArmorStandEntity armorStandEntity) {
        return TEXTURE;
    }

    public ArmorStandModel getModel() {
        return (ArmorStandModel)super.getModel();
    }

    protected void applyRotation(ArmorStandEntity armorStandEntity, float f, float g, float h) {
        GlStateManager.rotatef(180.0F - g, 0.0F, 1.0F, 0.0F);
    }

    protected boolean shouldRenderNameTag(ArmorStandEntity armorStandEntity) {
        return armorStandEntity.isCustomNameVisible();
    }
}
