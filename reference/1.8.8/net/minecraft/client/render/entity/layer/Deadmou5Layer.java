package net.minecraft.client.render.entity.layer;

import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.client.render.platform.GlStateManager;

public class Deadmou5Layer implements EntityRenderLayer<ClientPlayerEntity> {
    private final PlayerRenderer parent;

    public Deadmou5Layer(PlayerRenderer parent) {
        this.parent = parent;
    }

    public void render(ClientPlayerEntity clientPlayerEntity, float f, float g, float h, float i, float j, float k, float l) {
        if (clientPlayerEntity.getName().equals("deadmau5") && clientPlayerEntity.hasSkinTexture() && !clientPlayerEntity.isInvisible()) {
            this.parent.bindTexture(clientPlayerEntity.getSkinTextureLocation());

            for (int ix = 0; ix < 2; ix++) {
                float fx = clientPlayerEntity.lastYaw
                    + (clientPlayerEntity.yaw - clientPlayerEntity.lastYaw) * h
                    - (clientPlayerEntity.lastBodyYaw + (clientPlayerEntity.bodyYaw - clientPlayerEntity.lastBodyYaw) * h);
                float f1 = clientPlayerEntity.lastPitch + (clientPlayerEntity.pitch - clientPlayerEntity.lastPitch) * h;
                GlStateManager.pushMatrix();
                GlStateManager.rotatef(fx, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotatef(f1, 1.0F, 0.0F, 0.0F);
                GlStateManager.translatef(0.375F * (ix * 2 - 1), 0.0F, 0.0F);
                GlStateManager.translatef(0.0F, -0.375F, 0.0F);
                GlStateManager.rotatef(-f1, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotatef(-fx, 0.0F, 1.0F, 0.0F);
                float f2 = 1.3333334F;
                GlStateManager.scalef(f2, f2, f2);
                this.parent.getModel().renderEars(0.0625F);
                GlStateManager.popMatrix();
            }
        }
    }

    @Override
    public boolean colorsWhenDamaged() {
        return true;
    }
}
