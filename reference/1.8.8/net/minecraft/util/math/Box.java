package net.minecraft.util.math;

import net.minecraft.world.HitResult;

public class Box {
    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    public Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public Box(BlockPos min, BlockPos max) {
        this.minX = min.getX();
        this.minY = min.getY();
        this.minZ = min.getZ();
        this.maxX = max.getX();
        this.maxY = max.getY();
        this.maxZ = max.getZ();
    }

    /**
     * Create a new {@code Box} that expands by the given amounts. Positive amounts lead to
     * increases in the maximum values, while negative amounts lead to decreases in the
     * minimum values. This ensures the new {@code Box} is always bigger (unless the amounts
     * given are all {@code 0}).
     */
    public Box expanded(double dx, double dy, double dz) {
        double d0 = this.minX;
        double d1 = this.minY;
        double d2 = this.minZ;
        double d3 = this.maxX;
        double d4 = this.maxY;
        double d5 = this.maxZ;
        if (dx < 0.0) {
            d0 += dx;
        } else if (dx > 0.0) {
            d3 += dx;
        }

        if (dy < 0.0) {
            d1 += dy;
        } else if (dy > 0.0) {
            d4 += dy;
        }

        if (dz < 0.0) {
            d2 += dz;
        } else if (dz > 0.0) {
            d5 += dz;
        }

        return new Box(d0, d1, d2, d3, d4, d5);
    }

    /**
     * Create a new {@code Box} that grows by the given amounts. The minimum and maximum
     * values are decreased and increased respectively by the amounts given. This means the
     * new {@code Box} will be bigger by twice the amounts given on each axis.
     */
    public Box grown(double dx, double dy, double dz) {
        double d0 = this.minX - dx;
        double d1 = this.minY - dy;
        double d2 = this.minZ - dz;
        double d3 = this.maxX + dx;
        double d4 = this.maxY + dy;
        double d5 = this.maxZ + dz;
        return new Box(d0, d1, d2, d3, d4, d5);
    }

    public Box union(Box box) {
        double d0 = Math.min(this.minX, box.minX);
        double d1 = Math.min(this.minY, box.minY);
        double d2 = Math.min(this.minZ, box.minZ);
        double d3 = Math.max(this.maxX, box.maxX);
        double d4 = Math.max(this.maxY, box.maxY);
        double d5 = Math.max(this.maxZ, box.maxZ);
        return new Box(d0, d1, d2, d3, d4, d5);
    }

    public static Box of(double x1, double y1, double z1, double x2, double y2, double z2) {
        double d0 = Math.min(x1, x2);
        double d1 = Math.min(y1, y2);
        double d2 = Math.min(z1, z2);
        double d3 = Math.max(x1, x2);
        double d4 = Math.max(y1, y2);
        double d5 = Math.max(z1, z2);
        return new Box(d0, d1, d2, d3, d4, d5);
    }

    public Box moved(double dx, double dy, double dz) {
        return new Box(this.minX + dx, this.minY + dy, this.minZ + dz, this.maxX + dx, this.maxY + dy, this.maxZ + dz);
    }

    public double intersectX(Box box, double limit) {
        if (!(box.maxY <= this.minY) && !(box.minY >= this.maxY) && !(box.maxZ <= this.minZ) && !(box.minZ >= this.maxZ)) {
            if (limit > 0.0 && box.maxX <= this.minX) {
                double d1 = this.minX - box.maxX;
                if (d1 < limit) {
                    limit = d1;
                }
            } else if (limit < 0.0 && box.minX >= this.maxX) {
                double d0 = this.maxX - box.minX;
                if (d0 > limit) {
                    limit = d0;
                }
            }

            return limit;
        } else {
            return limit;
        }
    }

    public double intersectY(Box box, double limit) {
        if (!(box.maxX <= this.minX) && !(box.minX >= this.maxX) && !(box.maxZ <= this.minZ) && !(box.minZ >= this.maxZ)) {
            if (limit > 0.0 && box.maxY <= this.minY) {
                double d1 = this.minY - box.maxY;
                if (d1 < limit) {
                    limit = d1;
                }
            } else if (limit < 0.0 && box.minY >= this.maxY) {
                double d0 = this.maxY - box.minY;
                if (d0 > limit) {
                    limit = d0;
                }
            }

            return limit;
        } else {
            return limit;
        }
    }

    public double intersectZ(Box box, double limit) {
        if (!(box.maxX <= this.minX) && !(box.minX >= this.maxX) && !(box.maxY <= this.minY) && !(box.minY >= this.maxY)) {
            if (limit > 0.0 && box.maxZ <= this.minZ) {
                double d1 = this.minZ - box.maxZ;
                if (d1 < limit) {
                    limit = d1;
                }
            } else if (limit < 0.0 && box.minZ >= this.maxZ) {
                double d0 = this.maxZ - box.minZ;
                if (d0 > limit) {
                    limit = d0;
                }
            }

            return limit;
        } else {
            return limit;
        }
    }

    public boolean intersects(Box box) {
        return !(box.maxX <= this.minX)
            && !(box.minX >= this.maxX)
            && !(box.maxY <= this.minY)
            && !(box.minY >= this.maxY)
            && !(box.maxZ <= this.minZ)
            && !(box.minZ >= this.maxZ);
    }

