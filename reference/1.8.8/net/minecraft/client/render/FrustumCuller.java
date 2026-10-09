package net.minecraft.client.render;

import net.minecraft.util.math.Box;

public class FrustumCuller implements Culler {
    private FrustumData frustum;
    private double offsetX;
    private double offsetY;
    private double offsetZ;

    public FrustumCuller() {
        this(Frustum.getInstance());
    }

    public FrustumCuller(FrustumData frustum) {
        this.frustum = frustum;
    }

    @Override
    public void prepare(double offsetX, double offsetY, double offsetZ) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
    }

    public boolean isVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return this.frustum
            .contains(minX - this.offsetX, minY - this.offsetY, minZ - this.offsetZ, maxX - this.offsetX, maxY - this.offsetY, maxZ - this.offsetZ);
    }

    @Override
    public boolean isVisible(Box shape) {
        return this.isVisible(shape.minX, shape.minY, shape.minZ, shape.maxX, shape.maxY, shape.maxZ);
    }
}
