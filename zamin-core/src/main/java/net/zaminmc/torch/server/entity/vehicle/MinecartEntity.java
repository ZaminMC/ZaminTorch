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
    /**
     * The rider's from-rest W-kick (moveOnRail lines 393-395: the 0.1
     * handed the state along the rider's own look — the only drive a
     * cart ever gains from the input; the pre-fix engine's continuous
     * +0.02 RAIL_ACCELERATION drive retired with the kick's landing).
     */
    public static final double RIDER_KICK = 0.1;
    /**
     * The carried state's own ceiling (the reference's rail projection,
     * moveOnRail lines 381-383: {@code d5 > 2.0 → 2.0}) — the ONLY bound
     * on the velocity state; the position's per-tick advance clamps at
     * {@link #getMaxSpeedOnRail()} at the walk's own tail instead, never
     * on the state the wire and the pushes read.
     */
    public static final double RAIL_STATE_CEILING = 2.0;
    /** The off-rail grounded drag (moveOffRail line 326: the 0.5). */
    public static final double GROUND_DRAG = 0.5;
    /** The off-rail air drag (moveOffRail line 335: the 0.95). */
    public static final double AIR_DRAG = 0.95;
    // The powered-rail walk (Slice 38 — the reference's own numbers:
    // MinecartEntity.moveAlongTrack line 482's 0.06 boost, getMaxSpeed's
    // 0.4 on-rail move clamp, lines 402-414's brake — under 0.03 the full
    // stop, over it the 0.5 halving, lines 485-494's wall launch ±0.02).
    /** The powered golden rail's boost per tick (line 482's 0.06). */
    public static final double POWERED_RAIL_BOOST = 0.06;
    /** The powered golden rail's from-rest wall launch (lines 485-494). */
    public static final double POWERED_RAIL_LAUNCH = 0.02;
    /**
     * The rider's move scale (moveOnRail lines 438-441): a ridden cart
     * advances at 0.75 of its velocity state each tick — the seat's own
     * law, landed with the seated-mob model's drag row.
     */
    public static final double RIDER_MOVE_SCALE = 0.75;
    /**
     * The ridden drag (applySlowdown line 498 — the velocity triple
     * {@code 0.997 / 0.0 / 0.997} a seated rider carries: the cart keeps
     * its roll, the reference's own "a ridden cart barely slows").
     */
    public static final double RIDDEN_DRAG = 0.997;
    /**
     * The un-ridden drag (applySlowdown line 503 — the empty cart's
     * {@code 0.96 / 0.0 / 0.96}: an abandoned cart grinds to a dead
     * stop within the tick-count of a vanilla redstone clock's lag).
     */
    public static final double UNRIDDEN_DRAG = 0.96;
    /** The plain family's on-rail move clamp (getMaxSpeed line 314: 0.4). */
    public static final double MAX_SPEED_ON_RAIL = 0.4;
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
     * The on-rail move clamp (getMaxSpeed lines 313-315: the plain
     * family's 0.4): the position's per-tick advance — the carried
     * velocity state is NEVER clamped by it (the reference's clamp
     * lands on {@code move(d22, 0, d23)}'s arguments alone, so a
     * booster-saturated state rides above the clamp it moves at).
     * The furnace variant overrides with its own 0.2.
     */
    protected double getMaxSpeedOnRail() {
        return MAX_SPEED_ON_RAIL;
    }

    /**
     * The drag on the carried velocity (the reference's own
     * {@code applySlowdown} — moveOnRail line 454's call site, AFTER the
     * move): the RIDER-GATED triple, {@code 0.997 / 0.0 / 0.997} when a
     * body holds the seat and {@code 0.96 / 0.0 / 0.96} when the cart
     * runs empty (MinecartEntity.applySlowdown lines 497-506) — an
     * abandoned cart stops dead in a few dozen ticks while a ridden one
     * keeps its roll; the Y arm zeroes the vertical the rail walk left.
     * The inventory family (the chest and hopper carts) overrides with
     * the fullness walk — NO rider gate, the reference's own override
     * replaces the base whole; the furnace's override runs its push arm
     * and then THIS triple on top (its {@code super.applySlowdown()}).
     */
    protected void applySlowdown() {
        double drag = hasPassenger() ? RIDDEN_DRAG : UNRIDDEN_DRAG;
        setVelocity(velocityX() * drag, velocityY() * 0.0, velocityZ() * drag);
    }

    /** The piston push (VIII13): the cart's own solver walk with the cart box. */
    @Override
    protected void pushMove(double dx, double dy, double dz) {
        solverMove(query, HALF_HEIGHT, CART_WIDTH, dx, dy, dz);
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

        // THE STATE (the reference's velocity field): the rail projection
        // (moveOnRail lines 379-386) reads the 2-D magnitude — a diagonal
        // push folds its whole magnitude onto the travel axis — with the
        // ceiling 2.0 (lines 381-383) the only bound. The direction rides
        // the axis component's sign; the zero-component edge (a purely
        // perpendicular push) falls to the look pick below — the engine's
        // documented adaptation for the reference's canonical-shape unit.
        double velocityAlong = rail == AXIS_X ? velocityX() : velocityZ();
        double speed = Math.sqrt(velocityX() * velocityX() + velocityZ() * velocityZ());
        if (speed > RAIL_STATE_CEILING) {
            speed = RAIL_STATE_CEILING;
        }
        double direction = velocityAlong >= 0 ? 1.0 : -1.0;
        if (speed < 0.01) {
            // Starting from rest: the direction consult keeps the
            // travel-facing pick (the yaw bookkeeping and the continues
            // probe) — the state stays zero, an un-ridden cart never
            // self-launches (the reference's own answer: no rider, no
            // kick, no move).
            direction = lookDirection(rail);
            speed = 0.0;
        }
        // THE RIDER'S W-KICK (moveOnRail lines 387-399, read 1:1): a
        // mounted body holding the forward input, from rest alone (the
        // squared state under 0.01 — the second tick's 0.0997 still
        // inside the band), kicks 0.1 along the RIDER'S OWN LOOK — and
        // CANCELS the brake flag (line 395's {@code flag1 = false}: a
        // kicked cart on an unpowered golden rail never takes the
        // halving that tick). A rolling cart gains NOTHING from the
        // input — the vanilla W key never accelerates a moving cart —
        // and a riderless cart never kicks at all (the consult reads
        // the rider's own forward speed, {@code this.rider instanceof
        // LivingEntity} the gate). The pre-fix engine's continuous
        // +0.02 drive (the W-key adaptation) retired whole: it fed any
        // steered cart forever, rider or none, rolling or not.
        boolean brakeCancelled = false;
        if (hasPassenger() && input.forward() > 0.0f
                && velocityX() * velocityX() + velocityZ() * velocityZ() < 0.01) {
            float riderYaw = input.riderYaw();
            double radians = Math.toRadians(Float.isNaN(riderYaw)
                    ? rotation().yaw() : riderYaw);
            double lookAlong = rail == AXIS_X ? -Math.sin(radians) : Math.cos(radians);
            if (lookAlong != 0.0) {
                // The signed add onto the axis state (the reference's 2-D
                // add re-points whole onto the axis at the next
                // projection — the axis model folds it at the kick
                // itself; the exactly-perpendicular look never starts
                // the cart, the axis model's own documented edge).
                double kicked = velocityAlong + lookAlong * RIDER_KICK;
                direction = kicked >= 0.0 ? 1.0 : -1.0;
                speed = Math.abs(kicked);
            }
            brakeCancelled = true;
        }
        // THE BRAKE (flag1 — moveOnRail lines 402-414, the consult BEFORE
        // the move): the unpowered golden rail halves the rolling state,
        // and under the 0.03 band the full stop — unless the kick stood
        // the flag down this tick (line 395's cancel).
        int railPower = world.railPowerAt(
                cellX + 0.5, cellY + 0.06, cellZ + 0.5);
        if (railPower < 0 && !brakeCancelled) {
            if (speed < BRAKE_STOP_SPEED) {
                speed = 0.0;
            } else {
                speed *= BRAKE_FACTOR;
            }
        }

        // THE MOVE (moveOnRail lines 438-446): the rider's 0.75 scale on
        // the state, then the getMaxSpeed clamp — the position's per-tick
        // advance, never the carried state (a booster-saturated cart
        // rides above the clamp it moves at).
        double moveScale = hasPassenger() ? RIDER_MOVE_SCALE : 1.0;
        double moveMagnitude = Math.min(speed * moveScale, getMaxSpeedOnRail());

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
            moved = moveMagnitude * direction;
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
                moved = moveMagnitude * direction;
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

        // THE CARRY — the UN-clamped state rides the travel axis (the
        // reference's velocity field keeps the projection's own
        // magnitude; the move clamp never touches it).
        if (travel == AXIS_X) {
            setVelocity(speed * direction, 0.0, 0.0);
        } else {
            setVelocity(0.0, 0.0, speed * direction);
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

        // THE DRAG (the reference's own call site — moveOnRail line 454,
        // AFTER the move): the rider-gated triple lands on the CARRY
        // alone — this tick's move already ran at the un-damped state.
        applySlowdown();

        // THE BOOST (flag — moveOnRail lines 478-495, the consult AFTER
        // the drag, the walk's own tail): the powered golden rail adds
        // 0.06 along the rolling direction (the post-drag state, so the
        // addition rides un-decayed into the next tick's projection);
        // from rest the WALL-SIDE LAUNCH — the neighbor-solid consult,
        // the cart rolls away from the block behind it at ±0.02, and a
        // wall-less rest never launches at all (the vanilla powered
        // rail holds a resting cart until a block stands behind it).
        if (railPower > 0) {
            double rolling = Math.sqrt(velocityX() * velocityX() + velocityZ() * velocityZ());
            if (rolling > 0.01) {
                setVelocity(
                        velocityX() + velocityX() / rolling * POWERED_RAIL_BOOST,
                        velocityY(),
                        velocityZ() + velocityZ() / rolling * POWERED_RAIL_BOOST);
            } else {
                double launch = wallLaunch(world, cellX, cellY, cellZ, rail);
                if (rail == AXIS_X) {
                    setVelocity(launch, 0.0, 0.0);
                } else {
                    setVelocity(0.0, 0.0, launch);
                }
            }
        }
    }

    /**
     * The powered rail's from-rest launch (moveOnRail lines 485-494): the
     * neighbor-solid consult on the travel axis — the west wall launches
     * east (+0.02), the east wall west; the north wall south, the south
     * wall north; no wall, no launch. The consult is the block-level
     * solid (the reference's own {@code getBlockState(pos.west())
     * .getBlock().isSolid()}), the engine's WorldQuery point form.
     */
    private double wallLaunch(WorldQuery world, int cellX, int cellY, int cellZ, int rail) {
        if (rail == AXIS_X) {
            if (world.isSolid(cellX - 1 + 0.5, cellY + 0.5, cellZ + 0.5)) {
                return POWERED_RAIL_LAUNCH; // the west wall: launch east
            }
            if (world.isSolid(cellX + 1 + 0.5, cellY + 0.5, cellZ + 0.5)) {
                return -POWERED_RAIL_LAUNCH; // the east wall: launch west
            }
        } else {
            if (world.isSolid(cellX + 0.5, cellY + 0.5, cellZ - 1 + 0.5)) {
                return POWERED_RAIL_LAUNCH; // the north wall: launch south
            }
            if (world.isSolid(cellX + 0.5, cellY + 0.5, cellZ + 1 + 0.5)) {
                return -POWERED_RAIL_LAUNCH; // the south wall: launch north
            }
        }
        return 0.0;
    }

    /**
     * The off-rail tail (moveOffRail lines 320-337, 1:1 over the engine's
     * solver application): the tick head's RAW gravity (MinecartEntity.tick
     * line 249's −0.04 — the engine's invented pre-multiplied 0.98 on the
     * vertical retired), the ±getMaxSpeed clamp on the horizontal (lines
     * 321-323 — the same 0.4/0.2 family the on-rail move clamps at, the
     * clamp writing the STATE whole), the GROUNDED 0.5 on all three axes
     * PRE-move (lines 324-328 — the carried onGround consult, the heavy
     * land friction), the move itself, and the AIR 0.95 POST-move on all
     * three (lines 333-337 — the pre-fix engine's unconditional 0.5
     * retired: an airborne cart coasts at 0.95, a grounded one pays the
     * pre-move halving alone).
     */
    private void tickOffRail(Position at) {
        // The tick head's gravity (line 249): the raw −0.04, no drag.
        setVelocity(velocityX(), velocityY() - GRAVITY_PER_TICK, velocityZ());
        // Lines 321-323: the ±getMaxSpeed clamp on the horizontal (the
        // state itself clamped — the reference writes the fields).
        double max = getMaxSpeedOnRail();
        setVelocity(Math.max(-max, Math.min(max, velocityX())),
                velocityY(), Math.max(-max, Math.min(max, velocityZ())));
        // Lines 324-328: the GROUNDED 0.5 on all three — the carried
        // onGround consult (the last landing's own state).
        if (onGround()) {
            setVelocity(velocityX() * GROUND_DRAG, velocityY() * GROUND_DRAG,
                    velocityZ() * GROUND_DRAG);
        }
        // THE MOVE — the solver application when the query provides the
        // collision surface (the commit, the onGround, and the clipped-
        // axis zeroing inside), the manual walk otherwise.
        net.zaminmc.torch.server.physics.EntityMoveSolver.Result swept =
                solverMove(query, HALF_HEIGHT, CART_WIDTH,
                        velocityX(), velocityY(), velocityZ());
        if (swept != null) {
            // Lines 333-337: the AIR 0.95 on all three (the post-move
            // onGround consult — a landed cart pays nothing further).
            if (!onGround()) {
                setVelocity(velocityX() * AIR_DRAG, velocityY() * AIR_DRAG,
                        velocityZ() * AIR_DRAG);
            }
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
        if (!grounded) {
            setVelocity(velocityX() * AIR_DRAG, velocityY() * AIR_DRAG,
                    velocityZ() * AIR_DRAG);
        }
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
