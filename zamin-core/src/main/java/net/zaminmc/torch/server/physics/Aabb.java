package net.zaminmc.torch.server.physics;

/**
 * The world-space axis-aligned box (the physics-solver slice): the 1:1
 * port of the reference's {@code util/math/Box.java} — the geometry the
 * reference's own {@code Entity.move} clips a body's swept shape
 * against, box by box, axis by axis.
 *
 * <p>The engine's collision-shape table ({@code CollisionShapes}) keeps
 * its boxes as cell fractions; this class is the <b>absolute</b> form —
 * the min/max corners in world coordinates, exactly the reference's own
 * model. Every method here is the reference's own math, in the
 * reference's own order:</p>
 *
 * <ul>
 * <li>{@link #expanded(double, double, double)} — the swept-motion
 * expansion: a negative delta grows the min face, a positive delta the
 * max face ({@code Box.expanded} lines 37-67 — the region a body
 * sweeps through in one axis);</li>
 * <li>{@link #grown(double, double, double)} / {@link #contract(double, double, double)}
 * — the symmetric grow/shrink ({@code Box.grown} / {@code Box.contract});</li>
 * <li>{@link #moved(double, double, double)} — the pure translate
 * ({@code Box.moved});</li>
 * <li>{@link #intersects(Aabb)} — the strict-overlap test with the
 * reference's own half-open convention ({@code !(box.maxX <= this.minX)
 * && ...}, lines 164-172: touching faces do NOT intersect);</li>
 * <li>the clipping trio {@link #intersectX(Aabb, double)} /
 * {@link #intersectY(Aabb, double)} / {@link #intersectZ(Aabb, double)}
 * — the reference's own move-clipping law (lines 104-162): given the
 * body's shape and a per-axis motion limit, the box clips the limit to
 * the face distance <b>only when the two boxes overlap on the other two
 * axes</b>, so a floor beneath a falling body stops its Y exactly at
 * the floor's ceiling and a wall ahead stops its X exactly at the
 * wall's face.</li>
 * </ul>
 *
 * <p>Immutable: every operation answers a new box, exactly like the
 * reference's own immutable {@code Box}.</p>
 */
public final class Aabb {

