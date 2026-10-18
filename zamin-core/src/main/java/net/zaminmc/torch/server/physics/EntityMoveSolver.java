package net.zaminmc.torch.server.physics;

import java.util.List;

/**
 * The swept-box movement solver (the physics-solver slice): the 1:1
 * port of the reference's {@code Entity.move(double, double, double)}
 * lines 390-585 — the axis-by-axis clipping of a body's motion against
 * every collision box its swept shape touches, with the reference's own
 * step-height retry.
 *
 * <p>This is the BODY model the ledger deferred from the
 * collision-shape slice: the point queries ({@code WorldSolidity.
 * isSolidAt}/{@code supportY}) keep the legacy one-cell convention for
 * the families that still ride them, but the moving bodies resolve
 * through THIS law — a body straddling the fence's 1.5 bands from the
 * cell above, a walk up the stairs' ascending two-box form, a step
 * over the half slab, all exactly the reference's own walk:</p>
 *
 * <ol>
 * <li>the cobweb drag ({@code inCobweb}: {@code dx *= 0.25, dy *= 0.05,
 * dz *= 0.25} and the body's velocities zero — lines 397-405);</li>
 * <li>the sneaking edge-guard (a player on the ground sneaking: the
 * 0.05 shaved walk that never leaves the supporting block — lines
 * 409-446);</li>
 * <li>the gather over the shape expanded by the motion (line 448);</li>
 * <li>the Y clip, then the X clip, then the Z clip — each box in the
 * list clipping the axis limit through the reference's own
 * {@code Box.intersectY/X/Z} law (lines 450-468);</li>
 * <li>the step-height retry ({@code stepHeight > 0} and grounded and
 * the horizontal motion was clipped): the two-pass walk up-and-over
 * at the full intended motion, the squared-distance comparison, and
 * the keep-only-if-better law (lines 469-545);</li>
 * <li>the flags — {@code collidingHorizontally}, {@code
 * collidingVertically}, {@code onGround = collidingVertically && the
 * Y motion was downward} — and the velocity-zeroing laws (the X/Z
 * clip zeroes the matching velocity, the vertical clip zeroes
 * velocityY — the reference's own {@code beforeCollision} default,
 * lines 560-596).</li>
 * </ol>
 *
 * <p>The solver is pure geometry over the {@link EntityCollisionWorld}
 * block surface: no entity state crosses in, none crosses out. The
 * caller owns the velocities, the fall distance, and the
 * family-specific side effects (the step sounds, the block-collision
 * checks), exactly like the reference's own Entity/LivingEntity
 * split.</p>
 */
public final class EntityMoveSolver {

    private EntityMoveSolver() {
    }

    /**
     * The moving body's own state (the reference's {@code x/y/z} +
     * {@code width/height}): the feet-centre position and the box the
     * shape derives from — {@code (x - width/2, y, z - width/2) .. (x +
     * width/2, y + height, z + width/2)} (the reference's {@code
     * setPosition} law, line 246).
     */
    public static final class Body {

        /** The feet-centre X. */
        public double x;
        /** The feet Y (the shape's minY). */
        public double y;
        /** The feet-centre Z. */
        public double z;
        /** The body's width (the box's X/Z span). */
        public final double width;
        /** The body's height (the box's Y span). */
        public final double height;
        /** Whether the body stands on the ground entering the move (the step retry's own gate). */
        public boolean onGround;

        public Body(double x, double y, double z, double width, double height) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = width;
            this.height = height;
        }

        /** The body's current shape (the reference's {@code getShape()}). */
        public Aabb shape() {
            double half = this.width / 2.0;
            return new Aabb(this.x - half, this.y, this.z - half,
                    this.x + half, this.y + this.height, this.z + half);
        }

