package net.minecraft.client.render.entity.layer;

import net.minecraft.client.render.entity.CreeperRenderer;
import net.minecraft.client.render.model.entity.CreeperModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.monster.CreeperEntity;
import net.minecraft.resource.Identifier;

public class CreeperChargeLayer implements EntityRenderLayer<CreeperEntity> {
    private static final Identifier CREEPER_CHARGE_LOCATION = new Identifier("textures/entity/creeper/creeper_armor.png");
    private final CreeperRenderer parent;
    private final CreeperModel model = new CreeperModel(2.0F);

    public CreeperChargeLayer(CreeperRenderer parent) {
        this.parent = parent;
    }

    public void render(CreeperEntity creeperEntity, float f, float g, float h, float i, float j, float k, float l) {
        if (creeperEntity.isCharged()) {
            boolean flag = creeperEntity.isInvisible();
            GlStateManager.depthMask(!flag);
            this.parent.bindTexture(CREEPER_CHARGE_LOCATION);
            GlStateManager.matrixMode(5890);
            GlStateManager.loadIdentity();
            float fx = creeperEntity.ticks + h;
            GlStateManager.translatef(fx * 0.01F, fx * 0.01F, 0.0F);
            GlStateManager.matrixMode(5888);
            GlStateManager.enableBlend();
            float f1 = 0.5F;
            GlStateManager.color4f(f1, f1, f1, 1.0F);
            GlStateManager.disableLighting();
            GlStateManager.blendFunc(1, 1);
            this.model.copyPropertiesFrom(this.parent.getModel());
            this.model.render(creeperEntity, f, g, i, j, k, l);
            GlStateManager.matrixMode(5890);
            GlStateManager.loadIdentity();
            GlStateManager.matrixMode(5888);
            GlStateManager.enableLighting();
            GlStateManager.disableBlend();
            GlStateManager.depthMask(flag);
        }
    }

    @Override
    public boolean colorsWhenDamaged() {
        return false;
    }
}
