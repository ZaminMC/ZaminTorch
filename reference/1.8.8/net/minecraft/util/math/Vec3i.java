package net.minecraft.util.math;

import com.google.common.base.Objects;

public class Vec3i implements Comparable<Vec3i> {
    public static final Vec3i ZERO = new Vec3i(0, 0, 0);
    private final int x;
    private final int y;
    private final int z;

    public Vec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3i(double x, double y, double z) {
        this(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof Vec3i)) {
            return false;
        }

        Vec3i vec3i = (Vec3i)object;
        return this.getX() == vec3i.getX() && this.getY() == vec3i.getY() && this.getZ() == vec3i.getZ();
    }

    @Override
    public int hashCode() {
        return (this.getY() + this.getZ() * 31) * 31 + this.getX();
    }

    public int compareTo(Vec3i vec3i) {
        if (this.getY() == vec3i.getY()) {
            return this.getZ() == vec3i.getZ() ? this.getX() - vec3i.getX() : this.getZ() - vec3i.getZ();
        } else {
            return this.getY() - vec3i.getY();
        }
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public Vec3i cross(Vec3i vec) {
        return new Vec3i(
            this.getY() * vec.getZ() - this.getZ() * vec.getY(),
            this.getZ() * vec.getX() - this.getX() * vec.getZ(),
            this.getX() * vec.getY() - this.getY() * vec.getX()
        );
    }

    public double squaredDistanceTo(double x, double y, double z) {
        double d0 = this.getX() - x;
        double d1 = this.getY() - y;
        double d2 = this.getZ() - z;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public double squaredDistanceToCenter(double x, double y, double z) {
        double d0 = this.getX() + 0.5 - x;
        double d1 = this.getY() + 0.5 - y;
        double d2 = this.getZ() + 0.5 - z;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public double squaredDistanceTo(Vec3i vec) {
        return this.squaredDistanceTo(vec.getX(), vec.getY(), vec.getZ());
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("x", this.getX()).add("y", this.getY()).add("z", this.getZ()).toString();
    }
}
