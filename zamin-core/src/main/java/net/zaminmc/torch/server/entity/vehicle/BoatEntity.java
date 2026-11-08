package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;

import java.util.Random;

/**
 * The boat (community entities.json object type 1): it floats — in a fluid
 * the body rests on the surface with the glide drag — and the rider
 * paddles (Wave BS — the reference's own steer model, BoatEntity.tick
 * lines 259-284: the drive the momentum's own 0.05 per tick along the
 * rider's look rotated by the strafe, the momentum ramping 0.07-0.35
 * over its 35-tick curve, the horizontal capped 0.35); on land it slides
 * to a stop under the grounded halving and falls with the raw gravity.
 * The high-speed wall hit breaks the hull (lines 311-326).
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
     * The momentum field (the reference's own {@code speedBoost} —
     * Java's 0.0 first-tick value included: a fresh hull warms up to
     * the 0.07 idle floor over its first 35 ticks, the reference's own
     * curve).
     */
    private double speedBoost;

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
        boolean inWater = world.inFluid(at.x(), at.y(), at.z());

        // THE HEAD SPEED (the reference's d9, line 201 — measured before
        // every arm; the crash consult reads this, never the post-paddle
        // state).
        double headSpeed = Math.sqrt(velocityX() * velocityX() + velocityZ() * velocityZ());

        // THE VERTICAL (the landed water rest — the body holds the
        // surface, the reference's 5-slice buoyancy bob the documented
        // verifier row; on land the RAW gravity, tick line 249's own
        // −0.04 — the pre-fix engine's pre-multiplied (v − 0.04) × 0.98
        // retired with the OR wave's form).
        if (inWater) {
            setVelocity(velocityX(), 0.0, velocityZ());
        } else {
            setVelocity(velocityX(), velocityY() - GRAVITY_PER_TICK, velocityZ());
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
            if (swept.collidingHorizontally() && headSpeed > CRASH_SPEED) {
                crashBreak();
                return;
            }
            setVelocity(velocityX() * GLIDE_DRAG, velocityY() * VERTICAL_DRAG,
                    velocityZ() * GLIDE_DRAG);
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
        if (inWater) {
            grounded = false; // floating bodies are never grounded
        } else if (velocityY() <= 0 && world.isSolidAt(newX, newY - HALF_HEIGHT - GROUND_EPSILON, newZ)) {
            newY = Math.floor(newY - HALF_HEIGHT - GROUND_EPSILON) + 1.0 + HALF_HEIGHT;
            setVelocity(velocityX(), 0.0, velocityZ());
            grounded = true;
        } else {
            grounded = false;
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
    }

    /** The seat: the boat's heading tracks the rider's look every tick. */
    public void followRiderLook(Rotation riderLook) {
        setRotation(new Rotation(riderLook.yaw(), 0.0f));
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
        // forward of the hull's center along the heading (cos/sin of the
        // yaw in degrees), at the boat's own -0.3 mount height plus the
        // rider's own per-family offset.
        double yawRad = Math.toRadians(rotation().yaw());
        return new Position(
                position().x() + Math.cos(yawRad) * 0.4,
                position().y() + seatMountHeight() + riderRideHeight,
                position().z() + Math.sin(yawRad) * 0.4);
    }
}
