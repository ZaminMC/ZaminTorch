package net.minecraft.realms;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.resource.Identifier;

public class RealmsButton {
    protected static final Identifier WIDGETS_LOCATION = new Identifier("textures/gui/widgets.png");
    private RealmsButtonProxy proxy;

    public RealmsButton(int id, int x, int y, String message) {
        this.proxy = new RealmsButtonProxy(this, id, x, y, message);
    }

    public RealmsButton(int id, int x, int y, int width, int height, String message) {
        this.proxy = new RealmsButtonProxy(this, id, x, y, message, width, height);
    }

    public ButtonWidget getProxy() {
        return this.proxy;
    }

    public int getId() {
        return this.proxy.getId();
    }

    public boolean active() {
        return this.proxy.isActive();
    }

    public void active(boolean active) {
        this.proxy.setActive(active);
    }

    public void msg(String message) {
        this.proxy.setMessage(message);
    }

    public int getWidth() {
        return this.proxy.getWidth();
    }

    public int getHeight() {
        return this.proxy.getHeight();
    }

    public int getY() {
        return this.proxy.getY();
    }

    public void render(int mouseX, int mouseY) {
        this.proxy.render(Minecraft.getInstance(), mouseX, mouseY);
    }

    public void clicked(int mouseX, int mouseY) {
    }

    public void released(int mouseX, int mouseY) {
    }

    public void blit(int x, int y, int u, int v, int width, int height) {
        this.proxy.drawTexture(x, y, u, v, width, height);
    }

    public void renderBg(int mouseX, int mouseY) {
    }

    public int getYImage(boolean hovered) {
        return this.proxy.getSuperYImage(hovered);
    }
}
