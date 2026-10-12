package net.minecraft.client.gui;

import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.resource.Identifier;

public class GuiElement {
    public static final Identifier BACKGROUND_LOCATION = new Identifier("textures/gui/options_background.png");
    public static final Identifier STATS_ICONS_LOCATION = new Identifier("textures/gui/container/stats_icons.png");
    public static final Identifier ICONS_LOCATION = new Identifier("textures/gui/icons.png");
    protected float drawOffset;

    protected void drawHorizontalLine(int x1, int x2, int y, int color) {
        if (x2 < x1) {
            int i = x1;
            x1 = x2;
            x2 = i;
        }

        fill(x1, y, x2 + 1, y + 1, color);
    }

    protected void drawVerticalLine(int x, int y1, int y2, int color) {
        if (y2 < y1) {
            int i = y1;
            y1 = y2;
            y2 = i;
        }

        fill(x, y1 + 1, x + 1, y2, color);
    }

    public static void fill(int x1, int y1, int x2, int y2, int color) {
        if (x1 < x2) {
            int i = x1;
            x1 = x2;
            x2 = i;
        }

        if (y1 < y2) {
            int j = y1;
            y1 = y2;
            y2 = j;
        }

        float f3 = (color >> 24 & 0xFF) / 255.0F;
        float f = (color >> 16 & 0xFF) / 255.0F;
        float f1 = (color >> 8 & 0xFF) / 255.0F;
        float f2 = (color & 0xFF) / 255.0F;
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color4f(f, f1, f2, f3);
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION);
        bufferbuilder.vertex(x1, y2, 0.0).nextVertex();
        bufferbuilder.vertex(x2, y2, 0.0).nextVertex();
        bufferbuilder.vertex(x2, y1, 0.0).nextVertex();
        bufferbuilder.vertex(x1, y1, 0.0).nextVertex();
        tesselator.end();
        GlStateManager.enableTexture();
        GlStateManager.disableBlend();
    }

    protected void fillGradient(int x1, int y1, int x2, int y2, int color1, int color2) {
        float f = (color1 >> 24 & 0xFF) / 255.0F;
        float f1 = (color1 >> 16 & 0xFF) / 255.0F;
        float f2 = (color1 >> 8 & 0xFF) / 255.0F;
        float f3 = (color1 & 0xFF) / 255.0F;
        float f4 = (color2 >> 24 & 0xFF) / 255.0F;
        float f5 = (color2 >> 16 & 0xFF) / 255.0F;
        float f6 = (color2 >> 8 & 0xFF) / 255.0F;
        float f7 = (color2 & 0xFF) / 255.0F;
        GlStateManager.disableTexture();
        GlStateManager.enableBlend();
        GlStateManager.disableAlphaTest();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_COLOR);
        bufferbuilder.vertex(x2, y1, this.drawOffset).color(f1, f2, f3, f).nextVertex();
        bufferbuilder.vertex(x1, y1, this.drawOffset).color(f1, f2, f3, f).nextVertex();
        bufferbuilder.vertex(x1, y2, this.drawOffset).color(f5, f6, f7, f4).nextVertex();
        bufferbuilder.vertex(x2, y2, this.drawOffset).color(f5, f6, f7, f4).nextVertex();
        tesselator.end();
        GlStateManager.shadeModel(7424);
        GlStateManager.disableBlend();
        GlStateManager.enableAlphaTest();
        GlStateManager.enableTexture();
    }

    public void drawCenteredString(TextRenderer textRenderer, String text, int centerX, int y, int color) {
        textRenderer.drawWithShadow(text, centerX - textRenderer.getWidth(text) / 2, y, color);
    }

    public void drawString(TextRenderer textRenderer, String text, int x, int y, int color) {
        textRenderer.drawWithShadow(text, x, y, color);
    }

    public void drawTexture(int x, int y, int u, int v, int width, int height) {
        float f = 0.00390625F;
        float f1 = 0.00390625F;
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(x + 0, y + height, this.drawOffset).texture((u + 0) * f, (v + height) * f1).nextVertex();
        bufferbuilder.vertex(x + width, y + height, this.drawOffset).texture((u + width) * f, (v + height) * f1).nextVertex();
        bufferbuilder.vertex(x + width, y + 0, this.drawOffset).texture((u + width) * f, (v + 0) * f1).nextVertex();
        bufferbuilder.vertex(x + 0, y + 0, this.drawOffset).texture((u + 0) * f, (v + 0) * f1).nextVertex();
        tesselator.end();
    }

    public void drawTexture(float x, float y, int u, int v, int width, int height) {
        float f = 0.00390625F;
        float f1 = 0.00390625F;
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(x + 0.0F, y + height, this.drawOffset).texture((u + 0) * f, (v + height) * f1).nextVertex();
        bufferbuilder.vertex(x + width, y + height, this.drawOffset).texture((u + width) * f, (v + height) * f1).nextVertex();
        bufferbuilder.vertex(x + width, y + 0.0F, this.drawOffset).texture((u + width) * f, (v + 0) * f1).nextVertex();
        bufferbuilder.vertex(x + 0.0F, y + 0.0F, this.drawOffset).texture((u + 0) * f, (v + 0) * f1).nextVertex();
        tesselator.end();
    }

    public void drawSprite(int x, int y, TextureAtlasSprite sprite, int width, int height) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(x + 0, y + height, this.drawOffset).texture(sprite.getUMin(), sprite.getVMax()).nextVertex();
        bufferbuilder.vertex(x + width, y + height, this.drawOffset).texture(sprite.getUMax(), sprite.getVMax()).nextVertex();
        bufferbuilder.vertex(x + width, y + 0, this.drawOffset).texture(sprite.getUMax(), sprite.getVMin()).nextVertex();
        bufferbuilder.vertex(x + 0, y + 0, this.drawOffset).texture(sprite.getUMin(), sprite.getVMin()).nextVertex();
        tesselator.end();
    }

    public static void drawTexture(int x, int y, float u, float v, int width, int height, float scaleU, float scaleV) {
        float f = 1.0F / scaleU;
        float f1 = 1.0F / scaleV;
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(x, y + height, 0.0).texture(u * f, (v + height) * f1).nextVertex();
        bufferbuilder.vertex(x + width, y + height, 0.0).texture((u + width) * f, (v + height) * f1).nextVertex();
        bufferbuilder.vertex(x + width, y, 0.0).texture((u + width) * f, v * f1).nextVertex();
        bufferbuilder.vertex(x, y, 0.0).texture(u * f, v * f1).nextVertex();
        tesselator.end();
    }

    public static void drawTexture(int x, int y, float u, float v, int textureWidth, int textureHeight, int width, int height, float scaleU, float scaleV) {
        float f = 1.0F / scaleU;
        float f1 = 1.0F / scaleV;
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(x, y + height, 0.0).texture(u * f, (v + textureHeight) * f1).nextVertex();
        bufferbuilder.vertex(x + width, y + height, 0.0).texture((u + textureWidth) * f, (v + textureHeight) * f1).nextVertex();
        bufferbuilder.vertex(x + width, y, 0.0).texture((u + textureWidth) * f, v * f1).nextVertex();
        bufferbuilder.vertex(x, y, 0.0).texture(u * f, v * f1).nextVertex();
        tesselator.end();
    }
}
