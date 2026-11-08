package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;

import java.util.Random;

/**
 * The boat (community entities.json object type 1): it bobs at the
 * waterline (the 2026-11-08 census row BB-1 — the reference's own
 * 5-slice buoyancy, BoatEntity.tick lines 189-199 + 248-257: the hull's
 * band sliced into five, the wet fraction steering the ±0.04 bob and
 * the fully-submerged +0.007 float-up), the rider paddles (Wave BS —
 * the reference's own steer model, lines 259-284: the drive the
 * momentum's own 0.05 per tick along the rider's look rotated by the
 * strafe, the momentum ramping 0.07-0.35 over its 35-tick curve, the
 * horizontal capped 0.35), the heading follows the MOVEMENT (census
 * row BB-2 — the reference's fastAtan2 over the negated displacement,
 * clamped ±20°/tick, never the rider's look), the hull mows the flora
 * under its corners (census row BB-4 — snow layers vanish, lily pads
 * break with drops), and the crash / the 3.0+ fall break the hull
 * into the wreck (census row BB-3 — 3 planks + 2 sticks, never the
 * boat item the damage walk's own break hands); on land it slides to
 * a stop under the grounded halving.
 */
public final class BoatEntity extends VehicleEntity {

    public static final double HALF_HEIGHT = 0.2;

    /**
     * The hull's width (the physics-solver slice — the reference's own
     * {@code setSize(1.5F, 0.6F)}: the swept box's X/Z span; the height
     * keeps the engine's own 0.4 convention the resting rows pin).
     */
    public static final double HULL_WIDTH = 1.5;
    /**
     * The paddle's per-tick drive factor (tick lines 262-263: the 0.05 —
     * the momentum × forward input × 0.05 along the paddle heading, the
     * CONTINUOUS law the boat family owns — never the cart's from-rest
     * kick).
     */
    public static final double PADDLE_DRIVE = 0.05;
    /**
     * The momentum's ceiling (line 275: 0.35 — the full paddle force,
     * reached over the 35-tick ramp while the hull accelerates).
     */
    public static final double MOMENTUM_CEILING = 0.35;
    /**
     * The momentum's idle floor (line 281: 0.07 — the standing paddle
     * force the decay settles at).
     */
    public static final double MOMENTUM_FLOOR = 0.07;
    /** The momentum's ramp divisor (lines 275/280: the 35). */
    public static final double MOMENTUM_RAMP = 35.0;
    /**
     * The horizontal speed cap (lines 266-272: 0.35 — every surface, the
     * land crawl lives far below it under the grounded halving; the
     * pre-fix engine's separate 0.25 land cap retired).
     */
    public static final double HORIZONTAL_CAP = 0.35;
    /**
     * The crash band (line 311: {@code d9 > 0.2975} — the PRE-move speed
     * into the horizontal collision; below it the hull grinds off the
     * wall, at it the hull breaks).
     */
    public static final double CRASH_SPEED = 0.2975;
    /** The glide drag (lines 312-314: the post-move 0.99 horizontal). */
    public static final double GLIDE_DRAG = 0.99;
    /** The glide drag's vertical arm (line 313: the 0.95). */
    public static final double VERTICAL_DRAG = 0.95;
    /** The grounded halving (lines 304-308: the pre-move 0.5 on all three). */
    public static final double GROUND_HALVING = 0.5;
    /**
     * The buoyancy gain (line 250: the 0.04 — the partial band's bob
     * slope; the d0=0 form IS the raw land gravity, −0.04, the BS
     * wave's own documentation of the same line).
     */
    public static final double BUOYANCY_GAIN = 0.04;
    /** The fully-submerged float-up (line 256: the +0.007). */
    public static final double SUBMERGED_RISE = 0.007;
    /** The buoyancy's slice count (line 189: the 5). */
    public static final int BUOYANCY_SLICES = 5;
    /**
     * The slice band's own shift (lines 193-194: the −0.125 — the
     * waterline sits an eighth under the hull's own bottom).
     */
    public static final double SLICE_SHIFT = 0.125;
    /** The heading law's turn clamp (lines 337-343: the ±20°/tick). */
    public static final double HEADING_CLAMP = 20.0;
    /** The still-hull gate (line 332: the 0.001 squared displacement). */
    public static final double HEADING_MOVE_GATE = 0.001;
    /** The fall break's own band (checkFallDamage line 398: the 3.0). */
    public static final float FALL_BREAK_DISTANCE = 3.0f;

