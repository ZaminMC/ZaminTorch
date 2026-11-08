package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;

import java.util.Random;

/**
 * The minecart (community entities.json object type 10): it rides rails —
 * the rail block under the body names the travel (the shape's own axis,
 * the ascent's climb face, the curve's diagonal), the steer accelerates
 * along it, and an L-corner turns when the current axis runs out. Off the
 * rail it grinds to a halt under heavy drag and falls with gravity.
 *
 * <p>Wave SL carries the shape model whole: the reference's own
 * ADJACENT_RAIL_POSITIONS_BY_SHAPE geometry (MinecartEntity lines 34-44)
 * — the four ASCENT shapes now roll (the ±0.0078125 climb arm, the
 * interpolated ride height, the d14 grade trim) and the four CURVE
 * shapes walk (the mid-rail diagonal projection, the exit re-point),
 * where the pre-shape walk flattened the ascents to their axis and read
 * the curves as dead cells.</p>
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
    /**
     * The ascent's climb arm (moveOnRail lines 350-368: the d0 constant
     * 0.0078125 — the flat ± the axis component each tick on an
     * ascending rail's cell, resisting the climb, aiding the descent).
     */
    public static final double SLOPE_ACCEL = 0.0078125;
    /**
     * The grade's trim (moveOnRail lines 456-461: {@code d14 = (vec3d.y
     * - vec3d1.y) * 0.05} — the tick's altitude change scaled onto the
     * dragged state: a climb of one full cell costs 5% of the speed per
     * tick, a descent pays it back; the exact reference form, the
     * (speed + d14) < 0 flip allowed — a slow cart onto a big step
     * rolls back).
     */
    public static final double SLOPE_TRIM = 0.05;

    /** The reference's rail-shape ids (AbstractRailBlock.Shape). */
    static final int SHAPE_NORTH_SOUTH = 0;
    static final int SHAPE_EAST_WEST = 1;
    static final int SHAPE_ASCENDING_EAST = 2;
    static final int SHAPE_ASCENDING_WEST = 3;
    static final int SHAPE_ASCENDING_NORTH = 4;
    static final int SHAPE_ASCENDING_SOUTH = 5;
    static final int SHAPE_FIRST_CURVE = 6;

    /**
     * The reference's own rail geometry table (MinecartEntity lines
     * 34-44 — ADJACENT_RAIL_POSITIONS_BY_SHAPE): each shape's two rail
     * ends as {x, y, z} cell deltas — the end pair names the travel
     * line (the curve's diagonal, the slope's low/high ends) and the
     * y-deltas the slope's own arithmetic.
     */
    static final int[][][] ADJACENT_RAIL_POSITIONS = {
            {{0, 0, -1}, {0, 0, 1}},      // 0 north_south
            {{-1, 0, 0}, {1, 0, 0}},      // 1 east_west
            {{-1, -1, 0}, {1, 0, 0}},     // 2 ascending_east
            {{-1, 0, 0}, {1, -1, 0}},     // 3 ascending_west
            {{0, 0, -1}, {0, -1, 1}},     // 4 ascending_north
            {{0, -1, -1}, {0, 0, 1}},     // 5 ascending_south
            {{0, 0, 1}, {1, 0, 0}},       // 6 south_east
            {{0, 0, 1}, {-1, 0, 0}},      // 7 south_west
            {{0, 0, -1}, {-1, 0, 0}},     // 8 north_west
            {{0, 0, -1}, {1, 0, 0}},      // 9 north_east
    };

    /** The flat rail orientations (the engine's rail-axis band). */
    private static final int AXIS_Z = 0; // north-south rail: travel along Z
    private static final int AXIS_X = 1; // east-west rail: travel along X
    private static final int AXIS_NONE = -1;

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

        // THE RAIL SEARCH (the reference's two-band priority form — tick
        // lines 258-263: the cart's own y-cell first, and when the rail
        // stands one cell BELOW, that one is the rail — the slope's high
        // end (the cart rides a full cell above the ascent's own cell)
        // and the falling coupling both resolve here; the pre-shape
        // walk's single y+0.4 band missed both).
        int cellX = (int) Math.floor(at.x());
        int cellZ = (int) Math.floor(at.z());
        int cellY = (int) Math.floor(at.y());
        int shape = world.railShapeAt(at.x(), cellY - 0.5, at.z());
        if (shape >= 0) {
            cellY--;
        } else {
            shape = world.railShapeAt(at.x(), cellY + 0.5, at.z());
        }
        if (shape < 0) {
            tickOffRail(at);
            return;
        }

        if (shape >= SHAPE_FIRST_CURVE) {
            tickOnCurve(world, at, cellX, cellY, cellZ, shape, input);
            return;
        }

        int rail = axisOf(shape);

        // THE ASCENT'S CLIMB ARM (moveOnRail lines 350-368): the flat
        // ±0.0078125 on the axis component BEFORE the projection — the
        // reference's own d0, eastward ascents resisting +X, westward
        // ones aiding it (and the y++ the walk's own mid-tick altitude
        // below).
        if (shape == SHAPE_ASCENDING_EAST) {
            setVelocity(velocityX() - SLOPE_ACCEL, velocityY(), velocityZ());
        } else if (shape == SHAPE_ASCENDING_WEST) {
            setVelocity(velocityX() + SLOPE_ACCEL, velocityY(), velocityZ());
        } else if (shape == SHAPE_ASCENDING_NORTH) {
            setVelocity(velocityX(), velocityY(), velocityZ() + SLOPE_ACCEL);
        } else if (shape == SHAPE_ASCENDING_SOUTH) {
            setVelocity(velocityX(), velocityY(), velocityZ() - SLOPE_ACCEL);
        }
        // THE WALK'S MID-TICK ALTITUDE (line 341's this.y = pos.getY()
        // + the ascent cases' y++): one whole cell up on an ascending
        // rail — the post-move snap below resolves the final cell.
        double walkY = cellY + (isAscending(shape) ? 1.0 : 0.0);
        // THE TICK-HEAD SNAP (the reference's vec3d — snapPositionToRail
        // at the tick head, line 340): the interpolated ride height at
        // the CURRENT position over the CURRENT cell, the d14 trim's own
        // pre-tick read — a just-coupled body (its raw y still off the
        // line) carries NO spurious grade: both the trim's sides read
        // the snap, never the raw position.
        double headSnapY = cellY + RIDE_Y_OFFSET
                + (isAscending(shape)
                        ? slopeProgressTowardHighFace(shape, cellX, cellZ, at.x(), at.z())
                        : 0.0);

        // THE STATE (the reference's velocity field): the rail projection
        // (moveOnRail lines 379-386) reads the 2-D magnitude — a diagonal
        // push folds its whole magnitude onto the travel axis — with the
        // ceiling 2.0 (lines 381-383) the only bound. The direction rides
        // the axis component's sign; the zero-component edge (a purely
        // perpendicular push) falls to the +1 default — the reference's
        // own un-flipped d4 (the end0→end1 direction).
        double velocityAlong = rail == AXIS_X ? velocityX() : velocityZ();
        double speed = Math.sqrt(velocityX() * velocityX() + velocityZ() * velocityZ());
        if (speed > RAIL_STATE_CEILING) {
            speed = RAIL_STATE_CEILING;
        }
        double direction = velocityAlong >= 0 ? 1.0 : -1.0;
        if (speed < 0.01 && !isAscending(shape)) {
            // Starting from rest on a FLAT rail: the direction consult
            // keeps the travel-facing pick (the yaw bookkeeping and the
            // continues probe) — the state stays zero, an un-ridden cart
            // never self-launches (the reference's own answer: no rider,
            // no kick, no move). THE ASCENT EXEMPT (Wave SL): the climb
            // arm's own ±0.0078125 from rest rides UNDER this band —
            // the reference has no floor at all, and a cart placed on an
            // ascent SLIDES (the arm is the slide's whole cause); the
            // floor stays the flat decay's own dead-stop approximation.
            direction = lookDirection(rail);
            speed = 0.0;
        }
        // THE RIDER'S W-KICK (moveOnRail lines 387-399, the 2-D add
        // whole): a mounted body holding the forward input, from rest
        // alone (the squared state under 0.01 — the second tick's 0.0997
        // still inside the band), kicks 0.1 along the RIDER'S OWN LOOK —
        // the full 2-D vector, the reference's own add: the PERPENDICULAR
        // component folds onto the travel line at the projection (a
        // perpendicular look still kicks — the axis's un-flipped +1
        // default, the reference's own d4=0 edge; the pre-fix engine
        // refused the kick whole) — and CANCELS the brake flag (line
        // 395's {@code flag1 = false}: a kicked cart on an unpowered
        // golden rail never takes the halving that tick). A rolling cart
        // gains NOTHING from the input — the vanilla W key never
        // accelerates a moving cart — and a riderless cart never kicks
        // at all (the consult reads the rider's own forward speed,
        // {@code this.rider instanceof LivingEntity} the gate). The
        // pre-fix engine's continuous +0.02 drive (the W-key
        // adaptation) retired whole: it fed any steered cart forever,
        // rider or none, rolling or not.
        boolean brakeCancelled = false;
        if (hasPassenger() && input.forward() > 0.0f
                && velocityX() * velocityX() + velocityZ() * velocityZ() < 0.01) {
            float riderYaw = input.riderYaw();
            double radians = Math.toRadians(Float.isNaN(riderYaw)
                    ? rotation().yaw() : riderYaw);
            double kickX = -Math.sin(radians) * RIDER_KICK;
            double kickZ = Math.cos(radians) * RIDER_KICK;
            double alongAfter = velocityAlong + (rail == AXIS_X ? kickX : kickZ);
            double perpAfter = (rail == AXIS_X ? velocityZ() : velocityX())
                    + (rail == AXIS_X ? kickZ : kickX);
            // The 2-D add's fold onto the axis: the whole magnitude
            // (the perpendicular component included — the reference's
            // next-tick projection re-points it whole), the direction
            // the along component's sign with the zero case the axis's
            // own +1 default.
            speed = Math.sqrt(alongAfter * alongAfter + perpAfter * perpAfter);
            direction = alongAfter >= 0.0 ? 1.0 : -1.0;
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

        // The next cell on the axis: a rail continues it (the shape's own
        // exit level — an ascent heading to its high face exits one cell
        // up); otherwise an L-corner turns onto the perpendicular rail,
        // or the track ends.
        boolean continues = railContinues(world, shape, cellX, cellY, cellZ, rail, direction);
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
        // The cart's yaw faces the travel (the observer-facing heading);
        // a zero-move tick (the end-of-track halt) keeps the previous
        // heading — flipping it here would reverse the next start.
        if (moved != 0.0) {
            float yaw = travel == AXIS_X
                    ? (moved > 0 ? -90.0f : 90.0f)
                    : (moved > 0 ? 0.0f : 180.0f);
            setRotation(new Rotation(yaw, 0.0f));
        }

        // THE POST-MOVE SNAP (the reference's vec3d1 —
        // snapPositionToRail at the moved position, lines 454-464): the
        // same two-band search from the walk's mid-tick altitude, then
        // the cell family's own ride height — the flat cell's
        // +RIDE_Y_OFFSET, the ascent's progress interpolation (the
        // reference's d2 + d8×d0 form: a full cell's rise across the
        // cell, measured from the low face). The boundary crossings
        // resolve here — a cart stepping onto an ascent climbs from its
        // current altitude, one leaving the high face lands on the upper
        // flat's own line, and the walk's y++ folds into the search's
        // own band (the reference's explicit aint[][1] carry).
        int snapCellY = (int) Math.floor(walkY);
        int snapShape = world.railShapeAt(nx, snapCellY - 0.5, nz);
        if (snapShape >= 0) {
            snapCellY--;
        } else {
            snapShape = world.railShapeAt(nx, snapCellY + 0.5, nz);
        }
        double commitY;
        if (snapShape < 0) {
            // No rail under the moved position: the walk's own altitude
            // stands (the reference's skipped final setPosition — the
            // off-end body keeps the walk's y, the fall's start height).
            commitY = walkY;
        } else if (isAscending(snapShape)) {
            int snapCellX = (int) Math.floor(nx);
            int snapCellZ = (int) Math.floor(nz);
            commitY = snapCellY + RIDE_Y_OFFSET
                    + slopeProgressTowardHighFace(snapShape, snapCellX, snapCellZ, nx, nz);
        } else {
            commitY = snapCellY + RIDE_Y_OFFSET;
        }
        commit(nx, commitY, nz, true);

        // THE DRAG (the reference's own call site — moveOnRail line 454,
        // AFTER the move): the rider-gated triple lands on the CARRY
        // alone — this tick's move already ran at the un-damped state.
        applySlowdown();

        // THE GRADE'S TRIM (moveOnRail lines 456-461 — d14): the tick's
        // altitude change ((the tick-head snap y) - (the post-move snap
        // y)) × 0.05, scaled onto the dragged state along its own
        // direction — the climb's 5%-per-cell resistance, the descent's
        // aid; the exact reference form (the flip allowed: a slow body
        // onto a big step rolls back), a no-op on every flat tick (the
        // y never changes, d14 = 0).
        double d14 = (headSnapY - commitY) * SLOPE_TRIM;
        double dragged = Math.sqrt(velocityX() * velocityX() + velocityZ() * velocityZ());
        if (dragged > 0.0) {
            setVelocity(velocityX() / dragged * (dragged + d14), 0.0,
                    velocityZ() / dragged * (dragged + d14));
        }

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
     * THE CURVE WALK (Wave SL — the plain rail's four curves, shapes
     * 6-9, the reference's own diagonal): the projection re-points the
     * whole 2-D state onto the curve's travel line (the end-to-end
     * diagonal — the average of the two legs, the reference's lines
     * 379-386 over the curve's own d1/d2), the position projects onto
     * the same line and advances along it (the d18 walk, lines 414-434:
     * the mid-rail interpolation the engine's cell-center snap
     * replaced), and the cell crossing at the exit re-points the FULL
     * magnitude toward the new cell (lines 466-472) — the pure exit
     * axis. The curve is a plain-rail cell alone (the trio's
     * alwaysStraight band): never a boost, never a brake.
     */
    private void tickOnCurve(WorldQuery world, Position at, int cellX, int cellY,
                             int cellZ, int shape, SteerInput input) {
        int[][] ends = ADJACENT_RAIL_POSITIONS[shape];
        // The curve's travel line: end0's face center to end1's.
        double p0x = cellX + 0.5 + ends[0][0] * 0.5;
        double p0z = cellZ + 0.5 + ends[0][2] * 0.5;
        double p1x = cellX + 0.5 + ends[1][0] * 0.5;
        double p1z = cellZ + 0.5 + ends[1][2] * 0.5;
        double ddx = p1x - p0x;
        double ddz = p1z - p0z;
        double lineLen = Math.sqrt(ddx * ddx + ddz * ddz);

        // THE PROJECTION (lines 379-386 over the diagonal): the 2-D
        // magnitude with the 2.0 ceiling, the direction the d4 fold —
        // the un-flipped default the end0→end1 diagonal.
        double speed = Math.sqrt(velocityX() * velocityX() + velocityZ() * velocityZ());
        if (speed > RAIL_STATE_CEILING) {
            speed = RAIL_STATE_CEILING;
        }
        double d4 = velocityX() * ddx + velocityZ() * ddz;
        double sign = d4 < 0.0 ? -1.0 : 1.0;
        // THE W-KICK (the same 2-D add, the diagonal's own fold): from
        // rest, any look direction kicks the full 0.1 — the magnitude
        // survives the fold whole, the direction the d4 sign.
        boolean brakeCancelled = false;
        if (hasPassenger() && input.forward() > 0.0f
                && velocityX() * velocityX() + velocityZ() * velocityZ() < 0.01) {
            float riderYaw = input.riderYaw();
            double radians = Math.toRadians(Float.isNaN(riderYaw)
                    ? rotation().yaw() : riderYaw);
            double kickX = -Math.sin(radians) * RIDER_KICK;
            double kickZ = Math.cos(radians) * RIDER_KICK;
            speed = Math.sqrt((velocityX() + kickX) * (velocityX() + kickX)
                    + (velocityZ() + kickZ) * (velocityZ() + kickZ));
            d4 = (velocityX() + kickX) * ddx + (velocityZ() + kickZ) * ddz;
            sign = d4 < 0.0 ? -1.0 : 1.0;
            brakeCancelled = true;
        }
        // THE BRAKE consult (the golden-alone gate — the curve's plain
        // family answers 0 over the real query; the stub tracks their
        // own).
        int railPower = world.railPowerAt(cellX + 0.5, cellY + 0.06, cellZ + 0.5);
        if (railPower < 0 && !brakeCancelled) {
            if (speed < BRAKE_STOP_SPEED) {
                speed = 0.0;
            } else {
                speed *= BRAKE_FACTOR;
            }
        }

        // THE MOVE (lines 436-446): the rider's 0.75 scale, the clamp —
        // the advance rides the diagonal's own length (the curve's path
        // through the cell is the half+half diagonal, sqrt(0.5) — the
        // corner cuts faster than the straight crossing, the reference's
        // own geometry).
        double moveScale = hasPassenger() ? RIDER_MOVE_SCALE : 1.0;
        double advance = Math.min(speed * moveScale, getMaxSpeedOnRail());

        // THE d18 POSITION PROJECTION (lines 414-434): the position
        // pulled onto the curve's line, then advanced along it.
        double d18 = ((at.x() - p0x) * ddx + (at.z() - p0z) * ddz) * 2.0;
        d18 += (advance * sign) / lineLen;
        double nx = p0x + ddx * d18;
        double nz = p0z + ddz * d18;

        // THE CARRY: the diagonal velocity (the reference's projected
        // field — the state IS the diagonal while the body rides the
        // curve).
        double ux = ddx * sign / lineLen;
        double uz = ddz * sign / lineLen;
        setVelocity(ux * speed, 0.0, uz * speed);
        // The cart's yaw faces the diagonal travel (the engine's look
        // convention — the same form the flat walk's ±90/0/180 carry).
        if (advance > 0.0) {
            setRotation(new Rotation(
                    (float) Math.toDegrees(Math.atan2(-ux, uz)), 0.0f));
        }
        // The curve rides flat (the ends' y-deltas are zero): the cell's
        // own floor + the offset, the grade trim a no-op.
        commit(nx, cellY + RIDE_Y_OFFSET, nz, true);

        // THE DRAG, then THE EXIT RE-POINT (lines 453, 466-472): the
        // dragged state's FULL magnitude redirected toward the cell the
        // body crossed into — the pure exit axis off the curve's
        // diagonal.
        applySlowdown();
        int newCellX = (int) Math.floor(nx);
        int newCellZ = (int) Math.floor(nz);
        if (newCellX != cellX || newCellZ != cellZ) {
            double mag = Math.sqrt(velocityX() * velocityX() + velocityZ() * velocityZ());
            setVelocity(mag * (newCellX - cellX), 0.0, mag * (newCellZ - cellZ));
        }

        // THE BOOST (the same tail consult — the plain curve answers 0
        // over the real query; the family's uniform walk).
        if (railPower > 0) {
            double rolling = Math.sqrt(velocityX() * velocityX() + velocityZ() * velocityZ());
            if (rolling > 0.01) {
                setVelocity(
                        velocityX() + velocityX() / rolling * POWERED_RAIL_BOOST,
                        velocityY(),
                        velocityZ() + velocityZ() / rolling * POWERED_RAIL_BOOST);
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

    /**
     * The continuation consult (the shape-aware form): the next cell on
     * the travel axis at the shape's OWN exit level — an ascent heading
     * to its high face exits one cell up (the reference's geometry: the
     * upper flat rides the ascent's cell + 1) — with the down-step
     * beside it: an ascent one cell BELOW whose high face looks back
     * (the descent's start, and the crest — two ascents back to back).
     * ANY rail at the exit level continues the advance (the reference's
     * own junction fold: the body rolls INTO the perpendicular cell and
     * the next tick's projection folds the velocity onto the new axis,
     * the un-flipped d4 the default direction) — the same-axis gate
     * would strand the cart one cell short of every junction.
     */
    private boolean railContinues(WorldQuery world, int shape, int cellX, int cellY,
                                  int cellZ, int rail, double direction) {
        int stepX = rail == AXIS_X ? (int) direction : 0;
        int stepZ = rail == AXIS_Z ? (int) direction : 0;
        int nextX = cellX + stepX;
        int nextZ = cellZ + stepZ;
        int exit = exitLevelOffset(shape, rail, direction);
        if (shapeOfCell(world, nextX, cellY + exit, nextZ) >= 0) {
            return true;
        }
        int below = shapeOfCell(world, nextX, cellY + exit - 1, nextZ);
        return axisMatches(below, rail) && isAscending(below)
                && highFaceFacesBack(below, stepX, stepZ);
    }

    /** @return the cell's rail shape, -1 when no rail. */
    private int shapeOfCell(WorldQuery world, int x, int y, int z) {
        return world.railShapeAt(x + 0.5, y + 0.5, z + 0.5);
    }

    /**
     * The exit level's offset for a shape and heading (the ascent's high
     * face: +1 toward it, the low face level).
     */
    private int exitLevelOffset(int shape, int rail, double direction) {
        if (!isAscending(shape)) {
            return 0;
        }
        boolean towardHigh = (shape == SHAPE_ASCENDING_EAST && rail == AXIS_X && direction > 0)
                || (shape == SHAPE_ASCENDING_WEST && rail == AXIS_X && direction < 0)
                || (shape == SHAPE_ASCENDING_NORTH && rail == AXIS_Z && direction < 0)
                || (shape == SHAPE_ASCENDING_SOUTH && rail == AXIS_Z && direction > 0);
        return towardHigh ? 1 : 0;
    }

    /**
     * Whether a shape's travel line matches the walk's axis (the curves
     * match both — the corner's own turn target).
     */
    private boolean axisMatches(int shape, int rail) {
        if (shape < 0) {
            return false;
        }
        if (shape >= SHAPE_FIRST_CURVE) {
            return true;
        }
        boolean shapeIsX = shape == SHAPE_EAST_WEST
                || shape == SHAPE_ASCENDING_EAST || shape == SHAPE_ASCENDING_WEST;
        return shapeIsX == (rail == AXIS_X);
    }

    /**
     * An ascent's high face looking back toward the cell the walk came
     * from (the descent's start / the crest's far side).
     */
    private boolean highFaceFacesBack(int shape, int stepX, int stepZ) {
        return (shape == SHAPE_ASCENDING_EAST && stepX < 0)
                || (shape == SHAPE_ASCENDING_WEST && stepX > 0)
                || (shape == SHAPE_ASCENDING_NORTH && stepZ > 0)
                || (shape == SHAPE_ASCENDING_SOUTH && stepZ < 0);
    }

    /**
     * The ascent's ride-height progress toward its HIGH face (the
     * reference's snapPositionToRail interpolation — d2 + d8×d0 with
     * the y++/y+=0.5 fold: a full cell's rise across the cell, 0 at the
     * low face, 1 at the high one).
     */
    private double slopeProgressTowardHighFace(int shape, int cellX, int cellZ,
                                               double x, double z) {
        switch (shape) {
            case SHAPE_ASCENDING_EAST:
                return x - cellX;
            case SHAPE_ASCENDING_WEST:
                return cellX + 1.0 - x;
            case SHAPE_ASCENDING_NORTH:
                return cellZ + 1.0 - z;
            case SHAPE_ASCENDING_SOUTH:
                return z - cellZ;
            default:
                return 0.0;
        }
    }

    /** @return whether the shape is one of the four ascents. */
    static boolean isAscending(int shape) {
        return shape >= SHAPE_ASCENDING_EAST && shape <= SHAPE_ASCENDING_SOUTH;
    }

    /** The shape's travel axis (the curves': the Z band — a rail is a rail). */
    static int axisOf(int shape) {
        boolean isX = shape == SHAPE_EAST_WEST
                || shape == SHAPE_ASCENDING_EAST || shape == SHAPE_ASCENDING_WEST;
        return isX ? AXIS_X : AXIS_Z;
    }

    /** @return the perpendicular rail's direction at this cell, 0 if none. */
    private int crossRailDirection(WorldQuery world, int cellX, int cellY, int cellZ, int rail) {
        int other = rail == AXIS_X ? AXIS_Z : AXIS_X;
        int[] deltas = {1, -1};
        for (int delta : deltas) {
            int probeX = other == AXIS_X ? cellX + delta : cellX;
            int probeZ = other == AXIS_Z ? cellZ + delta : cellZ;
            if (shapeOfCell(world, probeX, cellY, probeZ) >= 0) {
                return delta;
            }
        }
        return 0;
    }

    /** The rail shape under a point (protected for the variants' own walks). */
    protected int railShapeAt(WorldQuery world, Position at) {
        int cellY = (int) Math.floor(at.y());
        int below = world.railShapeAt(at.x(), cellY - 0.5, at.z());
        if (below >= 0) {
            return below;
        }
        return world.railShapeAt(at.x(), cellY + 0.5, at.z());
    }
}
