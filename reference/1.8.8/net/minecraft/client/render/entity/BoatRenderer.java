package net.minecraft.client.render.entity;

import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.entity.BoatModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;

public class BoatRenderer extends EntityRenderer<BoatEntity> {
    private static final Identifier BOAT_LOCATION = new Identifier("textures/entity/boat.png");
    protected Model model = new BoatModel();

    public BoatRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher);
        this.shadowSize = 0.5F;
    }

    public void render(BoatEntity boatEntity, double d, double e, double f, float g, float h) {
        GlStateManager.pushMatrix();
        GlStateManager.translatef((float)d, (float)e + 0.25F, (float)f);
        GlStateManager.rotatef(180.0F - g, 0.0F, 1.0F, 0.0F);
        float fx = boatEntity.getDamagedTimer() - h;
        float f1 = boatEntity.getDamage() - h;
        if (f1 < 0.0F) {
            f1 = 0.0F;
        }

        if (fx > 0.0F) {
            GlStateManager.rotatef(MathHelper.sin(fx) * fx * f1 / 10.0F * boatEntity.getDamagedSwingDirection(), 1.0F, 0.0F, 0.0F);
        }

        float f2 = 0.75F;
        GlStateManager.scalef(f2, f2, f2);
        GlStateManager.scalef(1.0F / f2, 1.0F / f2, 1.0F / f2);
        this.bindTexture(boatEntity);
        GlStateManager.scalef(-1.0F, -1.0F, 1.0F);
        this.model.render(boatEntity, 0.0F, 0.0F, -0.1F, 0.0F, 0.0F, 0.0625F);
        GlStateManager.popMatrix();
        super.render(boatEntity, d, e, f, g, h);
    }

    protected Identifier getTextureLocation(BoatEntity boatEntity) {
        return BOAT_LOCATION;
    }
}
