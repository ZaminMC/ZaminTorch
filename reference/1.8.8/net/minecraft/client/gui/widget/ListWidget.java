package net.minecraft.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Mouse;

public abstract class ListWidget {
    protected final Minecraft minecraft;
    protected int width;
    protected int height;
    protected int minY;
    protected int maxY;
    protected int maxX;
    protected int minX;
    protected final int entryHeight;
    private int upButtonId;
    private int downButtonId;
    protected int mouseX;
    protected int mouseY;
    protected boolean centerAlongY = true;
    protected int mouseYStart = -2;
    protected float scrollSpeedMultiplier;
    protected float scrollAmount;
    protected int pos = -1;
    protected long time;
    protected boolean visible = true;
    protected boolean renderSelectionHighlight = true;
    protected boolean renderHeader;
    protected int headerHeight;
    private boolean scrolling = true;

    public ListWidget(Minecraft minecraft, int width, int height, int minY, int maxY, int entryHeight) {
        this.minecraft = minecraft;
        this.width = width;
        this.height = height;
        this.minY = minY;
        this.maxY = maxY;
        this.entryHeight = entryHeight;
        this.minX = 0;
        this.maxX = width;
    }

    public void setBounds(int width, int height, int minY, int maxY) {
        this.width = width;
        this.height = height;
        this.minY = minY;
        this.maxY = maxY;
        this.minX = 0;
        this.maxX = width;
    }

    public void setRenderSelectionHighlight(boolean renderSelectionHighlight) {
        this.renderSelectionHighlight = renderSelectionHighlight;
    }

    protected void setHeader(boolean renderHeader, int headerHeight) {
        this.renderHeader = renderHeader;
        this.headerHeight = headerHeight;
        if (!renderHeader) {
            this.headerHeight = 0;
        }
    }

    protected abstract int size();

    protected abstract void entryClicked(int index, boolean doubleClick, int mouseX, int mouseY);

    protected abstract boolean isEntrySelected(int index);

    protected int getHeight() {
        return this.size() * this.entryHeight + this.headerHeight;
    }

    protected abstract void renderBackground();

    protected void renderEntryOutOfBounds(int index, int x, int y) {
    }

    protected abstract void renderEntry(int index, int x, int y, int rowHeight, int mouseX, int mouseY);

    protected void renderHeader(int x, int y, Tesselator tesselator) {
    }

    protected void headerClicked(int x, int y) {
    }

    protected void renderDecorations(int mouseX, int mouseY) {
    }

    public int getEntryAt(int x, int y) {
        int i = this.minX + this.width / 2 - this.getRowWidth() / 2;
        int j = this.minX + this.width / 2 + this.getRowWidth() / 2;
        int k = y - this.minY - this.headerHeight + (int)this.scrollAmount - 4;
        int l = k / this.entryHeight;
        return x < this.getScrollbarPosition() && x >= i && x <= j && l >= 0 && k >= 0 && l < this.size() ? l : -1;
    }

    public void setScrollButtonIds(int upButtonId, int downButtonId) {
        this.upButtonId = upButtonId;
        this.downButtonId = downButtonId;
    }

    /**
     * prevents you from scrolling out too far
     */
    protected void capScrolling() {
        this.scrollAmount = MathHelper.clamp(this.scrollAmount, 0.0F, this.getMaxScroll());
    }

    public int getMaxScroll() {
        return Math.max(0, this.getHeight() - (this.maxY - this.minY - 4));
    }

    public int getScrollAmount() {
        return (int)this.scrollAmount;
    }

    public boolean isMouseInList(int mouseY) {
        return mouseY >= this.minY && mouseY <= this.maxY && this.mouseX >= this.minX && this.mouseX <= this.maxX;
    }

    /**
     * scroll a given amount. A positive amount will scroll down and a negative one scroll up
     */
    public void scroll(int amount) {
        this.scrollAmount += amount;
        this.capScrolling();
        this.mouseYStart = -2;
    }

