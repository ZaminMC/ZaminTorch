package net.minecraft.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.util.ProgressListener;

public class ProgressRenderer implements ProgressListener {
    private String stage = "";
    private Minecraft minecraft;
    private String title = "";
    private long lastTime = Minecraft.getTime();
    private boolean noAbort;
    private Window window;
    private RenderTarget target;

    public ProgressRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.window = new Window(minecraft);
        this.target = new RenderTarget(minecraft.width, minecraft.height, false);
        this.target.setFilterMode(9728);
    }

    @Override
    public void updateTitle(String title) {
        this.noAbort = false;
        this.start(title);
    }

    @Override
    public void progressStartNoAbort(String title) {
        this.noAbort = true;
        this.start(title);
    }

    private void start(String title) {
        this.title = title;
        if (!this.minecraft.running) {
            if (!this.noAbort) {
                throw new ProgressRenderError();
            }
        } else {
            GlStateManager.clear(256);
            GlStateManager.matrixMode(5889);
            GlStateManager.loadIdentity();
            if (GLX.useFbo()) {
                int i = this.window.getScale();
                GlStateManager.ortho(0.0, this.window.getWidth() * i, this.window.getHeight() * i, 0.0, 100.0, 300.0);
            } else {
                Window window = new Window(this.minecraft);
                GlStateManager.ortho(0.0, window.getScaledWidth(), window.getScaledHeight(), 0.0, 100.0, 300.0);
            }

            GlStateManager.matrixMode(5888);
            GlStateManager.loadIdentity();
            GlStateManager.translatef(0.0F, 0.0F, -200.0F);
        }
    }

    @Override
    public void progressStage(String stage) {
        if (!this.minecraft.running) {
            if (!this.noAbort) {
                throw new ProgressRenderError();
            }
        } else {
            this.lastTime = 0L;
            this.stage = stage;
            this.progressStagePercentage(-1);
            this.lastTime = 0L;
        }
    }

    @Override
    public void progressStagePercentage(int percentage) {
        if (!this.minecraft.running) {
            if (!this.noAbort) {
                throw new ProgressRenderError();
            }
        } else {
            long i = Minecraft.getTime();
            if (i - this.lastTime >= 100L) {
                this.lastTime = i;
                Window window = new Window(this.minecraft);
                int j = window.getScale();
                int k = window.getWidth();
                int l = window.getHeight();
                if (GLX.useFbo()) {
                    this.target.clear();
                } else {
                    GlStateManager.clear(256);
                }

                this.target.bindWrite(false);
                GlStateManager.matrixMode(5889);
                GlStateManager.loadIdentity();
                GlStateManager.ortho(0.0, window.getScaledWidth(), window.getScaledHeight(), 0.0, 100.0, 300.0);
                GlStateManager.matrixMode(5888);
                GlStateManager.loadIdentity();
                GlStateManager.translatef(0.0F, 0.0F, -200.0F);
                if (!GLX.useFbo()) {
                    GlStateManager.clear(16640);
                }

                Tesselator tesselator = Tesselator.getInstance();
                BufferBuilder bufferbuilder = tesselator.getBuffer();
                this.minecraft.getTextureManager().bind(GuiElement.BACKGROUND_LOCATION);
                float f = 32.0F;
                bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
                bufferbuilder.vertex(0.0, l, 0.0).texture(0.0, l / f).color(64, 64, 64, 255).nextVertex();
                bufferbuilder.vertex(k, l, 0.0).texture(k / f, l / f).color(64, 64, 64, 255).nextVertex();
                bufferbuilder.vertex(k, 0.0, 0.0).texture(k / f, 0.0).color(64, 64, 64, 255).nextVertex();
                bufferbuilder.vertex(0.0, 0.0, 0.0).texture(0.0, 0.0).color(64, 64, 64, 255).nextVertex();
                tesselator.end();
                if (percentage >= 0) {
                    int i1 = 100;
                    int j1 = 2;
                    int k1 = k / 2 - i1 / 2;
                    int l1 = l / 2 + 16;
                    GlStateManager.disableTexture();
                    bufferbuilder.begin(7, DefaultVertexFormat.POSITION_COLOR);
                    bufferbuilder.vertex(k1, l1, 0.0).color(128, 128, 128, 255).nextVertex();
                    bufferbuilder.vertex(k1, l1 + j1, 0.0).color(128, 128, 128, 255).nextVertex();
                    bufferbuilder.vertex(k1 + i1, l1 + j1, 0.0).color(128, 128, 128, 255).nextVertex();
                    bufferbuilder.vertex(k1 + i1, l1, 0.0).color(128, 128, 128, 255).nextVertex();
                    bufferbuilder.vertex(k1, l1, 0.0).color(128, 255, 128, 255).nextVertex();
                    bufferbuilder.vertex(k1, l1 + j1, 0.0).color(128, 255, 128, 255).nextVertex();
                    bufferbuilder.vertex(k1 + percentage, l1 + j1, 0.0).color(128, 255, 128, 255).nextVertex();
                    bufferbuilder.vertex(k1 + percentage, l1, 0.0).color(128, 255, 128, 255).nextVertex();
                    tesselator.end();
                    GlStateManager.enableTexture();
                }

                GlStateManager.enableBlend();
                GlStateManager.blendFuncSeparate(770, 771, 1, 0);
                this.minecraft.textRenderer.drawWithShadow(this.title, (k - this.minecraft.textRenderer.getWidth(this.title)) / 2, l / 2 - 4 - 16, 16777215);
                this.minecraft.textRenderer.drawWithShadow(this.stage, (k - this.minecraft.textRenderer.getWidth(this.stage)) / 2, l / 2 - 4 + 8, 16777215);
                this.target.unbindWrite();
                if (GLX.useFbo()) {
                    this.target.draw(k * j, l * j);
                }

                this.minecraft.updateDisplay();

                try {
                    Thread.yield();
                } catch (Exception exception) {
                }
            }
        }
    }

    @Override
    public void setDone() {
    }
}