    /**
     * The momentum field (the reference's own {@code speedBoost} —
     * Java's 0.0 first-tick value included: a fresh hull warms up to
     * the 0.07 idle floor over its first 35 ticks, the reference's own
     * curve).
     */
    private double speedBoost;

    /** The fall debt (checkFallDamage line 416: the accumulated dry fall). */
    private float fallDistance;

    public BoatEntity(int entityId, Position position, Random random) {
        super(entityId, Kind.BOAT, position, random);
    }

    /** The piston push (VIII13): the boat's own solver walk with the hull box. */
    @Override
    protected void pushMove(double dx, double dy, double dz) {
        solverMove(query, HALF_HEIGHT, HULL_WIDTH, dx, dy, dz);
    }

    /** @return the paddle momentum's own state (the seats' read). */
    public double momentum() {
        return speedBoost;
    }

    @Override
    protected void tickKind(WorldQuery world) {
        Position at = position();
        SteerInput input = consumeSteer();
        boolean wasGrounded = onGround();

        // THE HEAD SPEED (the reference's d9, line 201 — measured before
        // every arm; the crash consult reads this, never the post-paddle
        // state).
        double headSpeed = Math.sqrt(velocityX() * velocityX() + velocityZ() * velocityZ());

        // THE 5-SLICE BUOYANCY (lines 189-199 + 248-257, the 2026-11-08
        // census row BB-1): the hull's own [minY, maxY] band sliced into
        // five vertical slices, each slice's box shifted −0.125 (the
        // waterline an eighth under the hull's own bottom), d0 the wet
        // fraction — then the bob: a PARTIAL band adds
        // 0.04·(2·d0 − 1) to vy (the d0=0 form IS the raw land gravity,
        // −0.04 — the separate gravity arm retired; the half-wet hull
        // rests, the quarter-wet sinks, the three-quarter bobs up), and
        // the FULLY-submerged hull halves a falling vy and floats up at
        // +0.007. The pre-fix engine's zero-vertical water rest — the
        // landed approximation the row named — retired whole.
        double hullMinY = at.y() - HALF_HEIGHT;
        double hullMaxY = at.y() + HALF_HEIGHT;
        double d0 = 0.0;
        for (int j = 0; j < BUOYANCY_SLICES; j++) {
            double sliceMin = hullMinY
                    + (hullMaxY - hullMinY) * j / BUOYANCY_SLICES - SLICE_SHIFT;
            double sliceMax = hullMinY
                    + (hullMaxY - hullMinY) * (j + 1) / BUOYANCY_SLICES - SLICE_SHIFT;
            if (world.waterInBox(at.x() - HULL_WIDTH / 2.0, sliceMin,
                    at.z() - HULL_WIDTH / 2.0,
                    at.x() + HULL_WIDTH / 2.0, sliceMax,
                    at.z() + HULL_WIDTH / 2.0)) {
                d0 += 1.0 / BUOYANCY_SLICES;
            }
        }
        if (d0 < 1.0) {
            setVelocity(velocityX(), velocityY() + BUOYANCY_GAIN * (d0 * 2.0 - 1.0),
                    velocityZ());
        } else {
            if (velocityY() < 0.0) {
                setVelocity(velocityX(), velocityY() / 2.0, velocityZ());
            }
            setVelocity(velocityX(), velocityY() + SUBMERGED_RISE, velocityZ());
        }

        // THE PADDLING (tick lines 259-264, 1:1): the paddle heading f =
        // the RIDER'S OWN yaw − sideways × 90° (the A/D strafe rotates
        // the heading a quarter turn), the drive the momentum's own
        // speedBoost × forward input × 0.05 per tick — W paddles ahead,
        // S paddles astern (no forward gate: the boat's drive answers
        // the negative input too), and a riderless hull never paddles
        // (the LivingEntity gate). The NaN look stands in the boat's own
        // heading — the direct-feed seats' documented convention.
        if (hasPassenger()) {
            float riderYaw = input.riderYaw();
            float yaw = Float.isNaN(riderYaw) ? rotation().yaw() : riderYaw;
            double f = Math.toRadians(yaw + -input.sideways() * 90.0f);
            double drive = speedBoost * input.forward() * PADDLE_DRIVE;
            setVelocity(velocityX() + -Math.sin(f) * drive,
                    velocityY(),
                    velocityZ() + Math.cos(f) * drive);
        }

        // THE CAP (lines 266-272): the horizontal 0.35 — scaled onto the
        // direction, the reference's own form.
        double speedNow = Math.sqrt(velocityX() * velocityX() + velocityZ() * velocityZ());
        if (speedNow > HORIZONTAL_CAP) {
            setVelocity(velocityX() * (HORIZONTAL_CAP / speedNow), velocityY(),
                    velocityZ() * (HORIZONTAL_CAP / speedNow));
            speedNow = HORIZONTAL_CAP;
        }

        // THE MOMENTUM (lines 274-284): accelerating under the ceiling
        // ramps the boost toward 0.35 (the /35 curve — a half-second of
        // sustained paddling to the full force); anything else decays
        // back toward the 0.07 idle floor.
        if (speedNow > headSpeed && speedBoost < MOMENTUM_CEILING) {
            speedBoost = speedBoost + (MOMENTUM_CEILING - speedBoost) / MOMENTUM_RAMP;
            if (speedBoost > MOMENTUM_CEILING) {
                speedBoost = MOMENTUM_CEILING;
            }
        } else {
            speedBoost = speedBoost - (speedBoost - MOMENTUM_FLOOR) / MOMENTUM_RAMP;
            if (speedBoost < MOMENTUM_FLOOR) {
                speedBoost = MOMENTUM_FLOOR;
            }
        }

        // THE FLORA MOW (lines 286-302, the 2026-11-08 census row BB-4):
        // the hull's four corners (±0.4 around the centre — the
        // reference's (i1%2 − 0.5)·0.8 / (i1/2 − 0.5)·0.8 offsets) × the
        // two y-bands (floor(y) and the cell above): a SNOW LAYER
        // vanishes WITHOUT drops (world.removeBlock), a LILY PAD breaks
        // WITH its standard drops (world.breakBlock(pos, true)). The
        // reference's collidingHorizontally reset beside each arm has no
        // engine-visible counterpart: the flora is never in the
        // collision surface (isFlora's own exclusion), so the crash
        // consult never sees the mowed cell at all.
        for (int i1 = 0; i1 < 4; i1++) {
            int cornerX = (int) Math.floor(at.x() + (i1 % 2 - 0.5) * 0.8);
            int cornerZ = (int) Math.floor(at.z() + (i1 / 2 - 0.5) * 0.8);
            for (int j2 = 0; j2 < 2; j2++) {
                int cellY = (int) Math.floor(at.y()) + j2;
                net.zaminmc.torch.util.Identifier floraId =
                        world.blockIdAt(cornerX, cellY, cornerZ);
                if (floraId == null) {
                    continue;
                }
                String floraValue = floraId.value();
                if (floraValue.equals("snow_layer")) {
                    world.removeBlockAt(cornerX, cellY, cornerZ);
                } else if (floraValue.equals("waterlily")) {
                    world.breakBlockWithDrops(cornerX, cellY, cornerZ);
                }
            }
        }

        // THE GROUNDED HALVING (lines 304-308): the pre-move 0.5 on all
        // three axes when the hull rests on ground.
        if (onGround()) {
            setVelocity(velocityX() * GROUND_HALVING, velocityY() * GROUND_HALVING,
                    velocityZ() * GROUND_HALVING);
        }

        // THE SWEPT-BOX APPLICATION (the physics-solver slice): the
        // boat's own Entity.move — the 1.5-wide hull (the reference's
        // own setSize) clipped axis by axis — when the query provides
        // the collision surface; the high-speed wall hit breaks the hull
        // (lines 311-326), anything else the glide drag.
        net.zaminmc.torch.server.physics.EntityMoveSolver.Result swept =
                solverMove(world, HALF_HEIGHT, HULL_WIDTH,
                        velocityX(), velocityY(), velocityZ());
        if (swept != null) {
            // THE FALL DEBT'S LANDING (checkFallDamage lines 396-414,
            // census row BB-3): the dry fall accumulated below, and the
            // landing past 3.0 breaks the hull with the WRECK drops
            // (3 planks + 2 sticks — the same arm the crash hands, never
            // the boat item); the water-below fall never accumulates.
            double movedDy = position().y() - at.y();
            boolean landed = swept.onGround() && !wasGrounded;
            if (landed) {
                if (fallDistance > FALL_BREAK_DISTANCE) {
                    fallDistance = 0.0f;
                    crashBreak();
                    return;
                }
                fallDistance = 0.0f;
            } else if (movedDy < 0.0
                    && !world.inFluid(at.x(), position().y() - HALF_HEIGHT - 0.1, at.z())) {
                fallDistance = (float) (fallDistance - movedDy);
            }
            if (swept.collidingHorizontally() && headSpeed > CRASH_SPEED) {
                crashBreak();
                return;
            }
            setVelocity(velocityX() * GLIDE_DRAG, velocityY() * VERTICAL_DRAG,
                    velocityZ() * GLIDE_DRAG);
            applyHeadingLaw(at);
            return;
        }

        // The manual walk (the no-solver stub surfaces): the flat model's
        // block refusal — the zeroed axis is the horizontal collision
        // the crash consult reads.
        double newX = at.x() + velocityX();
        double newZ = at.z() + velocityZ();
        double newY = at.y() + velocityY();
        boolean hitWall = false;
        if (blocked(world, newX, at.z(), at.y() + HALF_HEIGHT)) {
            newX = at.x();
            setVelocity(0.0, velocityY(), velocityZ());
            hitWall = true;
        }
        if (blocked(world, newX, newZ, at.y() + HALF_HEIGHT)) {
            newZ = at.z();
            setVelocity(velocityX(), velocityY(), 0.0);
            hitWall = true;
        }

        boolean grounded;
        if (world.inFluid(at.x(), at.y(), at.z())) {
            grounded = false; // floating bodies are never grounded
        } else if (velocityY() <= 0 && world.isSolidAt(newX, newY - HALF_HEIGHT - GROUND_EPSILON, newZ)) {
            newY = Math.floor(newY - HALF_HEIGHT - GROUND_EPSILON) + 1.0 + HALF_HEIGHT;
            setVelocity(velocityX(), 0.0, velocityZ());
            grounded = true;
        } else {
            grounded = false;
        }

        // THE FALL DEBT'S LANDING (the manual walk's own form of the
        // reference's checkFallDamage consult — the same law the solver
        // path carries above).
        double movedDy = newY - at.y();
        if (grounded && !wasGrounded) {
            if (fallDistance > FALL_BREAK_DISTANCE) {
                fallDistance = 0.0f;
                crashBreak();
                return;
            }
            fallDistance = 0.0f;
        } else if (movedDy < 0.0
                && !world.inFluid(at.x(), newY - HALF_HEIGHT - 0.1, at.z())) {
            fallDistance = (float) (fallDistance - movedDy);
        }

        // THE CRASH / THE GLIDE (lines 311-314): the wall hit at the
        // pre-move speed breaks the hull; the survivor glides.
        if (hitWall && headSpeed > CRASH_SPEED) {
            crashBreak();
            return;
        }
        setVelocity(velocityX() * GLIDE_DRAG, velocityY() * VERTICAL_DRAG,
                velocityZ() * GLIDE_DRAG);
        commit(newX, newY, newZ, grounded);
        applyHeadingLaw(at);
    }

