package net.minecraft.client.render;

public class FrustumData {
    public float[][] frustum = new float[6][4];
    public float[] projectionMatrix = new float[16];
    public float[] modelMatrix = new float[16];
    public float[] clipMatrix = new float[16];

    private double discriminant(float[] side, double x, double y, double z) {
        return side[0] * x + side[1] * y + side[2] * z + side[3];
    }

    public boolean contains(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        for (int i = 0; i < 6; i++) {
            float[] afloat = this.frustum[i];
            if (!(this.discriminant(afloat, minX, minY, minZ) > 0.0)
                && !(this.discriminant(afloat, maxX, minY, minZ) > 0.0)
                && !(this.discriminant(afloat, minX, maxY, minZ) > 0.0)
                && !(this.discriminant(afloat, maxX, maxY, minZ) > 0.0)
                && !(this.discriminant(afloat, minX, minY, maxZ) > 0.0)
                && !(this.discriminant(afloat, maxX, minY, maxZ) > 0.0)
                && !(this.discriminant(afloat, minX, maxY, maxZ) > 0.0)
                && !(this.discriminant(afloat, maxX, maxY, maxZ) > 0.0)) {
                return false;
            }
        }

        return true;
    }
}
