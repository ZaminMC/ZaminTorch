package net.minecraft.client.render.entity.layer;

import net.minecraft.client.render.entity.SpiderRenderer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.monster.SpiderEntity;
import net.minecraft.resource.Identifier;

public class SpiderEyesLayer implements EntityRenderLayer<SpiderEntity> {
    private static final Identifier SPIDER_EYES_LOCATION = new Identifier("textures/entity/spider_eyes.png");
    private final SpiderRenderer parent;

    public SpiderEyesLayer(SpiderRenderer parent) {
        this.parent = parent;
    }

    public void render(SpiderEntity spiderEntity, float f, float g, float h, float i, float j, float k, float l) {
        this.parent.bindTexture(SPIDER_EYES_LOCATION);
        GlStateManager.enableBlend();
        GlStateManager.disableAlphaTest();
        GlStateManager.blendFunc(1, 1);
        if (spiderEntity.isInvisible()) {
            GlStateManager.depthMask(false);
        } else {
            GlStateManager.depthMask(true);
        }

        int ix = 61680;
        int jx = ix % 65536;
        int kx = ix / 65536;
        GLX.multiTexCoord2f(GLX.GL_TEXTURE1, jx / 1.0F, kx / 1.0F);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.parent.getModel().render(spiderEntity, f, g, i, j, k, l);
        ix = spiderEntity.getLightLevel(h);
        jx = ix % 65536;
        kx = ix / 65536;
        GLX.multiTexCoord2f(GLX.GL_TEXTURE1, jx / 1.0F, kx / 1.0F);
        this.parent.setLightColor(spiderEntity, h);
        GlStateManager.disableBlend();
        GlStateManager.enableAlphaTest();
    }

    @Override
    public boolean colorsWhenDamaged() {
        return false;
    }
}
