package net.minecraft.client.render.entity.layer;

import net.minecraft.client.render.entity.EnderDragonRenderer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.monster.boss.EnderDragonEntity;
import net.minecraft.resource.Identifier;

public class EnderDragonEyesLayer implements EntityRenderLayer<EnderDragonEntity> {
    private static final Identifier DRAGON_EYES_LOCATION = new Identifier("textures/entity/enderdragon/dragon_eyes.png");
    private final EnderDragonRenderer parent;

    public EnderDragonEyesLayer(EnderDragonRenderer parent) {
        this.parent = parent;
    }

    public void render(EnderDragonEntity enderDragonEntity, float f, float g, float h, float i, float j, float k, float l) {
        this.parent.bindTexture(DRAGON_EYES_LOCATION);
        GlStateManager.enableBlend();
        GlStateManager.disableAlphaTest();
        GlStateManager.blendFunc(1, 1);
        GlStateManager.disableLighting();
        GlStateManager.depthFunc(514);
        int ix = 61680;
        int jx = ix % 65536;
        int kx = ix / 65536;
        GLX.multiTexCoord2f(GLX.GL_TEXTURE1, jx / 1.0F, kx / 1.0F);
        GlStateManager.enableLighting();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.parent.getModel().render(enderDragonEntity, f, g, i, j, k, l);
        this.parent.setLightColor(enderDragonEntity, h);
        GlStateManager.disableBlend();
        GlStateManager.enableAlphaTest();
        GlStateManager.depthFunc(515);
    }

    @Override
    public boolean colorsWhenDamaged() {
        return false;
    }
}
