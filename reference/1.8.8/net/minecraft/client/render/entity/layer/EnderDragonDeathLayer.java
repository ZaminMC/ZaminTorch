package net.minecraft.client.render.entity.layer;

import java.util.Random;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.living.mob.monster.boss.EnderDragonEntity;

public class EnderDragonDeathLayer implements EntityRenderLayer<EnderDragonEntity> {
    public void render(EnderDragonEntity enderDragonEntity, float f, float g, float h, float i, float j, float k, float l) {
        if (enderDragonEntity.ticksSinceDeath > 0) {
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tesselator.getBuffer();
            Lighting.turnOff();
            float fx = (enderDragonEntity.ticksSinceDeath + h) / 200.0F;
            float f1 = 0.0F;
            if (fx > 0.8F) {
                f1 = (fx - 0.8F) / 0.2F;
            }

            Random random = new Random(432L);
            GlStateManager.disableTexture();
            GlStateManager.shadeModel(7425);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 1);
            GlStateManager.disableAlphaTest();
            GlStateManager.enableCull();
            GlStateManager.depthMask(false);
            GlStateManager.pushMatrix();
            GlStateManager.translatef(0.0F, -1.0F, -2.0F);

            for (int ix = 0; ix < (fx + fx * fx) / 2.0F * 60.0F; ix++) {
                GlStateManager.rotatef(random.nextFloat() * 360.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotatef(random.nextFloat() * 360.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotatef(random.nextFloat() * 360.0F, 0.0F, 0.0F, 1.0F);
                GlStateManager.rotatef(random.nextFloat() * 360.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotatef(random.nextFloat() * 360.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotatef(random.nextFloat() * 360.0F + fx * 90.0F, 0.0F, 0.0F, 1.0F);
                float f2 = random.nextFloat() * 20.0F + 5.0F + f1 * 10.0F;
                float f3 = random.nextFloat() * 2.0F + 1.0F + f1 * 2.0F;
                bufferbuilder.begin(6, DefaultVertexFormat.POSITION_COLOR);
                bufferbuilder.vertex(0.0, 0.0, 0.0).color(255, 255, 255, (int)(255.0F * (1.0F - f1))).nextVertex();
                bufferbuilder.vertex(-0.866 * f3, f2, -0.5F * f3).color(255, 0, 255, 0).nextVertex();
                bufferbuilder.vertex(0.866 * f3, f2, -0.5F * f3).color(255, 0, 255, 0).nextVertex();
                bufferbuilder.vertex(0.0, f2, 1.0F * f3).color(255, 0, 255, 0).nextVertex();
                bufferbuilder.vertex(-0.866 * f3, f2, -0.5F * f3).color(255, 0, 255, 0).nextVertex();
                tesselator.end();
            }

            GlStateManager.popMatrix();
            GlStateManager.depthMask(true);
            GlStateManager.disableCull();
            GlStateManager.disableBlend();
            GlStateManager.shadeModel(7424);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableTexture();
            GlStateManager.enableAlphaTest();
            Lighting.turnOn();
        }
    }

    @Override
    public boolean colorsWhenDamaged() {
        return false;
    }
}
