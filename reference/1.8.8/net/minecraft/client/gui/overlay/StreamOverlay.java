package net.minecraft.client.gui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.resource.Identifier;

public class StreamOverlay {
    private static final Identifier STREAM_INDICATOR_TEXTURE = new Identifier("textures/gui/stream_indicator.png");
    private final Minecraft minecraft;
    private float f_3884854 = 1.0F;
    private int f_1320308 = 1;

    public StreamOverlay(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void render(int x, int y) {
        if (this.minecraft.getTwitchStream().isBroadcasting()) {
            GlStateManager.enableBlend();
            int i = this.minecraft.getTwitchStream().getViewerCount();
            if (i > 0) {
                String s = "" + i;
                int j = this.minecraft.textRenderer.getWidth(s);
                int k = 20;
                int l = x - j - 1;
                int i1 = y + 20 - 1;
                int j1 = x;
                int k1 = y + 20 + this.minecraft.textRenderer.fontHeight - 1;
                GlStateManager.disableTexture();
                Tesselator tesselator = Tesselator.getInstance();
                BufferBuilder bufferbuilder = tesselator.getBuffer();
                GlStateManager.color4f(0.0F, 0.0F, 0.0F, (0.65F + 0.35000002F * this.f_3884854) / 2.0F);
                bufferbuilder.begin(7, DefaultVertexFormat.POSITION);
                bufferbuilder.vertex(l, k1, 0.0).nextVertex();
                bufferbuilder.vertex(j1, k1, 0.0).nextVertex();
                bufferbuilder.vertex(j1, i1, 0.0).nextVertex();
                bufferbuilder.vertex(l, i1, 0.0).nextVertex();
                tesselator.end();
                GlStateManager.enableTexture();
                this.minecraft.textRenderer.draw(s, x - j, y + 20, 16777215);
            }

            this.m_4136843(x, y, this.m_2344031(), 0);
            this.m_4136843(x, y, this.m_2041700(), 17);
        }
    }

    private void m_4136843(int i, int j, int k, int l) {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 0.65F + 0.35000002F * this.f_3884854);
        this.minecraft.getTextureManager().bind(STREAM_INDICATOR_TEXTURE);
        float f = 150.0F;
        float f1 = 0.0F;
        float f2 = k * 0.015625F;
        float f3 = 1.0F;
        float f4 = (k + 16) * 0.015625F;
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(i - 16 - l, j + 16, f).texture(f1, f4).nextVertex();
        bufferbuilder.vertex(i - l, j + 16, f).texture(f3, f4).nextVertex();
        bufferbuilder.vertex(i - l, j + 0, f).texture(f3, f2).nextVertex();
        bufferbuilder.vertex(i - 16 - l, j + 0, f).texture(f1, f2).nextVertex();
        tesselator.end();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private int m_2344031() {
        return this.minecraft.getTwitchStream().isPaused() ? 16 : 0;
    }

    private int m_2041700() {
        return this.minecraft.getTwitchStream().m_1283038() ? 48 : 32;
    }

    public void m_6080915() {
        if (this.minecraft.getTwitchStream().isBroadcasting()) {
            this.f_3884854 = this.f_3884854 + 0.025F * this.f_1320308;
            if (this.f_3884854 < 0.0F) {
                this.f_1320308 *= -1;
                this.f_3884854 = 0.0F;
            } else if (this.f_3884854 > 1.0F) {
                this.f_1320308 *= -1;
                this.f_3884854 = 1.0F;
            }
        } else {
            this.f_3884854 = 1.0F;
            this.f_1320308 = 1;
        }
    }
}
