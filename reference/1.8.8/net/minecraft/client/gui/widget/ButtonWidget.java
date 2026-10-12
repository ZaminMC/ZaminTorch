package net.minecraft.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.sound.instance.SimpleSoundInstance;
import net.minecraft.client.sound.system.SoundManager;
import net.minecraft.resource.Identifier;

public class ButtonWidget extends GuiElement {
    protected static final Identifier WIDGETS_LOCATION = new Identifier("textures/gui/widgets.png");
    protected int width = 200;
    protected int height = 20;
    public int x;
    public int y;
    public String message;
    public int id;
    public boolean active = true;
    public boolean visible = true;
    protected boolean hovered;

    public ButtonWidget(int id, int x, int y, String message) {
        this(id, x, y, 200, 20, message);
    }

    public ButtonWidget(int id, int x, int y, int width, int height, String message) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.message = message;
    }

    protected int getYImage(boolean hovered) {
        int i = 1;
        if (!this.active) {
            i = 0;
        } else if (hovered) {
            i = 2;
        }

        return i;
    }

    public void render(Minecraft minecraft, int mouseX, int mouseY) {
        if (this.visible) {
            TextRenderer textrenderer = minecraft.textRenderer;
            minecraft.getTextureManager().bind(WIDGETS_LOCATION);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
            int i = this.getYImage(this.hovered);
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            GlStateManager.blendFunc(770, 771);
            this.drawTexture(this.x, this.y, 0, 46 + i * 20, this.width / 2, this.height);
            this.drawTexture(this.x + this.width / 2, this.y, 200 - this.width / 2, 46 + i * 20, this.width / 2, this.height);
            this.renderBackground(minecraft, mouseX, mouseY);
            int j = 14737632;
            if (!this.active) {
                j = 10526880;
            } else if (this.hovered) {
                j = 16777120;
            }

            this.drawCenteredString(textrenderer, this.message, this.x + this.width / 2, this.y + (this.height - 8) / 2, j);
        }
    }

    protected void renderBackground(Minecraft minecraft, int mouseX, int mouseY) {
    }

    public void mouseReleased(int mouseX, int mouseY) {
    }

    public boolean mouseClicked(Minecraft minecraft, int mouseX, int mouseY) {
        return this.active && this.visible && mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
    }

    public boolean isHovered() {
        return this.hovered;
    }

    public void renderTooltip(int mouseX, int mouseY) {
    }

    public void playClickSound(SoundManager soundManager) {
        soundManager.play(SimpleSoundInstance.of(new Identifier("gui.button.press"), 1.0F));
    }

    public int getWidth() {
        return this.width;
    }

    public void setWidth(int width) {
        this.width = width;
    }
}
