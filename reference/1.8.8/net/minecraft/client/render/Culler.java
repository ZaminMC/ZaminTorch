package net.minecraft.client.render;

import net.minecraft.util.math.Box;

public interface Culler {
    boolean isVisible(Box shape);

    void prepare(double offsetX, double offsetY, double offsetZ);
}
