package net.minecraft.realms;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;

public class RealmsButtonProxy extends ButtonWidget {
    private RealmsButton delegate;

    public RealmsButtonProxy(RealmsButton button, int id, int x, int y, String message) {
        super(id, x, y, message);
        this.delegate = button;
    }

    public RealmsButtonProxy(RealmsButton button, int id, int x, int y, String message, int width, int height) {
        super(id, x, y, width, height, message);
        this.delegate = button;
    }

    public int getId() {
        return super.id;
    }

    public boolean isActive() {
        return super.active;
    }

    public void setActive(boolean active) {
        super.active = active;
    }

    public void setMessage(String message) {
        super.message = message;
    }

    @Override
    public int getWidth() {
        return super.getWidth();
    }

    public int getY() {
        return super.y;
    }

    @Override
    public boolean mouseClicked(Minecraft minecraft, int mouseX, int mouseY) {
        if (super.mouseClicked(minecraft, mouseX, mouseY)) {
            this.delegate.clicked(mouseX, mouseY);
        }

        return super.mouseClicked(minecraft, mouseX, mouseY);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        this.delegate.released(mouseX, mouseY);
    }

    @Override
    public void renderBackground(Minecraft minecraft, int mouseX, int mouseY) {
        this.delegate.renderBg(mouseX, mouseY);
    }

    public RealmsButton get() {
        return this.delegate;
    }

    @Override
    public int getYImage(boolean hovered) {
        return this.delegate.getYImage(hovered);
    }

    public int getSuperYImage(boolean hovered) {
        return super.getYImage(hovered);
    }

    public int getHeight() {
        return this.height;
    }
}
