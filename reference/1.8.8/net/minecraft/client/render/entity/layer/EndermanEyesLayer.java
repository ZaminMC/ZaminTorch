package net.minecraft.client.render.entity.layer;

import net.minecraft.client.render.entity.EndermanRenderer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.monster.EndermanEntity;
import net.minecraft.resource.Identifier;

public class EndermanEyesLayer implements EntityRenderLayer<EndermanEntity> {
    private static final Identifier ENDERMAN_EYES_LOCATION = new Identifier("textures/entity/enderman/enderman_eyes.png");
    private final EndermanRenderer parent;

    public EndermanEyesLayer(EndermanRenderer parent) {
        this.parent = parent;
    }

    public void render(EndermanEntity endermanEntity, float f, float g, float h, float i, float j, float k, float l) {
        this.parent.bindTexture(ENDERMAN_EYES_LOCATION);
        GlStateManager.enableBlend();
        GlStateManager.disableAlphaTest();
        GlStateManager.blendFunc(1, 1);
        GlStateManager.disableLighting();
        GlStateManager.depthMask(!endermanEntity.isInvisible());
        int ix = 61680;
        int jx = ix % 65536;
        int kx = ix / 65536;
        GLX.multiTexCoord2f(GLX.GL_TEXTURE1, jx / 1.0F, kx / 1.0F);
        GlStateManager.enableLighting();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.parent.getModel().render(endermanEntity, f, g, i, j, k, l);
        this.parent.setLightColor(endermanEntity, h);
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableAlphaTest();
    }

    @Override
    public boolean colorsWhenDamaged() {
        return false;
    }
}
