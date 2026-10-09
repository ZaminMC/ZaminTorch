package net.minecraft.client.render.entity;

import net.minecraft.client.render.model.entity.LeashModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.decoration.LeadKnotEntity;
import net.minecraft.resource.Identifier;

public class LeadKnotRenderer extends EntityRenderer<LeadKnotEntity> {
    private static final Identifier LEAD_KNOT_LOCATION = new Identifier("textures/entity/lead_knot.png");
    private LeashModel model = new LeashModel();

    public LeadKnotRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher);
    }

    public void render(LeadKnotEntity leadKnotEntity, double d, double e, double f, float g, float h) {
        GlStateManager.pushMatrix();
        GlStateManager.disableCull();
        GlStateManager.translatef((float)d, (float)e, (float)f);
        float fx = 0.0625F;
        GlStateManager.enableRescaleNormal();
        GlStateManager.scalef(-1.0F, -1.0F, 1.0F);
        GlStateManager.enableAlphaTest();
        this.bindTexture(leadKnotEntity);
        this.model.render(leadKnotEntity, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, fx);
        GlStateManager.popMatrix();
        super.render(leadKnotEntity, d, e, f, g, h);
    }

    protected Identifier getTextureLocation(LeadKnotEntity leadKnotEntity) {
        return LEAD_KNOT_LOCATION;
    }
}
