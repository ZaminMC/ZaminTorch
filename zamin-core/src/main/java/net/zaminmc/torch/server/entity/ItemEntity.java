package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;

import java.util.Objects;

/**
 * One dropped item in the world: identity, position, motion, contents and the
 * two timers that shape its life (pickup delay, despawn age).
 *
 * <p>The entity is engine state owned by the simulation context; the id is
 * engine-global (all observers must agree on it, unlike player entities whose
 * wire ids are observer-local today). Physics is deliberately simplified
 * (gravity + ground snap) and marked for the physics slice.</p>
 */
public final class ItemEntity implements net.zaminmc.torch.entity.DroppedItem {

    /** The public kind identifier (§27.3): the item-entity body. */
    private static final Identifier TYPE_IDENTIFIER = Identifier.parse("minecraft:item");

    /** Historical constants, documented for the physics slice to revisit. */
    public static final double GRAVITY_PER_TICK = 0.04;
    public static final double HALF_HEIGHT = 0.125;   // item box 0.25 like the historical model
    public static final double HALF_WIDTH = 0.125;
    public static final int DESPAWN_TICKS = 6_000;    // 5 minutes
    public static final int PICKUP_DELAY_DROP_TICKS = 10;         // 0.5s for block drops
    public static final int PICKUP_DELAY_PLAYER_THROW_TICKS = 40; // 2s for player-thrown

    /** Minimal ground query the entity needs from its world (tick-thread context only). */
    public interface Ground extends net.zaminmc.torch.server.physics.HasCollisionWorld {
        /**
         * The dimension context switch (Wave TF-9 — the reference's
         * Entity.world): the manager flips the shared ground to the body's
         * own dimension before every tick walk, exactly like the mob
         * query's own flip. The default is a no-op so stub grounds keep
         * their overworld-only semantics.
         */
        default void setActiveDimension(int dimension) {
        }

        /** @return whether the block containing this point is solid. */
        boolean isSolid(double x, double y, double z);

        /**
         * The shape-aware point test (the collision-shape slice); the
         * default folds to the boolean world for stub grounds.
         */
        default boolean isSolidAt(double x, double y, double z) {
            return isSolid(x, y, z);
        }

        /**
         * The top surface of the collision shape in the point's cell (the
         * support a resting bottom snaps onto); the default mirrors the
         * boolean world's full-cube snap. Non-solid cells answer negative
         * infinity.
         */
        default double supportY(double x, double y, double z) {
            return isSolid(x, y, z) ? Math.floor(y) + 1.0 : Double.NEGATIVE_INFINITY;
        }

        /**
         * The item's own half-width (the historical EntityItem box is
         * 0.25 wide — the box-footprint physics constant).
         */
        double HALF_WIDTH = 0.125;

        /**
         * The box-footprint landing test (the vanilla-shape slice): the
         * item's 0.25-wide bottom face — the four corners + the center —
         * against the collision shapes, so an item whose CENTER has
         * drifted past a cell edge still rests on the shape under its
         * overlapping edge (vanilla's EntityItem collides as a box, not
         * a point — the hopper's funnel, the slab's rim, the fence's
         * post all catch the edge of the box). The default folds to the
         * center-point test (the pre-box behavior) for stub grounds.
         */
        default boolean isSolidAtBox(double centerX, double bottomY, double centerZ) {
            return isSolidAt(centerX, bottomY, centerZ);
        }

        /**
         * The box-footprint support: the HIGHEST shape top any bottom
         * face sample touches — the surface the resting bottom snaps
         * onto. The default folds to the center-point support.
         */
        default double supportYAtBox(double centerX, double bottomY, double centerZ) {
            return supportY(centerX, bottomY, centerZ);
        }

