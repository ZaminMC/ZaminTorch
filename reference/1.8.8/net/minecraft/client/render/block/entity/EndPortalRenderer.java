package net.minecraft.client.render.block.entity;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.util.Random;
import net.minecraft.block.entity.EndPortalBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.MemoryTracker;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.resource.Identifier;

public class EndPortalRenderer extends BlockEntityRenderer<EndPortalBlockEntity> {
    private static final Identifier END_SKY_LOCATION = new Identifier("textures/environment/end_sky.png");
    private static final Identifier END_PORTAL_LOCATION = new Identifier("textures/entity/end_portal.png");
    private static final Random RANDOM = new Random(31100L);
    FloatBuffer buffer = MemoryTracker.createFloatBuffer(16);

    public void render(EndPortalBlockEntity endPortalBlockEntity, double d, double e, double f, float g, int i) {
        float fx = (float)this.dispatcher.cameraX;
        float f1 = (float)this.dispatcher.cameraY;
        float f2 = (float)this.dispatcher.cameraZ;
        GlStateManager.disableLighting();
        RANDOM.setSeed(31100L);
        float f3 = 0.75F;

        for (int ix = 0; ix < 16; ix++) {
            GlStateManager.pushMatrix();
            float f4 = 16 - ix;
            float f5 = 0.0625F;
            float f6 = 1.0F / (f4 + 1.0F);
            if (ix == 0) {
                this.bindTexture(END_SKY_LOCATION);
                f6 = 0.1F;
                f4 = 65.0F;
                f5 = 0.125F;
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(770, 771);
            }

            if (ix >= 1) {
                this.bindTexture(END_PORTAL_LOCATION);
            }

            if (ix == 1) {
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(1, 1);
                f5 = 0.5F;
            }

            float f7 = (float)(-(e + f3));
            float f8 = f7 + (float)Camera.offset().y;
            float f9 = f7 + f4 + (float)Camera.offset().y;
            float f10 = f8 / f9;
            f10 = (float)(e + f3) + f10;
            GlStateManager.translatef(fx, f10, f2);
            GlStateManager.texGenMode(GlStateManager.TexGenMode.S, 9217);
            GlStateManager.texGenMode(GlStateManager.TexGenMode.T, 9217);
            GlStateManager.texGenMode(GlStateManager.TexGenMode.R, 9217);
            GlStateManager.texGenMode(GlStateManager.TexGenMode.Q, 9216);
            GlStateManager.texGenParam(GlStateManager.TexGenMode.S, 9473, this.getBuffer(1.0F, 0.0F, 0.0F, 0.0F));
            GlStateManager.texGenParam(GlStateManager.TexGenMode.T, 9473, this.getBuffer(0.0F, 0.0F, 1.0F, 0.0F));
            GlStateManager.texGenParam(GlStateManager.TexGenMode.R, 9473, this.getBuffer(0.0F, 0.0F, 0.0F, 1.0F));
            GlStateManager.texGenParam(GlStateManager.TexGenMode.Q, 9474, this.getBuffer(0.0F, 1.0F, 0.0F, 0.0F));
            GlStateManager.enableTexGen(GlStateManager.TexGenMode.S);
            GlStateManager.enableTexGen(GlStateManager.TexGenMode.T);
            GlStateManager.enableTexGen(GlStateManager.TexGenMode.R);
            GlStateManager.enableTexGen(GlStateManager.TexGenMode.Q);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(5890);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.translatef(0.0F, (float)(Minecraft.getTime() % 700000L) / 700000.0F, 0.0F);
            GlStateManager.scalef(f5, f5, f5);
            GlStateManager.translatef(0.5F, 0.5F, 0.0F);
            GlStateManager.rotatef((ix * ix * 4321 + ix * 9) * 2.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.translatef(-0.5F, -0.5F, 0.0F);
            GlStateManager.translatef(-fx, -f2, -f1);
            f8 = f7 + (float)Camera.offset().y;
            GlStateManager.translatef((float)Camera.offset().x * f4 / f8, (float)Camera.offset().z * f4 / f8, -f1);
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tesselator.getBuffer();
            bufferbuilder.begin(7, DefaultVertexFormat.POSITION_COLOR);
            float f11 = (RANDOM.nextFloat() * 0.5F + 0.1F) * f6;
            float f12 = (RANDOM.nextFloat() * 0.5F + 0.4F) * f6;
            float f13 = (RANDOM.nextFloat() * 0.5F + 0.5F) * f6;
            if (ix == 0) {
                f11 = f12 = f13 = 1.0F * f6;
            }

            bufferbuilder.vertex(d, e + f3, f).color(f11, f12, f13, 1.0F).nextVertex();
            bufferbuilder.vertex(d, e + f3, f + 1.0).color(f11, f12, f13, 1.0F).nextVertex();
            bufferbuilder.vertex(d + 1.0, e + f3, f + 1.0).color(f11, f12, f13, 1.0F).nextVertex();
            bufferbuilder.vertex(d + 1.0, e + f3, f).color(f11, f12, f13, 1.0F).nextVertex();
            tesselator.end();
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(5888);
            this.bindTexture(END_SKY_LOCATION);
        }

        GlStateManager.disableBlend();
        GlStateManager.disableTexGen(GlStateManager.TexGenMode.S);
        GlStateManager.disableTexGen(GlStateManager.TexGenMode.T);
        GlStateManager.disableTexGen(GlStateManager.TexGenMode.R);
        GlStateManager.disableTexGen(GlStateManager.TexGenMode.Q);
        GlStateManager.enableLighting();
    }

    private FloatBuffer getBuffer(float f1, float f2, float f3, float f4) {
        ((Buffer)this.buffer).clear();
        this.buffer.put(f1).put(f2).put(f3).put(f4);
        ((Buffer)this.buffer).flip();
        return this.buffer;
    }
}
