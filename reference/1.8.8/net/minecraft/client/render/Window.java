package net.minecraft.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;

public class Window {
    private final double scaledWidth;
    private final double scaledHeight;
    private int width;
    private int height;
    private int scale;

    public Window(Minecraft minecraft) {
        this.width = minecraft.width;
        this.height = minecraft.height;
        this.scale = 1;
        boolean flag = minecraft.isUnicode();
        int i = minecraft.options.guiScale;
        if (i == 0) {
            i = 1000;
        }

        while (this.scale < i && this.width / (this.scale + 1) >= 320 && this.height / (this.scale + 1) >= 240) {
            this.scale++;
        }

        if (flag && this.scale % 2 != 0 && this.scale != 1) {
            this.scale--;
        }

        this.scaledWidth = (double)this.width / this.scale;
        this.scaledHeight = (double)this.height / this.scale;
        this.width = MathHelper.ceil(this.scaledWidth);
        this.height = MathHelper.ceil(this.scaledHeight);
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public double getScaledWidth() {
        return this.scaledWidth;
    }

    public double getScaledHeight() {
        return this.scaledHeight;
    }

    public int getScale() {
        return this.scale;
    }
}
