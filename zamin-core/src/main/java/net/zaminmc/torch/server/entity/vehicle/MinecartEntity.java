package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;

import java.util.Random;

/**
 * The minecart (community entities.json object type 10): it rides rails —
 * the rail block under the body names the axis (the metadata's flat
 * orientations), the steer accelerates along it, and an L-corner turns
 * when the current axis runs out. Off the rail it grinds to a halt under
 * heavy drag and falls with gravity.
 *
 * <p>Slice 21a opens the family: the chest variant extends this body with
 * the 27-slot container and its own rail drag (the reference's
 * {@code applySlowdown} fullness walk).</p>
 */
public class MinecartEntity extends VehicleEntity {

    public static final double HALF_HEIGHT = 0.35;

    /**
     * The cart's width (the physics-solver slice — the reference's own
     * {@code setSize(0.98F, 0.7F)}: the swept box's X/Z span).
     */
    public static final double CART_WIDTH = 0.98;
    /** The rail top offset: the cart rides just above the rail cell floor. */
    public static final double RIDE_Y_OFFSET = 0.06;
    /** The steer acceleration per tick on rails. */
    public static final double RAIL_ACCELERATION = 0.02;
    /** The historical uncoupled top speed on flat rails. */
    public static final double RAIL_SPEED_CAP = 0.3;
    /** Off-rail drag (the historical ground friction). */
    public static final double OFF_RAIL_DRAG = 0.5;
    // The powered-rail walk (Slice 38 — the reference's own numbers:
    // MinecartEntity.moveAlongTrack line 477's 0.06 boost, getMaxSpeed's
    // 0.4 on-rail cap, lines 428-437's brake — under 0.03 the full stop,
    // over it the 0.5 halving).
    /** The powered golden/activator rail's boost per tick. */
    public static final double POWERED_RAIL_BOOST = 0.06;
    /** The reference's on-rail getMaxSpeed cap. */
    public static final double POWERED_RAIL_CAP = 0.4;
    /** The unpowered golden rail's full-stop band. */
    public static final double BRAKE_STOP_SPEED = 0.03;
    /** The unpowered golden rail's halving brake. */
    public static final double BRAKE_FACTOR = 0.5;

    /** The flat rail orientations (the engine's rail-axis band). */
    private static final int AXIS_Z = 0; // north-south rail: travel along Z
    private static final int AXIS_X = 1; // east-west rail: travel along X
    private static final int AXIS_NONE = -1;

    private int lastAxis = AXIS_NONE;

    public MinecartEntity(int entityId, Position position, Random random) {
        this(entityId, Kind.MINECART, position, random);
    }

    /** The family constructor (the chest variant names its own kind). */
    protected MinecartEntity(int entityId, Kind kind, Position position, Random random) {
        super(entityId, kind, position, random);
    }

    /**
     * The wire's minecart type index (TrackedEntity lines 413-415 — the
     * Spawn Object objectData field): 0 rideable, 1 chest, 2 furnace,
     * 3 TNT, 4 spawner, 5 hopper, 6 command block. The chest variant
     * overrides with 1; a nonzero index also arms the velocity triple
     * (AddEntityS2CPacket's {@code data > 0} shape).
     */
    public int minecartTypeIndex() {
        return 0;
    }

    /**
     * The on-rail velocity multiplier the kind applies every tick (the
     * reference's {@code applySlowdown} — 1.0 for the plain cart whose
     * own walk keeps the historical {@code 0.997}); the chest variant
     * overrides with the fullness drag.
     */
    protected double railDragFactor() {
        return 1.0;
    }