    /**
     * THE HEADING LAW (tick lines 328-346, the 2026-11-08 census row
     * BB-2): the pitch zeroes, and the yaw follows the MOVEMENT — the
     * reference's own {@code fastAtan2} over the NEGATED displacement
     * ({@code lastX − x}, {@code lastZ − z}), NEVER the rider's look
     * (the pre-fix followRiderLook adaptation retired whole) — with the
     * turn clamped at ±20°/tick; a still hull (the squared displacement
     * under 0.001) keeps its heading.
     */
    private void applyHeadingLaw(Position tickStart) {
        double d18 = tickStart.x() - position().x();
        double d21 = tickStart.z() - position().z();
        double d15 = rotation().yaw();
        if (d18 * d18 + d21 * d21 > HEADING_MOVE_GATE) {
            d15 = VehicleMath.fastAtan2(d21, d18) * 180.0 / Math.PI;
        }
        double d23 = VehicleMath.wrapDegrees(d15 - rotation().yaw());
        if (d23 > HEADING_CLAMP) {
            d23 = HEADING_CLAMP;
        }
        if (d23 < -HEADING_CLAMP) {
            d23 = -HEADING_CLAMP;
        }
        setRotation(new Rotation((float) (rotation().yaw() + d23), 0.0f));
    }

    @Override
    public double seatMountHeight() {
        // BoatEntity.getMountHeight line 79: the hull's seat sits 0.3
        // below the waterline — the rider's legs fold into the hull.
        return -0.3;
    }

    @Override
    public Position seatAnchor(double riderRideHeight) {
        // BoatEntity.updateRiderPositon lines 367-371: the rider sits 0.4
        // from the hull's centre along the heading's own (cos, sin)
        // quadrant (the reference's verbatim form over its own atan2
        // yaw convention), at the boat's own -0.3 mount height plus the
        // rider's own per-family offset.
        double yawRad = Math.toRadians(rotation().yaw());
        return new Position(
                position().x() + Math.cos(yawRad) * 0.4,
                position().y() + seatMountHeight() + riderRideHeight,
                position().z() + Math.sin(yawRad) * 0.4);
    }
}
