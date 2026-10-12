package net.minecraft.client.render.entity.layer;

import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.client.render.model.PlayerModelPart;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.util.math.MathHelper;

public class CapeLayer implements EntityRenderLayer<ClientPlayerEntity> {
    private final PlayerRenderer parent;

    public CapeLayer(PlayerRenderer parent) {
        this.parent = parent;
    }

    public void render(ClientPlayerEntity clientPlayerEntity, float f, float g, float h, float i, float j, float k, float l) {
        if (clientPlayerEntity.hasInfo()
            && !clientPlayerEntity.isInvisible()
            && clientPlayerEntity.isModelPartVisible(PlayerModelPart.CAPE)
            && clientPlayerEntity.getCapeTextureLocation() != null) {
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.parent.bindTexture(clientPlayerEntity.getCapeTextureLocation());
            GlStateManager.pushMatrix();
            GlStateManager.translatef(0.0F, 0.0F, 0.125F);
            double d0 = clientPlayerEntity.lastCapeX
                + (clientPlayerEntity.capeX - clientPlayerEntity.lastCapeX) * h
                - (clientPlayerEntity.lastX + (clientPlayerEntity.x - clientPlayerEntity.lastX) * h);
            double d1 = clientPlayerEntity.lastCapeY
                + (clientPlayerEntity.capeY - clientPlayerEntity.lastCapeY) * h
                - (clientPlayerEntity.lastY + (clientPlayerEntity.y - clientPlayerEntity.lastY) * h);
            double d2 = clientPlayerEntity.lastCapeZ
                + (clientPlayerEntity.capeZ - clientPlayerEntity.lastCapeZ) * h
                - (clientPlayerEntity.lastZ + (clientPlayerEntity.z - clientPlayerEntity.lastZ) * h);
            float fx = clientPlayerEntity.lastBodyYaw + (clientPlayerEntity.bodyYaw - clientPlayerEntity.lastBodyYaw) * h;
            double d3 = MathHelper.sin(fx * (float) Math.PI / 180.0F);
            double d4 = -MathHelper.cos(fx * (float) Math.PI / 180.0F);
            float f1 = (float)d1 * 10.0F;
            f1 = MathHelper.clamp(f1, -6.0F, 32.0F);
            float f2 = (float)(d0 * d3 + d2 * d4) * 100.0F;
            float f3 = (float)(d0 * d4 - d2 * d3) * 100.0F;
            if (f2 < 0.0F) {
                f2 = 0.0F;
            }

            float f4 = clientPlayerEntity.lastBob + (clientPlayerEntity.bob - clientPlayerEntity.lastBob) * h;
            f1 += MathHelper.sin((clientPlayerEntity.lastWalkDistance + (clientPlayerEntity.walkDistance - clientPlayerEntity.lastWalkDistance) * h) * 6.0F)
                * 32.0F
                * f4;
            if (clientPlayerEntity.isSneaking()) {
                f1 += 25.0F;
            }

            GlStateManager.rotatef(6.0F + f2 / 2.0F + f1, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotatef(f3 / 2.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.rotatef(-f3 / 2.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotatef(180.0F, 0.0F, 1.0F, 0.0F);
            this.parent.getModel().renderCape(0.0625F);
            GlStateManager.popMatrix();
        }
    }

    @Override
    public boolean colorsWhenDamaged() {
        return false;
    }
}
