package net.minecraft.client.render.entity.layer;

import net.minecraft.client.render.entity.WitherRenderer;
import net.minecraft.client.render.model.entity.WitherModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.monster.boss.WitherEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;

public class WitherChargeLayer implements EntityRenderLayer<WitherEntity> {
    private static final Identifier WITHER_CHARGE_LOCATION = new Identifier("textures/entity/wither/wither_armor.png");
    private final WitherRenderer parent;
    private final WitherModel model = new WitherModel(0.5F);

    public WitherChargeLayer(WitherRenderer parent) {
        this.parent = parent;
    }

    public void render(WitherEntity witherEntity, float f, float g, float h, float i, float j, float k, float l) {
        if (witherEntity.isAtHalfHealth()) {
            GlStateManager.depthMask(!witherEntity.isInvisible());
            this.parent.bindTexture(WITHER_CHARGE_LOCATION);
            GlStateManager.matrixMode(5890);
            GlStateManager.loadIdentity();
            float fx = witherEntity.ticks + h;
            float f1 = MathHelper.cos(fx * 0.02F) * 3.0F;
            float f2 = fx * 0.01F;
            GlStateManager.translatef(f1, f2, 0.0F);
            GlStateManager.matrixMode(5888);
            GlStateManager.enableBlend();
            float f3 = 0.5F;
            GlStateManager.color4f(f3, f3, f3, 1.0F);
            GlStateManager.disableLighting();
            GlStateManager.blendFunc(1, 1);
            this.model.prepare(witherEntity, f, g, h);
            this.model.copyPropertiesFrom(this.parent.getModel());
            this.model.render(witherEntity, f, g, i, j, k, l);
            GlStateManager.matrixMode(5890);
            GlStateManager.loadIdentity();
            GlStateManager.matrixMode(5888);
            GlStateManager.enableLighting();
            GlStateManager.disableBlend();
        }
    }

    @Override
    public boolean colorsWhenDamaged() {
        return false;
    }
}