    public void buttonClicked(ButtonWidget button) {
        if (button.active) {
            if (button.id == this.upButtonId) {
                this.scrollAmount = this.scrollAmount - this.entryHeight * 2 / 3;
                this.mouseYStart = -2;
                this.capScrolling();
            } else if (button.id == this.downButtonId) {
                this.scrollAmount = this.scrollAmount + this.entryHeight * 2 / 3;
                this.mouseYStart = -2;
                this.capScrolling();
            }
        }
    }

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
            this.minecraft.getTextureManager().bind(GuiElement.BACKGROUND_LOCATION);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            float f = 32.0F;
            bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
            bufferbuilder.vertex(this.minX, this.maxY, 0.0)
                .texture(this.minX / f, (this.maxY + (int)this.scrollAmount) / f)
                .color(32, 32, 32, 255)
                .nextVertex();
            bufferbuilder.vertex(this.maxX, this.maxY, 0.0)
                .texture(this.maxX / f, (this.maxY + (int)this.scrollAmount) / f)
                .color(32, 32, 32, 255)
                .nextVertex();
            bufferbuilder.vertex(this.maxX, this.minY, 0.0)
                .texture(this.maxX / f, (this.minY + (int)this.scrollAmount) / f)
                .color(32, 32, 32, 255)
                .nextVertex();
            bufferbuilder.vertex(this.minX, this.minY, 0.0)
                .texture(this.minX / f, (this.minY + (int)this.scrollAmount) / f)
                .color(32, 32, 32, 255)
                .nextVertex();
            tesselator.end();
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
            bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
            bufferbuilder.vertex(this.minX, this.minY + i1, 0.0).texture(0.0, 1.0).color(0, 0, 0, 0).nextVertex();
            bufferbuilder.vertex(this.maxX, this.minY + i1, 0.0).texture(1.0, 1.0).color(0, 0, 0, 0).nextVertex();
            bufferbuilder.vertex(this.maxX, this.minY, 0.0).texture(1.0, 0.0).color(0, 0, 0, 255).nextVertex();
            bufferbuilder.vertex(this.minX, this.minY, 0.0).texture(0.0, 0.0).color(0, 0, 0, 255).nextVertex();
            tesselator.end();
            bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
            bufferbuilder.vertex(this.minX, this.maxY, 0.0).texture(0.0, 1.0).color(0, 0, 0, 255).nextVertex();
            bufferbuilder.vertex(this.maxX, this.maxY, 0.0).texture(1.0, 1.0).color(0, 0, 0, 255).nextVertex();
            bufferbuilder.vertex(this.maxX, this.maxY - i1, 0.0).texture(1.0, 0.0).color(0, 0, 0, 0).nextVertex();
            bufferbuilder.vertex(this.minX, this.maxY - i1, 0.0).texture(0.0, 0.0).color(0, 0, 0, 0).nextVertex();
            tesselator.end();
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

    public void handleMouse() {
        if (this.isMouseInList(this.mouseY)) {
            if (Mouse.getEventButton() == 0 && Mouse.getEventButtonState() && this.mouseY >= this.minY && this.mouseY <= this.maxY) {
                int i = (this.width - this.getRowWidth()) / 2;
                int j = (this.width + this.getRowWidth()) / 2;
                int k = this.mouseY - this.minY - this.headerHeight + (int)this.scrollAmount - 4;
                int l = k / this.entryHeight;
                if (l < this.size() && this.mouseX >= i && this.mouseX <= j && l >= 0 && k >= 0) {
                    this.entryClicked(l, false, this.mouseX, this.mouseY);
                    this.pos = l;
                } else if (this.mouseX >= i && this.mouseX <= j && k < 0) {
                    this.headerClicked(this.mouseX - i, this.mouseY - this.minY + (int)this.scrollAmount - 4);
                }
            }

            if (!Mouse.isButtonDown(0) || !this.isScrolling()) {
                this.mouseYStart = -1;
            } else if (this.mouseYStart == -1) {
                boolean flag1 = true;
                if (this.mouseY >= this.minY && this.mouseY <= this.maxY) {
                    int j2 = (this.width - this.getRowWidth()) / 2;
                    int k2 = (this.width + this.getRowWidth()) / 2;
                    int l2 = this.mouseY - this.minY - this.headerHeight + (int)this.scrollAmount - 4;
                    int i1 = l2 / this.entryHeight;
                    if (i1 < this.size() && this.mouseX >= j2 && this.mouseX <= k2 && i1 >= 0 && l2 >= 0) {
                        boolean flag = i1 == this.pos && Minecraft.getTime() - this.time < 250L;
                        this.entryClicked(i1, flag, this.mouseX, this.mouseY);
                        this.pos = i1;
                        this.time = Minecraft.getTime();
                    } else if (this.mouseX >= j2 && this.mouseX <= k2 && l2 < 0) {
                        this.headerClicked(this.mouseX - j2, this.mouseY - this.minY + (int)this.scrollAmount - 4);
                        flag1 = false;
                    }

                    int i3 = this.getScrollbarPosition();
                    int j1 = i3 + 6;
                    if (this.mouseX >= i3 && this.mouseX <= j1) {
                        this.scrollSpeedMultiplier = -1.0F;
                        int k1 = this.getMaxScroll();
                        if (k1 < 1) {
                            k1 = 1;
                        }

                        int l1 = (int)((float)((this.maxY - this.minY) * (this.maxY - this.minY)) / this.getHeight());
                        l1 = MathHelper.clamp(l1, 32, this.maxY - this.minY - 8);
                        this.scrollSpeedMultiplier = this.scrollSpeedMultiplier / ((float)(this.maxY - this.minY - l1) / k1);
                    } else {
                        this.scrollSpeedMultiplier = 1.0F;
                    }

                    if (flag1) {
                        this.mouseYStart = this.mouseY;
                    } else {
                        this.mouseYStart = -2;
                    }
                } else {
                    this.mouseYStart = -2;
                }
            } else if (this.mouseYStart >= 0) {
                this.scrollAmount = this.scrollAmount - (this.mouseY - this.mouseYStart) * this.scrollSpeedMultiplier;
                this.mouseYStart = this.mouseY;
            }

            int i2 = Mouse.getEventDWheel();
            if (i2 != 0) {
                if (i2 > 0) {
                    i2 = -1;
                } else if (i2 < 0) {
                    i2 = 1;
                }

                this.scrollAmount = this.scrollAmount + i2 * this.entryHeight / 2;
            }
        }
    }