        /**
         * The block id at the body's -0.2 feet cell (Wave IO-F — the
         * reference's Entity.move line 572 consult cell
         * {@code floor(x), floor(y - 0.2), floor(z)} with y the box
         * bottom; the engine's bottom = {@code position.y - HALF_HEIGHT}).
         * The vertical-clip consult (line 599's {@code beforeCollision})
         * reads it — the slime block's own override flips a falling
         * body's velocityY at full speed while the default zeroes it.
         * The default answers null so stub grounds keep the pre-wave
         * zeroing semantics (null is never the pad).
         */
        default net.zaminmc.torch.util.Identifier blockIdAt(double centerX, double bottomY, double centerZ) {
            return null;
        }

        /**
         * The block slipperiness at the support cell (Wave IO-F — the
         * reference's ItemEntity.tick lines 89-96 over Block.slipperiness:
         * the drag band reads {@code floor(x), floor(minY) - 1, floor(z)}
         * — the cell BELOW the feet cell, the engine's bottom =
         * {@code position.y - HALF_HEIGHT}). The default answers the
         * common case 0.6 (Block.java line 117) so stub grounds keep the
         * pre-wave drag.
         */
        default double slipperinessBelow(double centerX, double bottomY, double centerZ) {
            return 0.6;
        }
    }

    private final int entityId;
    private Position position;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private ItemStack stack;
    private int age;
    private int pickupDelay;
    private boolean onGround;
    /** The never-despawn flag (Slice 59 — ItemEntity.setNeverDespawn: the wither's nether star stays until collected). */
    private boolean neverDespawn;
    /**
     * The throwing player's name (Slice 93/D1 — ItemEntity lines 24-25: the
     * private string the drop site stamps through
     * {@code PlayerEntity.dropItem(item, false, true)}'s
     * {@code setThrower(this.getName())}); the pickup walk's GIVE_DIAMOND
     * arm reads it — a diamond picked up with a thrower stamps THAT player
     * (a different live one) with the achievement. Null on every non-player
     * drop (the block loot, the mob deaths, the container spills).
     */
    private String thrower;
    /**
     * The window's own pickup-window owner (ItemEntity's own second string): the pickup
     * gate answers true when the store is null, when the body is within
     * 200 ticks of despawning ({@code 6000 - age <= 200}), or when the
     * collector IS the owner. The server never stamps it (the reference's
     * own shape — the row round-trips the NBT alone); the gate stays
     * permissive for every engine drop.
     */
    private String owner;

    /**
     * The body's dimension (Wave TF-9 — the reference's Entity.world, the
     * mob query's own Slice-25d shape generalized over the item family):
     * 0 the overworld, -1 the nether, 1 the end. The shared ground flips
     * to the body's own world before every tick walk (setActiveDimension)
     * — a nether item rests on the nether's blocks, merges only with the
     * nether's items, and only the nether's players collect it.
     */
    private int dimension;

    /** @return the dimension the body lives in (0 overworld, -1 nether, 1 the end). */
    public int dimension() {
        return dimension;
    }

