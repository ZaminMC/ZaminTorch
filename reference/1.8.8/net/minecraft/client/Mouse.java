package net.minecraft.client;

import org.lwjgl.opengl.Display;

public class Mouse {
    public int x;
    public int y;

    public void lock() {
        org.lwjgl.input.Mouse.setGrabbed(true);
        this.x = 0;
        this.y = 0;
    }

    public void unlock() {
        org.lwjgl.input.Mouse.setCursorPosition(Display.getWidth() / 2, Display.getHeight() / 2);
        org.lwjgl.input.Mouse.setGrabbed(false);
    }

    public void tick() {
        this.x = org.lwjgl.input.Mouse.getDX();
        this.y = org.lwjgl.input.Mouse.getDY();
    }
}