    public void setScrolling(boolean scrolling) {
        this.scrolling = scrolling;
    }

    public boolean isScrolling() {
        return this.scrolling;
    }

    public int getRowWidth() {
        return 220;
    }

    protected void renderList(int x, int y, int mouseX, int mouseY) {
        int i = this.size();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();

        for (int j = 0; j < i; j++) {
            int k = y + j * this.entryHeight + this.headerHeight;
            int l = this.entryHeight - 4;
            if (k > this.maxY || k + l < this.minY) {
                this.renderEntryOutOfBounds(j, x, k);
            }

            if (this.renderSelectionHighlight && this.isEntrySelected(j)) {
                int i1 = this.minX + (this.width / 2 - this.getRowWidth() / 2);
                int j1 = this.minX + this.width / 2 + this.getRowWidth() / 2;
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.disableTexture();
                bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
                bufferbuilder.vertex(i1, k + l + 2, 0.0).texture(0.0, 1.0).color(128, 128, 128, 255).nextVertex();
                bufferbuilder.vertex(j1, k + l + 2, 0.0).texture(1.0, 1.0).color(128, 128, 128, 255).nextVertex();
                bufferbuilder.vertex(j1, k - 2, 0.0).texture(1.0, 0.0).color(128, 128, 128, 255).nextVertex();
                bufferbuilder.vertex(i1, k - 2, 0.0).texture(0.0, 0.0).color(128, 128, 128, 255).nextVertex();
                bufferbuilder.vertex(i1 + 1, k + l + 1, 0.0).texture(0.0, 1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(j1 - 1, k + l + 1, 0.0).texture(1.0, 1.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(j1 - 1, k - 1, 0.0).texture(1.0, 0.0).color(0, 0, 0, 255).nextVertex();
                bufferbuilder.vertex(i1 + 1, k - 1, 0.0).texture(0.0, 0.0).color(0, 0, 0, 255).nextVertex();
                tesselator.end();
                GlStateManager.enableTexture();
            }

            this.renderEntry(j, x, k, l, mouseX, mouseY);
        }
    }

    protected int getScrollbarPosition() {
        return this.width / 2 + 124;
    }

    protected void renderHoleBackground(int top, int bottom, int topAlpha, int bottomAlpha) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        this.minecraft.getTextureManager().bind(GuiElement.BACKGROUND_LOCATION);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        float f = 32.0F;
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
        bufferbuilder.vertex(this.minX, bottom, 0.0).texture(0.0, bottom / 32.0F).color(64, 64, 64, bottomAlpha).nextVertex();
        bufferbuilder.vertex(this.minX + this.width, bottom, 0.0).texture(this.width / 32.0F, bottom / 32.0F).color(64, 64, 64, bottomAlpha).nextVertex();
        bufferbuilder.vertex(this.minX + this.width, top, 0.0).texture(this.width / 32.0F, top / 32.0F).color(64, 64, 64, topAlpha).nextVertex();
        bufferbuilder.vertex(this.minX, top, 0.0).texture(0.0, top / 32.0F).color(64, 64, 64, topAlpha).nextVertex();
        tesselator.end();
    }

    public void setX(int minX) {
        this.minX = minX;
        this.maxX = minX + this.width;
    }

    public int getEntryHeight() {
        return this.entryHeight;
    }
}