    /** Sets the body's dimension (the spawn walks and the ZID restore stamp it). */
    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public ItemEntity(int entityId, Position position, ItemStack stack, int pickupDelayTicks) {
        this.entityId = entityId;
        this.position = Objects.requireNonNull(position, "position");
        this.stack = Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Item entities cannot be empty");
        }
        this.pickupDelay = pickupDelayTicks;
    }

    public int entityId() {
        return entityId;
    }

    public Position position() {
        return position;
    }

    /**
     * The seated rider's plain position set (the riding-follow surface —
     * the reference's updateRiderPositon writes the rider's position
     * whatever family it is; the reference's rideTick zeroes the motion
     * while the tick rides, the item's own drift re-anchoring each tick).
     */
    public void teleport(Position to) {
        this.position = Objects.requireNonNull(to, "to");
    }

    /**
     * The piston push (the VIII13 slice — the reference's
     * {@code Entity.move(amount * facing)}): the immediate, per-axis
     * clamped position shift — the same leading-face law the legacy walk
     * drives (the box's leading face against the ground's solid cells,
     * the blocked axis refusing its shift), the vertical clamped by the
     * box's own top/bottom faces. Outside the velocity integration (the
     * push is direct, the reference's own shape).
     */
    public void pushBy(Ground ground, double dx, double dy, double dz) {
        double newX = position.x();
        double newY = position.y();
        double newZ = position.z();
        if (dx != 0.0) {
            double leadX = position.x() + dx + Math.signum(dx) * HALF_WIDTH;
            if (!leadingFaceBlockedX(ground, leadX, position.y(), position.z())) {
                newX = position.x() + dx;
            }
        }
        if (dz != 0.0) {
            double leadZ = position.z() + dz + Math.signum(dz) * HALF_WIDTH;
            if (!leadingFaceBlockedZ(ground, newX, position.y(), leadZ)) {
                newZ = position.z() + dz;
            }
        }
        if (dy != 0.0) {
            double probeY = dy > 0
                    ? position.y() + dy + HALF_HEIGHT - GROUND_EPSILON
                    : position.y() + dy - HALF_HEIGHT + GROUND_EPSILON;
            if (!ground.isSolidAt(newX, probeY, newZ)) {
                newY = position.y() + dy;
            }
        }
        position = new Position(newX, newY, newZ);
    }

    public double velocityX() {
        return velocityX;
    }

    public double velocityY() {
        return velocityY;
    }

    public double velocityZ() {
        return velocityZ;
    }

    public ItemStack stack() {
        return stack;
    }

    public int age() {
        return age;
    }

    public boolean pickupAllowed() {
        return pickupDelay <= 0;
    }

    public boolean expired() {
        return !neverDespawn && age >= DESPAWN_TICKS;
    }

    /** ItemEntity.setNeverDespawn: the age clock stops expiring the body. */
    public void setNeverDespawn() {
        neverDespawn = true;
    }

    /** @return whether the body carries the never-despawn flag. */
    public boolean neverDespawn() {
        return neverDespawn;
    }

    /**
     * The persisted restore's own setters (Slice 93/E1 — the reference's
     * readCustomNbt rows landing on the model): the age clock, the pickup
     * delay, and the thrower/owner strings ride the store's round trip.
     */
    public void setAge(int age) {
        this.age = age;
    }

    public void setPickupDelayTicks(int pickupDelay) {
        this.pickupDelay = pickupDelay;
    }

    /** The vanilla kill plane: an item below it is gone without a drop. */
    public static final double VOID_KILL_Y = -64.0;

    /** @return true when the body fell out of the world (silent removal due). */
    public boolean inVoid() {
        return position.y() < VOID_KILL_Y;
    }

    public void setStack(ItemStack newStack) {
        this.stack = Objects.requireNonNull(newStack, "newStack");
        if (newStack.isEmpty()) {
            throw new IllegalArgumentException("Item entities cannot hold the empty stack");
        }
    }

    public void setVelocity(double vx, double vy, double vz) {
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
    }

    public boolean onGround() {
        return onGround;
    }

    /** Position epsilon so a resting item's bottom queries the block *below* the surface. */
    private static final double GROUND_EPSILON = 1.0E-7;

    /**
     * Advances one tick of the item's physics — the reference's own
     * ItemEntity.tick model (Wave IO-F): the pre-move gravity, the swept
     * move with its vertical-clip consult, the post-move drag with the
     * slipperiness band, and the grounded vertical's -0.5 flip. Returns
     * true when the rounded fixed-point position changed (so the
     * publisher can decide to sync).
     */
    public boolean tick(Ground ground) {
        long before = fixedPoint(position);
        age++;
        if (pickupDelay > 0) {
            pickupDelay--;
        }
        // THE PRE-MOVE GRAVITY (Wave IO-F — the reference's ItemEntity.tick
        // line 72, the engine's order repair): gravity lands BEFORE the
        // move, UNCONDITIONALLY — never fused with the drag. The reference
        // applies {@code velocityY -= 0.04} here and the *0.98 vertical
        // drag POST-move (line 99); the pre-fix engine had fused the pair
        // into (v - 0.04) * 0.98 and gated it on !onGround — a resting
        // body never re-armed its vertical, the exact cycle the old
        // standing-drift hack then had to restore at the solver door.
        // With the unconditional pre-move arm the drift hack RETIRES: a
        // resting body always enters the move carrying a small negative
        // vertical, so the solver's ground flag stays on every tick.
        velocityY -= GRAVITY_PER_TICK;

        // THE SWEPT-BOX APPLICATION (the physics-solver slice): the
        // item's own Entity.move — the 0.25 box clipped axis by axis
        // against every collision box its swept shape touches (the
        // leading-face sample stand-in RETIRED) — when the ground
        // provides the collision surface. The engine's centre
        // convention translates to the solver's feet convention (the
        // bottom = position.y - HALF_HEIGHT).
        net.zaminmc.torch.server.physics.EntityCollisionWorld collisionWorld =
                ground.collisionWorld();
        if (collisionWorld != null) {
            net.zaminmc.torch.server.physics.EntityMoveSolver.Body body =
                    new net.zaminmc.torch.server.physics.EntityMoveSolver.Body(
                            position.x(), position.y() - HALF_HEIGHT, position.z(),
                            HALF_WIDTH * 2.0, HALF_HEIGHT * 2.0);
            body.onGround = onGround;
            net.zaminmc.torch.server.physics.EntityMoveSolver.Result result =
                    net.zaminmc.torch.server.physics.EntityMoveSolver.move(
                            collisionWorld, body, velocityX, velocityY, velocityZ,
                            net.zaminmc.torch.server.physics.EntityMoveSolver.MoveLaw.plain());
            position = new Position(body.x, body.y + HALF_HEIGHT, body.z);
            onGround = result.onGround();
            if (result.zeroVelocityX()) {
                velocityX = 0.0;
            }
            if (result.zeroVelocityZ()) {
                velocityZ = 0.0;
            }
            if (result.zeroVelocityY()) {
                // THE VERTICAL-CLIP CONSULT (Wave IO-F — the reference's
                // Entity.move line 599 over Block.beforeCollision line
                // 882): the clip consults the -0.2 feet cell — the slime
                // block's own override (SlimeBlock lines 33-39) flips a
                // falling body's velocityY at full speed and keeps a
                // rising one (the velocityY >= 0 no-op quirk), the default
                // zeroes it. Items never sneak (the override's other arm
                // is vacuous here). The consult rides the post-move
                // position (the reference consults inside move, after
                // setPositionFromShape — the same cell either way).
                beforeCollisionAt(ground);
            }
            applyPostMoveDrag(ground);
            return fixedPoint(position) != before;
        }

        double newY = position.y() + velocityY;
        double newX = position.x() + velocityX;
        double newZ = position.z() + velocityZ;

        // The horizontal clip (the vanilla-shape slice's follow-up): the
        // box's LEADING FACE against the collision shapes, per axis — a
        // blocked axis zeroes its velocity (the vanilla moveEntity
        // response: no horizontal bounce), the free axis keeps sliding.
        if (velocityX != 0.0) {
            double leadX = newX + Math.signum(velocityX) * Ground.HALF_WIDTH;
            if (leadingFaceBlockedX(ground, leadX, newY, newZ)) {
                newX = position.x();
                velocityX = 0.0;
            }
        }
        if (velocityZ != 0.0) {
            double leadZ = newZ + Math.signum(velocityZ) * Ground.HALF_WIDTH;
            if (leadingFaceBlockedZ(ground, newX, newY, leadZ)) {
                newZ = position.z();
                velocityZ = 0.0;
            }
        }

        double clipConsultY = Double.NaN;
        if (velocityY <= 0 && ground.isSolidAtBox(newX, newY - HALF_HEIGHT - GROUND_EPSILON, newZ)) {
            // Snap the item's bottom onto the surface it landed on (the
            // slab half or the full cube's ceiling) — the box footprint's
            // own support (the highest shape top the bottom face touches).
            newY = ground.supportYAtBox(newX, newY - HALF_HEIGHT - GROUND_EPSILON, newZ) + HALF_HEIGHT;
            onGround = true;
            clipConsultY = newY; // the landing clip (the vertical consult below)
        } else if (velocityY > 0 && ground.isSolidAtBox(newX, newY + HALF_HEIGHT + GROUND_EPSILON, newZ)) {
            newY = position.y();
            onGround = false;
            clipConsultY = newY; // bumped a ceiling (the same consult below)
        } else {
            onGround = false;
        }

        position = new Position(newX, newY, newZ);
        if (!Double.isNaN(clipConsultY)) {
            // The vertical-clip consult (the reference's beforeCollision at
            // the clip — the slime flip over the default zeroing), riding
            // the POST-move position exactly like the solver path's own
            // placement of the consult.
            beforeCollisionAt(ground);
        }
        applyPostMoveDrag(ground);
        return fixedPoint(position) != before;
    }

    /**
     * The vertical-clip consult (Wave IO-F — Entity.move line 599's
     * {@code block1.beforeCollision(world, this)}): the -0.2 feet cell's
     * block id decides the vertical response — the slime block's override
     * (SlimeBlock lines 33-39) flips a falling body at full speed, keeps a
     * rising one (the velocityY >= 0 no-op quirk), and every other block
     * rides the default (Block.beforeCollision line 882: velocityY = 0).
     * The consult cell is the reference's own (Entity.move line 572:
     * {@code floor(x), floor(y - 0.2), floor(z)}, y the box bottom — the
     * engine's bottom = position.y - HALF_HEIGHT).
     */
    private void beforeCollisionAt(Ground ground) {
        net.zaminmc.torch.util.Identifier underfoot = ground.blockIdAt(
                position.x(), position.y() - HALF_HEIGHT, position.z());
        if (underfoot != null && underfoot.value().equals("slime_block")) {
            if (velocityY < 0.0) {
                velocityY = -velocityY;
            }
            // velocityY >= 0: the no-op quirk (SlimeBlock line 38's else) —
            // a rising body clipped over the pad keeps its rise.
        } else {
            velocityY = 0.0;
        }
    }

    /**
     * The post-move drag (Wave IO-F — the reference's ItemEntity.tick lines
     * 89-103): the horizontal band reads the SUPPORT cell's slipperiness
     * on the ground ({@code floor(x), floor(minY) - 1, floor(z)} — the
     * reference's own consult, the ice family's 0.98 and the pad's 0.8
     * against the common case's 0.6, all * 0.98), the air band is the flat
     * 0.98, the vertical drags at 0.98 — and a grounded body's vertical
     * flips at -0.5 (line 102: the resting micro-hop — the reference's
     * own anti-jitter law; over the pad the flip halves the
     * beforeCollision bounce, over the common blocks it acts on the zero
     * the default consult left).
     */
    private void applyPostMoveDrag(Ground ground) {
        double f = 0.98;
        if (onGround) {
            f = ground.slipperinessBelow(position.x(), position.y() - HALF_HEIGHT, position.z()) * 0.98;
        }
        velocityX *= f;
        velocityY *= 0.98;
        velocityZ *= f;
        if (onGround) {
            velocityY *= -0.5;
        }
    }

    private static long fixedPoint(Position position) {
        return (Math.round(position.x() * 32.0) << 42)
                | (Math.round(position.y() * 32.0) << 21)
                | Math.round(position.z() * 32.0);
    }

    /**
     * The X-leading-face test: the box's face at {@code leadX} swept over
     * the box's z-width and y-height (three z samples x three y samples —
     * the bottom sits a hair above the resting surface so the ground the
     * item slides on never blocks its own slide).
     */
    private static boolean leadingFaceBlockedX(Ground ground, double leadX,
                                               double centerY, double centerZ) {
        double w = Ground.HALF_WIDTH;
        double bottom = centerY - HALF_HEIGHT + GROUND_EPSILON;
        double top = centerY + HALF_HEIGHT - GROUND_EPSILON;
        for (double y : new double[]{bottom, centerY, top}) {
            if (ground.isSolidAt(leadX, y, centerZ - w)
                    || ground.isSolidAt(leadX, y, centerZ)
                    || ground.isSolidAt(leadX, y, centerZ + w)) {
                return true;
            }
        }
        return false;
    }

    /** The Z-leading-face test: the face at {@code leadZ} over the x-width. */
    private static boolean leadingFaceBlockedZ(Ground ground, double centerX,
                                               double centerY, double leadZ) {
        double w = Ground.HALF_WIDTH;
        double bottom = centerY - HALF_HEIGHT + GROUND_EPSILON;
        double top = centerY + HALF_HEIGHT - GROUND_EPSILON;
        for (double y : new double[]{bottom, centerY, top}) {
            if (ground.isSolidAt(centerX - w, y, leadZ)
                    || ground.isSolidAt(centerX, y, leadZ)
                    || ground.isSolidAt(centerX + w, y, leadZ)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // The public API surface (§27.3 — the DroppedItem handle). The
    // engine class IS the handle (§27.5's zero-allocation shape: no
    // wrapper per access; the internal implementation may change behind
    // this surface).
    // ------------------------------------------------------------------

    @Override
    public Identifier typeIdentifier() {
        return TYPE_IDENTIFIER;
    }

    /**
     * The any-thread-consumable view (§27.6): captured on the owner
     * context inside {@code World.execute}. Health/maxHealth do not
     * apply to the item body (1/1); {@code dead} answers false — the
     * removal contract is the population view's disappearance, not a
     * corpse flag.
     */
    @Override
    public net.zaminmc.torch.entity.EntitySnapshot snapshot() {
        return new net.zaminmc.torch.entity.EntitySnapshot(
                entityId, TYPE_IDENTIFIER, position, 0.0f,
                velocityX, velocityY, velocityZ,
                1.0f, 1.0f, onGround, false);
    }

    /** The public pickup-delay read (the manager's own cadence state). */
    @Override
    public int pickupDelayTicks() {
        return pickupDelay;
    }

    // ------------------------------------------------------------------
    // The thrower/owner family (Slice 93/D1 — ItemEntity lines 24-25 +
    // 262-267 + 344-358): the two private strings, their accessors, and
    // the pickup walk's owner-window gate.
    // ------------------------------------------------------------------

    /** ItemEntity.getThrower: the dropping player's name, null when the drop is not a player's throw. */
    public String thrower() {
        return thrower;
    }

    /** ItemEntity.setThrower: the drop site's stamp (the player's own name). */
    public void setThrower(String thrower) {
        this.thrower = thrower;
    }

    /** ItemEntity.getOwner: the pickup-window owner, null when unset (every engine drop). */
    public String owner() {
        return owner;
    }

    /** ItemEntity.setOwner: the pickup-window owner's stamp. */
    public void setOwner(String owner) {
        this.owner = owner;
    }

    /**
     * The owner-window pickup gate (ItemEntity.onPlayerCollision lines
     * 266-267): {@code this.owner == null || 6000 - this.age <= 200 ||
     * this.owner.equals(player.getName())} — an unowned body answers true
     * for everyone, an owned body answers true only for its owner until
     * the last 200 ticks before despawn open it to everyone. The delay
     * gate itself ({@code pickUpDelay == 0}) stays the caller's own walk.
     */
    public boolean pickupAllowedFor(String collectorName) {
        return owner == null
                || DESPAWN_TICKS - age <= 200
                || owner.equals(collectorName);
    }
}