        /** The reference's {@code setPositionFromShape()} (line 586's own tail). */
        void setPositionFromShape(Aabb shape) {
            this.x = (shape.minX + shape.maxX) / 2.0;
            this.y = shape.minY;
            this.z = (shape.minZ + shape.maxZ) / 2.0;
        }
    }

    /**
     * The per-family movement law (the reference's own per-entity
     * fields, distilled): the step height ({@code Entity.stepHeight} —
     * 0.5 for the walkers, 0.0 for the item bodies and the falling
     * blocks, 1.0 for the horses), the sneaking edge-guard (a player on
     * the ground — {@code onGround && isSneaking() && this instanceof
     * PlayerEntity}), the no-clip pass-through, and the cobweb drag.
     */
    public record MoveLaw(double stepHeight, boolean sneakingEdgeGuard,
            boolean noClip, boolean inCobweb) {

        /** The plain law: no step, no guard, no clip, no web. */
        public static MoveLaw plain() {
            return new MoveLaw(0.0, false, false, false);
        }
    }

    /**
     * The move's outcome: the actually-moved deltas (the clipped
     * {@code dx/dy/dz} — the caller applies them to its own position,
     * which the solver's {@link Body} already carries), the colliding
     * flags, and the velocity-zeroing signals (the reference's own
     * lines 577-596: the X clip zeroes velocityX, the Z clip
     * velocityZ, the vertical clip velocityY through {@code
     * beforeCollision}'s default).
     */
    public record Result(double dx, double dy, double dz, boolean onGround,
            boolean collidingHorizontally, boolean collidingVertically,
            boolean collided, boolean zeroVelocityX, boolean zeroVelocityZ,
            boolean zeroVelocityY, boolean cobwebVelocityZero) {

        /** The no-clip outcome: everything passes, no flag flips. */
        static Result noClip(double dx, double dy, double dz, boolean onGround) {
            return new Result(dx, dy, dz, onGround, false, false, false,
                    false, false, false, false);
        }
    }

    /**
     * Moves the body by the intended motion, clipping against the
     * world's collision boxes exactly like the reference's {@code
     * Entity.move} (lines 390-585). The body's own x/y/z land on the
     * clipped destination; the result reports the flags and the
     * zeroing signals.
     */
    public static Result move(EntityCollisionWorld world, Body body,
            double dx, double dy, double dz, MoveLaw law) {
        if (law.noClip()) {
            body.x += dx;
            body.y += dy;
            body.z += dz;
            return Result.noClip(dx, dy, dz, body.onGround);
        }

        // The cobweb drag (lines 397-405): the motion shrinks to the
        // web's own crawl and the body's velocities zero.
        boolean cobwebZero = false;
        if (law.inCobweb()) {
            dx *= 0.25;
            dy *= 0.05F;
            dz *= 0.25;
            cobwebZero = true;
        }

        // The intended motion (the d3/d4/d5 the flags compare against).
        double d3 = dx;
        double d4 = dy;
        double d5 = dz;

        // The sneaking edge-guard (lines 409-446): a player on the
        // ground never leaves the supporting block — the 0.05 shaved
        // walk, X alone first, then Z, then the pair.
        if (law.sneakingEdgeGuard() && body.onGround) {
            double d6 = 0.05;
            while (dx != 0.0
                    && EntityCollisions.gatherBlockBoxes(world,
                            body.shape().moved(dx, -1.0, 0.0), true).isEmpty()) {
                if (dx < d6 && dx >= -d6) {
                    dx = 0.0;
                } else if (dx > 0.0) {
                    dx -= d6;
                } else {
                    dx += d6;
                }
                d3 = dx;
            }
            for (; dz != 0.0
                    && EntityCollisions.gatherBlockBoxes(world,
                            body.shape().moved(0.0, -1.0, dz), true).isEmpty(); d5 = dz) {
                if (dz < d6 && dz >= -d6) {
                    dz = 0.0;
                } else if (dz > 0.0) {
                    dz -= d6;
                } else {
                    dz += d6;
                }
            }
            for (; dx != 0.0 && dz != 0.0
                    && EntityCollisions.gatherBlockBoxes(world,
                            body.shape().moved(dx, -1.0, dz), true).isEmpty(); d5 = dz) {
                if (dx < d6 && dx >= -d6) {
                    dx = 0.0;
                } else if (dx > 0.0) {
                    dx -= d6;
                } else {
                    dx += d6;
                }
                d3 = dx;
                if (dz < d6 && dz >= -d6) {
                    dz = 0.0;
                } else if (dz > 0.0) {
                    dz -= d6;
                } else {
                    dz += d6;
                }
            }
        }

        // The gather (line 448): every box the swept shape touches.
        Aabb box = body.shape();
        List<Aabb> list1 = EntityCollisions.gatherBlockBoxes(world,
                box.expanded(dx, dy, dz), true);

        // The Y clip (lines 450-452), then the X clip (455-457), then
        // the Z clip (460-462) — each box in the list in walk order.
        for (Aabb box1 : list1) {
            dy = box1.intersectY(body.shape(), dy);
        }
        Aabb moved = body.shape().moved(0.0, dy, 0.0);
        body.setPositionFromShape(moved);
        boolean flag1 = body.onGround || d4 != dy && d4 < 0.0;

        for (Aabb box2 : list1) {
            dx = box2.intersectX(body.shape(), dx);
        }
        body.setPositionFromShape(body.shape().moved(dx, 0.0, 0.0));

        for (Aabb box13 : list1) {
            dz = box13.intersectZ(body.shape(), dz);
        }
        body.setPositionFromShape(body.shape().moved(0.0, 0.0, dz));

        // The step-height retry (lines 469-545): a grounded walker whose
        // horizontal motion was clipped tries the up-and-over walk at the
        // full intended motion — two passes, the squared-distance
        // comparison, and the keep-only-if-better law.
        if (law.stepHeight() > 0.0 && flag1 && (d3 != dx || d5 != dz)) {
            double d11 = dx;
            double d7 = dy;
            double d8 = dz;
            Aabb box3 = body.shape();
            body.setPositionFromShape(box);
            dx = d3;
            dy = law.stepHeight();
            dz = d5;
            List<Aabb> list = EntityCollisions.gatherBlockBoxes(world,
                    body.shape().expanded(dx, dy, dz), true);
            Aabb box4 = body.shape();
            Aabb box5 = box4.expanded(dx, 0.0, dz);
            double d9 = dy;

            for (Aabb box6 : list) {
                d9 = box6.intersectY(box5, d9);
            }
            box4 = box4.moved(0.0, d9, 0.0);
            double d15 = dx;

            for (Aabb box7 : list) {
                d15 = box7.intersectX(box4, d15);
            }
            box4 = box4.moved(d15, 0.0, 0.0);
            double d16 = dz;

            for (Aabb box8 : list) {
                d16 = box8.intersectZ(box4, d16);
            }
            box4 = box4.moved(0.0, 0.0, d16);
            Aabb box14 = body.shape();
            double d17 = dy;

            for (Aabb box9 : list) {
                d17 = box9.intersectY(box14, d17);
            }
            box14 = box14.moved(0.0, d17, 0.0);
            double d18 = dx;

            for (Aabb box10 : list) {
                d18 = box10.intersectX(box14, d18);
            }
            box14 = box14.moved(d18, 0.0, 0.0);
            double d19 = dz;

            for (Aabb box11 : list) {
                d19 = box11.intersectZ(box14, d19);
            }
            box14 = box14.moved(0.0, 0.0, d19);
            double d20 = d15 * d15 + d16 * d16;
            double d10 = d18 * d18 + d19 * d19;
            if (d20 > d10) {
                dx = d15;
                dz = d16;
                dy = -d9;
                body.setPositionFromShape(box4);
            } else {
                dx = d18;
                dz = d19;
                dy = -d17;
                body.setPositionFromShape(box14);
            }

            for (Aabb box12 : list) {
                dy = box12.intersectY(body.shape(), dy);
            }
            body.setPositionFromShape(body.shape().moved(0.0, dy, 0.0));
            if (d11 * d11 + d8 * d8 >= dx * dx + dz * dz) {
                dx = d11;
                dy = d7;
                dz = d8;
                body.setPositionFromShape(box3);
            }
        }

        // The flags (lines 560-564): the horizontal pair, the vertical,
        // the ground read.
        boolean collidingHorizontally = d3 != dx || d5 != dz;
        boolean collidingVertically = d4 != dy;
        boolean onGround = collidingVertically && d4 < 0.0;
        body.onGround = onGround;
        boolean collided = collidingHorizontally || collidingVertically;

        // The velocity-zeroing laws (lines 577-596): the X clip zeroes
        // velocityX, the Z clip velocityZ, the vertical clip
        // velocityY (the reference's own beforeCollision default).
        boolean zeroVx = d3 != dx;
        boolean zeroVz = d5 != dz;
        boolean zeroVy = d4 != dy;

        return new Result(dx, dy, dz, onGround, collidingHorizontally,
                collidingVertically, collided, zeroVx, zeroVz, zeroVy, cobwebZero);
    }
}