    public boolean contains(Vec3d vec) {
        return !(vec.x <= this.minX)
            && !(vec.x >= this.maxX)
            && !(vec.y <= this.minY)
            && !(vec.y >= this.maxY)
            && !(vec.z <= this.minZ)
            && !(vec.z >= this.maxZ);
    }

    public double getAverageSideLength() {
        double d0 = this.maxX - this.minX;
        double d1 = this.maxY - this.minY;
        double d2 = this.maxZ - this.minZ;
        return (d0 + d1 + d2) / 3.0;
    }

    /**
     * Create a new {@code Box} that is contracted by the given amounts. The minimum and maximum
     * values are increased and decreased respectively by the amounts given. This means the
     * new {@code Box} will be smaller by twice the amounts given on each axis.
     */
    public Box contract(double dx, double dy, double dz) {
        double d0 = this.minX + dx;
        double d1 = this.minY + dy;
        double d2 = this.minZ + dz;
        double d3 = this.maxX - dx;
        double d4 = this.maxY - dy;
        double d5 = this.maxZ - dz;
        return new Box(d0, d1, d2, d3, d4, d5);
    }

    public HitResult clip(Vec3d from, Vec3d to) {
        Vec3d vec3d = from.intermediateWithX(to, this.minX);
        Vec3d vec3d1 = from.intermediateWithX(to, this.maxX);
        Vec3d vec3d2 = from.intermediateWithY(to, this.minY);
        Vec3d vec3d3 = from.intermediateWithY(to, this.maxY);
        Vec3d vec3d4 = from.intermediateWithZ(to, this.minZ);
        Vec3d vec3d5 = from.intermediateWithZ(to, this.maxZ);
        if (!this.containsYZ(vec3d)) {
            vec3d = null;
        }

        if (!this.containsYZ(vec3d1)) {
            vec3d1 = null;
        }

        if (!this.containsXZ(vec3d2)) {
            vec3d2 = null;
        }

        if (!this.containsXZ(vec3d3)) {
            vec3d3 = null;
        }

        if (!this.containsXY(vec3d4)) {
            vec3d4 = null;
        }

        if (!this.containsXY(vec3d5)) {
            vec3d5 = null;
        }

        Vec3d vec3d6 = null;
        if (vec3d != null) {
            vec3d6 = vec3d;
        }

        if (vec3d1 != null && (vec3d6 == null || from.squaredDistanceTo(vec3d1) < from.squaredDistanceTo(vec3d6))) {
            vec3d6 = vec3d1;
        }

        if (vec3d2 != null && (vec3d6 == null || from.squaredDistanceTo(vec3d2) < from.squaredDistanceTo(vec3d6))) {
            vec3d6 = vec3d2;
        }

        if (vec3d3 != null && (vec3d6 == null || from.squaredDistanceTo(vec3d3) < from.squaredDistanceTo(vec3d6))) {
            vec3d6 = vec3d3;
        }

        if (vec3d4 != null && (vec3d6 == null || from.squaredDistanceTo(vec3d4) < from.squaredDistanceTo(vec3d6))) {
            vec3d6 = vec3d4;
        }

        if (vec3d5 != null && (vec3d6 == null || from.squaredDistanceTo(vec3d5) < from.squaredDistanceTo(vec3d6))) {
            vec3d6 = vec3d5;
        }

        if (vec3d6 == null) {
            return null;
        }

        Direction direction = null;
        if (vec3d6 == vec3d) {
            direction = Direction.WEST;
        } else if (vec3d6 == vec3d1) {
            direction = Direction.EAST;
        } else if (vec3d6 == vec3d2) {
            direction = Direction.DOWN;
        } else if (vec3d6 == vec3d3) {
            direction = Direction.UP;
        } else if (vec3d6 == vec3d4) {
            direction = Direction.NORTH;
        } else {
            direction = Direction.SOUTH;
        }

        return new HitResult(vec3d6, direction);
    }

    private boolean containsYZ(Vec3d vec) {
        return vec != null && vec.y >= this.minY && vec.y <= this.maxY && vec.z >= this.minZ && vec.z <= this.maxZ;
    }

    private boolean containsXZ(Vec3d vec) {
        return vec != null && vec.x >= this.minX && vec.x <= this.maxX && vec.z >= this.minZ && vec.z <= this.maxZ;
    }

    private boolean containsXY(Vec3d vec) {
        return vec != null && vec.x >= this.minX && vec.x <= this.maxX && vec.y >= this.minY && vec.y <= this.maxY;
    }

    @Override
    public String toString() {
        return "box[" + this.minX + ", " + this.minY + ", " + this.minZ + " -> " + this.maxX + ", " + this.maxY + ", " + this.maxZ + "]";
    }

    public boolean isInvalid() {
        return Double.isNaN(this.minX)
            || Double.isNaN(this.minY)
            || Double.isNaN(this.minZ)
            || Double.isNaN(this.maxX)
            || Double.isNaN(this.maxY)
            || Double.isNaN(this.maxZ);
    }
}
