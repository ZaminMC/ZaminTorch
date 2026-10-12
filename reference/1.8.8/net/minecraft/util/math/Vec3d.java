package net.minecraft.util.math;

public class Vec3d {
    public final double x;
    public final double y;
    public final double z;

    public Vec3d(double x, double y, double z) {
        if (x == -0.0) {
            x = 0.0;
        }

        if (y == -0.0) {
            y = 0.0;
        }

        if (z == -0.0) {
            z = 0.0;
        }

        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3d(Vec3i vec) {
        this(vec.getX(), vec.getY(), vec.getZ());
    }

    public Vec3d subtractFrom(Vec3d vec) {
        return new Vec3d(vec.x - this.x, vec.y - this.y, vec.z - this.z);
    }

    public Vec3d normalize() {
        double d0 = MathHelper.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
        return d0 < 1.0E-4 ? new Vec3d(0.0, 0.0, 0.0) : new Vec3d(this.x / d0, this.y / d0, this.z / d0);
    }

    public double dot(Vec3d vec) {
        return this.x * vec.x + this.y * vec.y + this.z * vec.z;
    }

    public Vec3d cross(Vec3d vec) {
        return new Vec3d(this.y * vec.z - this.z * vec.y, this.z * vec.x - this.x * vec.z, this.x * vec.y - this.y * vec.x);
    }

    public Vec3d subtract(Vec3d vec) {
        return this.subtract(vec.x, vec.y, vec.z);
    }

    public Vec3d subtract(double x, double y, double z) {
        return this.add(-x, -y, -z);
    }

    public Vec3d add(Vec3d vec) {
        return this.add(vec.x, vec.y, vec.z);
    }

    public Vec3d add(double x, double y, double z) {
        return new Vec3d(this.x + x, this.y + y, this.z + z);
    }

    public double distanceTo(Vec3d vec) {
        double d0 = vec.x - this.x;
        double d1 = vec.y - this.y;
        double d2 = vec.z - this.z;
        return MathHelper.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
    }

    public double squaredDistanceTo(Vec3d vec) {
        double d0 = vec.x - this.x;
        double d1 = vec.y - this.y;
        double d2 = vec.z - this.z;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public double length() {
        return MathHelper.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    /**
     * Returns a vector in between this and the given vector.
     * 
     * <p>
     * Given a value of {@code x} in between this and the given vector's X-coordinates,
     * this method returns a vector with Y- and Z-coordinates in between those of this and
     * the given vector.
     * 
     * <p>
     * If this and the given vector have virtually identical X-coordinates, or the given
     * value of {@code x} does not lie in between this and the given vector's X-coordinates,
     * this method returns {@code null}.
     */
    public Vec3d intermediateWithX(Vec3d vec, double x) {
        double d0 = vec.x - this.x;
        double d1 = vec.y - this.y;
        double d2 = vec.z - this.z;
        if (d0 * d0 < 1.0E-7F) {
            return null;
        }

        double d3 = (x - this.x) / d0;
        return !(d3 < 0.0) && !(d3 > 1.0) ? new Vec3d(this.x + d0 * d3, this.y + d1 * d3, this.z + d2 * d3) : null;
    }

    /**
     * Returns a vector in between this and the given vector.
     * 
     * <p>
     * Given a value of {@code y} in between this and the given vector's Y-coordinates,
     * this method returns a vector with X- and Z-coordinates in between those of this and
     * the given vector.
     * 
     * <p>
     * If this and the given vector have virtually identical Y-coordinates, or the given
     * value of {@code y} does not lie in between this and the given vector's Y-coordinates,
     * this method returns {@code null}.
     */
    public Vec3d intermediateWithY(Vec3d vec, double y) {
        double d0 = vec.x - this.x;
        double d1 = vec.y - this.y;
        double d2 = vec.z - this.z;
        if (d1 * d1 < 1.0E-7F) {
            return null;
        }

        double d3 = (y - this.y) / d1;
        return !(d3 < 0.0) && !(d3 > 1.0) ? new Vec3d(this.x + d0 * d3, this.y + d1 * d3, this.z + d2 * d3) : null;
    }

    /**
     * Returns a vector in between this and the given vector.
     * 
     * <p>
     * Given a value of {@code z} in between this and the given vector's Z-coordinates,
     * this method returns a vector with X- and Y-coordinates in between those of this and
     * the given vector.
     * 
     * <p>
     * If this and the given vector have virtually identical Z-coordinates, or the given
     * value of {@code z} does not lie in between this and the given vector's Z-coordinates,
     * this method returns {@code null}.
     */
    public Vec3d intermediateWithZ(Vec3d vec, double z) {
        double d0 = vec.x - this.x;
        double d1 = vec.y - this.y;
        double d2 = vec.z - this.z;
        if (d2 * d2 < 1.0E-7F) {
            return null;
        }

        double d3 = (z - this.z) / d2;
        return !(d3 < 0.0) && !(d3 > 1.0) ? new Vec3d(this.x + d0 * d3, this.y + d1 * d3, this.z + d2 * d3) : null;
    }

    @Override
    public String toString() {
        return "(" + this.x + ", " + this.y + ", " + this.z + ")";
    }

    public Vec3d rotateX(float angle) {
        float f = MathHelper.cos(angle);
        float f1 = MathHelper.sin(angle);
        double d0 = this.x;
        double d1 = this.y * f + this.z * f1;
        double d2 = this.z * f - this.y * f1;
        return new Vec3d(d0, d1, d2);
    }

    public Vec3d rotateY(float angle) {
        float f = MathHelper.cos(angle);
        float f1 = MathHelper.sin(angle);
        double d0 = this.x * f + this.z * f1;
        double d1 = this.y;
        double d2 = this.z * f - this.x * f1;
        return new Vec3d(d0, d1, d2);
    }
}
