package net.minecraft.client.gui.widget;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.language.I18n;

public class LabelWidget extends GuiElement {
    protected int width = 200;
    protected int height = 20;
    public int x;
    public int y;
    private List<String> text;
    public int id;
    private boolean centered;
    public boolean visible = true;
    private boolean hasBorder;
    private int textColor;
    private int fillColor;
    private int lineColor1;
    private int lineColor2;
    private TextRenderer textRenderer;
    private int offset;

    public LabelWidget(TextRenderer textRenderer, int id, int x, int y, int width, int height, int textColor) {
        this.textRenderer = textRenderer;
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.text = Lists.newArrayList();
        this.centered = false;
        this.hasBorder = false;
        this.textColor = textColor;
        this.fillColor = -1;
        this.lineColor1 = -1;
        this.lineColor2 = -1;
        this.offset = 0;
    }

    public void add(String text) {
        this.text.add(I18n.translate(text));
    }

    public LabelWidget setCentered() {
        this.centered = true;
        return this;
    }

    public void render(Minecraft minecraft, int mouseX, int mouseY) {
        if (this.visible) {
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            this.renderBorder(minecraft, mouseX, mouseY);
            int i = this.y + this.height / 2 + this.offset / 2;
            int j = i - this.text.size() * 10 / 2;

            for (int k = 0; k < this.text.size(); k++) {
                if (this.centered) {
                    this.drawCenteredString(this.textRenderer, this.text.get(k), this.x + this.width / 2, j + k * 10, this.textColor);
                } else {
                    this.drawString(this.textRenderer, this.text.get(k), this.x, j + k * 10, this.textColor);
                }
            }
        }
    }

    protected void renderBorder(Minecraft minecraft, int mouseX, int mouseY) {
        if (this.hasBorder) {
            int i = this.width + this.offset * 2;
            int j = this.height + this.offset * 2;
            int k = this.x - this.offset;
            int l = this.y - this.offset;
            fill(k, l, k + i, l + j, this.fillColor);
            this.drawHorizontalLine(k, k + i, l, this.lineColor1);
            this.drawHorizontalLine(k, k + i, l + j, this.lineColor2);
            this.drawVerticalLine(k, l, l + j, this.lineColor1);
            this.drawVerticalLine(k + i, l, l + j, this.lineColor2);
        }
    }
}