    @Override
    protected void tickKind(WorldQuery world) {
        Position at = position();
        SteerInput input = consumeSteer();

        int rail = railAxisAt(world, at);
        if (rail == AXIS_NONE) {
            tickOffRail(at);
            return;
        }

        int cellX = (int) Math.floor(at.x());
        int cellZ = (int) Math.floor(at.z());
        int cellY = (int) Math.floor(at.y() + 0.4); // the rail's own cell

        // Speed and direction along the rail axis.
        double velocityAlong = rail == AXIS_X ? velocityX() : velocityZ();
        double speed = Math.min(Math.abs(velocityAlong), RAIL_SPEED_CAP);
        double direction = velocityAlong >= 0 ? 1.0 : -1.0;
        if (speed < 0.01) {
            // Starting from rest: the rider's look names the way ahead.
            direction = lookDirection(rail);
            speed = 0.0;
        }
        if (input.forward() != 0.0f) {
            speed = Math.min(speed + RAIL_ACCELERATION * Math.abs(input.forward()), RAIL_SPEED_CAP);
        }
        // The powered-rail walk (Slice 38 — MinecartEntity.moveAlongTrack
        // lines 345-349, 477-494): the powered golden/activator rail adds
        // the 0.06 boost along the travel (capped at the reference's own
        // 0.4 getMaxSpeed); the unpowered golden rail is the BRAKE — under
        // 0.03 the cart stops dead, over it the velocity halves.
        int railPower = world.railPowerAt(
                cellX + 0.5, cellY + 0.06, cellZ + 0.5);
        if (railPower > 0) {
            if (speed > 0.01) {
                speed = Math.min(speed + POWERED_RAIL_BOOST, POWERED_RAIL_CAP);
            } else {
                // From rest on a powered rail: the shape's own kick
                // (lines 481-494 — the east-west/north-south launch arms).
                speed = Math.min(POWERED_RAIL_BOOST, POWERED_RAIL_CAP);
            }
        } else if (railPower < 0) {
            if (speed < BRAKE_STOP_SPEED) {
                speed = 0.0;
            } else {
                speed *= BRAKE_FACTOR;
            }
        }
        // The kind's own drag (the reference's applySlowdown runs after the
        // move; our speed-carry model applies the same factor to the next
        // tick's carried speed).
        speed *= railDragFactor();

        // The next cell on the axis: a rail continues it; otherwise an
        // L-corner turns onto the perpendicular rail, or the track ends.
        int nextCellAlong = (rail == AXIS_X ? cellX : cellZ) + (int) direction;
        boolean continues = railAxisOfCell(world, rail == AXIS_X ? nextCellAlong : cellX,
                cellY, rail == AXIS_Z ? nextCellAlong : cellZ) != AXIS_NONE;
        double nx = at.x();
        double nz = at.z();
        int travel = rail;
        double moved;
        if (continues) {
            moved = speed * direction;
            if (rail == AXIS_X) {
                nx = at.x() + moved;
                nz = cellZ + 0.5;
            } else {
                nz = at.z() + moved;
                nx = cellX + 0.5;
            }
        } else {
            int crossDelta = crossRailDirection(world, cellX, cellY, cellZ, rail);
            if (crossDelta != 0) {
                // The corner: snap to the cell center, then head across.
                travel = rail == AXIS_X ? AXIS_Z : AXIS_X;
                direction = crossDelta;
                moved = speed * direction;
                if (travel == AXIS_X) {
                    nx = cellX + 0.5 + moved;
                    nz = cellZ + 0.5;
                } else {
                    nz = cellZ + 0.5 + moved;
                    nx = cellX + 0.5;
                }
            } else {
                // Track ends: stop dead on the rail line.
                if (rail == AXIS_X) {
                    nx = cellX + 0.5;
                } else {
                    nz = cellZ + 0.5;
                }
                speed = 0.0;
                moved = 0.0;
            }
        }

        // The velocity bookkeeping rides the travel axis; the cart's yaw
        // faces the travel (the observer-facing heading).
        if (travel == AXIS_X) {
            setVelocity(moved, 0.0, 0.0);
        } else {
            setVelocity(0.0, 0.0, moved);
        }
        lastAxis = travel;
        // The cart's yaw faces the travel (the observer-facing heading);
        // a zero-move tick (the end-of-track halt) keeps the previous
        // heading — flipping it here would reverse the next start.
        if (moved != 0.0) {
            float yaw = travel == AXIS_X
                    ? (moved > 0 ? -90.0f : 90.0f)
                    : (moved > 0 ? 0.0f : 180.0f);
            setRotation(new Rotation(yaw, 0.0f));
        }
        // Flat rails: the body rides the rail cell's floor + the offset.
        commit(nx, cellY + RIDE_Y_OFFSET, nz, true);
    }

    /** The off-rail tail: gravity + heavy drag, the boat's land rules. */
    private void tickOffRail(Position at) {
        setVelocity(velocityX(), (velocityY() - GRAVITY_PER_TICK) * 0.98, velocityZ());
        // THE SWEPT-BOX APPLICATION (the physics-solver slice): the
        // cart's own Entity.move — the 0.98 box (the reference's own
        // setSize) clipped against every collision box its swept shape
        // touches, the landing the box's own footprint — when the query
        // provides the collision surface.
        net.zaminmc.torch.server.physics.EntityMoveSolver.Result swept =
                solverMove(query, HALF_HEIGHT, CART_WIDTH,
                        velocityX(), velocityY(), velocityZ());
        if (swept != null) {
            decay(OFF_RAIL_DRAG);
            return;
        }
        double newX = at.x() + velocityX();
        double newZ = at.z() + velocityZ();
        double newY = at.y() + velocityY();
        boolean grounded = false;
        if (velocityY() <= 0 && query.isSolidAt(newX, newY - HALF_HEIGHT - GROUND_EPSILON, newZ)) {
            newY = Math.floor(newY - HALF_HEIGHT - GROUND_EPSILON) + 1.0 + HALF_HEIGHT;
            setVelocity(velocityX(), 0.0, velocityZ());
            grounded = true;
        }
        decay(OFF_RAIL_DRAG);
        commit(newX, newY, newZ, grounded);
    }

    /** The look's cardinal sign along the rail axis (the "ahead" of W). */
    private double lookDirection(int rail) {
        double radians = Math.toRadians(rotation().yaw());
        double along = rail == AXIS_X ? -Math.sin(radians) : Math.cos(radians);
        return along >= 0 ? 1.0 : -1.0;
    }

    /** @return the perpendicular rail's direction at this cell, 0 if none. */
    private int crossRailDirection(WorldQuery world, int cellX, int cellY, int cellZ, int rail) {
        int other = rail == AXIS_X ? AXIS_Z : AXIS_X;
        int[] deltas = {1, -1};
        for (int delta : deltas) {
            int probeX = other == AXIS_X ? cellX + delta : cellX;
            int probeZ = other == AXIS_Z ? cellZ + delta : cellZ;
            if (railAxisOfCell(world, probeX, cellY, probeZ) != AXIS_NONE) {
                return delta;
            }
        }
        return 0;
    }

    private int railAxisOfCell(WorldQuery world, int x, int y, int z) {
        return world.railAxisAt(x + 0.5, y + 0.06, z + 0.5);
    }

    /** The rail axis under a point (protected for the variants' own walks). */
    protected int railAxisAt(WorldQuery world, Position at) {
        return world.railAxisAt(at.x(), at.y() + 0.4, at.z());
    }
}
