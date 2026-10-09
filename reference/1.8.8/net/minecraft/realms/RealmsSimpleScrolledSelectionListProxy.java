package net.minecraft.realms;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ListWidget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.util.math.MathHelper;

public class RealmsSimpleScrolledSelectionListProxy extends ListWidget {
    private final RealmsSimpleScrolledSelectionList delegate;

    public RealmsSimpleScrolledSelectionListProxy(RealmsSimpleScrolledSelectionList proxy, int width, int height, int minY, int maxY, int entryHeight) {
        super(Minecraft.getInstance(), width, height, minY, maxY, entryHeight);
        this.delegate = proxy;
    }

    @Override
    protected int size() {
        return this.delegate.getItemCount();
    }

    @Override
    protected void entryClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
        this.delegate.selectItem(index, doubleClick, mouseX, mouseY);
    }

    @Override
    protected boolean isEntrySelected(int index) {
        return this.delegate.isSelectedItem(index);
    }

    @Override
    protected void renderBackground() {
        this.delegate.renderBackground();
    }

    @Override
    protected void renderEntry(int index, int x, int y, int rowHeight, int mouseX, int mouseY) {
        this.delegate.renderItem(index, x, y, rowHeight, mouseX, mouseY);
    }

    public int getWidth() {
        return super.width;
    }

    public int getMouseY() {
        return super.mouseY;
    }

    public int getMouseX() {
        return super.mouseX;
    }

    @Override
    protected int getHeight() {
        return this.delegate.getMaxPosition();
    }

    @Override
    protected int getScrollbarPosition() {
        return this.delegate.getScrollbarPosition();
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        if (this.visible) {
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.renderBackground();
            int i = this.getScrollbarPosition();
            int j = i + 6;
            this.capScrolling();
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tesselator.getBuffer();
            int k = this.minX + this.width / 2 - this.getRowWidth() / 2 + 2;
            int l = this.minY + 4 - (int)this.scrollAmount;
            if (this.renderHeader) {
                this.renderHeader(k, l, tesselator);
            }

            this.renderList(k, l, mouseX, mouseY);
            GlStateManager.disableDepthTest();
            int i1 = 4;
            this.renderHoleBackground(0, this.minY, 255, 255);
            this.renderHoleBackground(this.maxY, this.height, 255, 255);
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 0, 1);
            GlStateManager.disableAlphaTest();
            GlStateManager.shadeModel(7425);
            GlStateManager.disableTexture();
            int j1 = this.getMaxScroll();
            if (j1 > 0) {
                int k1 = (this.maxY - this.minY) * (this.maxY - this.minY) / this.getHeight();
                k1 = MathHelper.clamp(k1, 32, this.maxY - this.minY - 8);
                int l1 = (int)this.scrollAmount * (this.maxY - this.minY - k1) / j1 + this.minY;
                if (l1 < this.minY) {
                    l1 = this.minY;
                }

                bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
                bufferbuilder.vertex(i, this.maxY, 0.0).texture(0.0, 1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(j, this.maxY, 0.0).texture(1.0, 1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(j, this.minY, 0.0).texture(1.0, 0.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(i, this.minY, 0.0).texture(0.0, 0.0).color(0, 0, 0, 255).nextVertex();
                tesselator.end();
                bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
                bufferbuilder.vertex(i, l1 + k1, 0.0).texture(0.0, 1.0).color(128, 128, 128, 255).nextVertex();
                bufferbuilder.vertex(j, l1 + k1, 0.0).texture(1.0, 1.0).color(128, 128, 128, 255).nextVertex();
                bufferbuilder.vertex(j, l1, 0.0).texture(1.0, 0.0).color(128, 128, 128, 255).nextVertex();
                bufferbuilder.vertex(i, l1, 0.0).texture(0.0, 0.0).color(128, 128, 128, 255).nextVertex();
                tesselator.end();
                bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
                bufferbuilder.vertex(i, l1 + k1 - 1, 0.0).texture(0.0, 1.0).color(192, 192, 192, 255).nextVertex();
                bufferbuilder.vertex(j - 1, l1 + k1 - 1, 0.0).texture(1.0, 1.0).color(192, 192, 192, 255).nextVertex();
                bufferbuilder.vertex(j - 1, l1, 0.0).texture(1.0, 0.0).color(192, 192, 192, 255).nextVertex();
                bufferbuilder.vertex(i, l1, 0.0).texture(0.0, 0.0).color(192, 192, 192, 255).nextVertex();
                tesselator.end();
            }

            this.renderDecorations(mouseX, mouseY);
            GlStateManager.enableTexture();
            GlStateManager.shadeModel(7424);
            GlStateManager.enableAlphaTest();
            GlStateManager.disableBlend();
        }
    }
}