    /** The min corner (inclusive). */
    public final double minX;
    public final double minY;
    public final double minZ;
    /** The max corner (exclusive in the reference's own convention). */
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    /** The canonical constructor (the reference's own field order). */
    public Aabb(double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    /** The min/max-free pair constructor ({@code Box.of}, lines 88-98). */
    public static Aabb of(double x1, double y1, double z1,
            double x2, double y2, double z2) {
        return new Aabb(
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    /**
     * The swept-motion expansion ({@code Box.expanded}, lines 37-67): a
     * negative delta lowers the min face, a positive delta raises the max
     * face — the union of the body's shape over the whole motion.
     */
    public Aabb expanded(double dx, double dy, double dz) {
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
        return new Aabb(d0, d1, d2, d3, d4, d5);
    }

    /**
     * The symmetric growth ({@code Box.grown}, lines 70-79): both faces
     * move out by the amount — the body grown by its own margin.
     */
    public Aabb grown(double dx, double dy, double dz) {
        return new Aabb(
                this.minX - dx, this.minY - dy, this.minZ - dz,
                this.maxX + dx, this.maxY + dy, this.maxZ + dz);
    }

    /**
     * The symmetric shrink ({@code Box.contract}, lines 194-203): both
     * faces move in by the amount — the fire-touch read's own 0.001
     * shave.
     */
    public Aabb contract(double dx, double dy, double dz) {
        return new Aabb(
                this.minX + dx, this.minY + dy, this.minZ + dz,
                this.maxX - dx, this.maxY - dy, this.maxZ - dz);
    }

    /** The pure translate ({@code Box.moved}, line 100). */
    public Aabb moved(double dx, double dy, double dz) {
        return new Aabb(
                this.minX + dx, this.minY + dy, this.minZ + dz,
                this.maxX + dx, this.maxY + dy, this.maxZ + dz);
    }

    /** The bounding union ({@code Box.union}, lines 81-86). */
    public Aabb union(Aabb box) {
        return new Aabb(
                Math.min(this.minX, box.minX), Math.min(this.minY, box.minY),
                Math.min(this.minZ, box.minZ), Math.max(this.maxX, box.maxX),
                Math.max(this.maxY, box.maxY), Math.max(this.maxZ, box.maxZ));
    }

    /**
     * The strict overlap ({@code Box.intersects}, lines 164-172): the
     * reference's half-open convention — faces that merely touch do NOT
     * intersect.
     */
    public boolean intersects(Aabb box) {
        return !(box.maxX <= this.minX)
                && !(box.minX >= this.maxX)
                && !(box.maxY <= this.minY)
                && !(box.minY >= this.maxY)
                && !(box.maxZ <= this.minZ)
                && !(box.minZ >= this.maxZ);
    }

    /**
     * The X-axis clip ({@code Box.intersectX}, lines 104-122): when the
     * two boxes overlap on Y and Z, the box clips the X motion limit to
     * its own face distance — a positive limit to {@code this.minX -
     * box.maxX} (the mover approaching the box's near face), a negative
     * limit to {@code this.maxX - box.minX} (the mover receding from the
     * box's far face). A zero-or-opposite limit passes untouched, and a
     * box that does not overlap the other two axes passes untouched too.
     */
    public double intersectX(Aabb box, double limit) {
        if (!(box.maxY <= this.minY) && !(box.minY >= this.maxY)
                && !(box.maxZ <= this.minZ) && !(box.minZ >= this.maxZ)) {
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
        }
        return limit;
    }

    /**
     * The Y-axis clip ({@code Box.intersectY}, lines 124-142): the same
     * law on the Y axis — the floor-clip and ceiling-clip the falling
     * and rising bodies take.
     */
    public double intersectY(Aabb box, double limit) {
        if (!(box.maxX <= this.minX) && !(box.minX >= this.maxX)
                && !(box.maxZ <= this.minZ) && !(box.minZ >= this.maxZ)) {
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
        }
        return limit;
    }

    /**
     * The Z-axis clip ({@code Box.intersectZ}, lines 144-162): the same
     * law on the Z axis.
     */
    public double intersectZ(Aabb box, double limit) {
        if (!(box.maxX <= this.minX) && !(box.minX >= this.maxX)
                && !(box.maxY <= this.minY) && !(box.minY >= this.maxY)) {
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
        }
        return limit;
    }

    /** The three sides' average ({@code Box.getAverageSideLength}). */
    public double averageSideLength() {
        return (this.maxX - this.minX + this.maxY - this.minY
                + this.maxZ - this.minZ) / 3.0;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Aabb)) {
            return false;
        }
        Aabb box = (Aabb) object;
        return this.minX == box.minX && this.minY == box.minY
                && this.minZ == box.minZ && this.maxX == box.maxX
                && this.maxY == box.maxY && this.maxZ == box.maxZ;
    }

    @Override
    public int hashCode() {
        long bits = Double.doubleToLongBits(this.minX);
        bits = 31 * bits + Double.doubleToLongBits(this.minY);
        bits = 31 * bits + Double.doubleToLongBits(this.minZ);
        bits = 31 * bits + Double.doubleToLongBits(this.maxX);
        bits = 31 * bits + Double.doubleToLongBits(this.maxY);
        bits = 31 * bits + Double.doubleToLongBits(this.maxZ);
        return (int) (bits ^ bits >>> 32);
    }

    @Override
    public String toString() {
        return "box[" + this.minX + ", " + this.minY + ", " + this.minZ
                + " -> " + this.maxX + ", " + this.maxY + ", " + this.maxZ + "]";
    }
}
