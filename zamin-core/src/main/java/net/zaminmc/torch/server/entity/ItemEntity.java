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
     * The pickup-window owner (ItemEntity's own second string): the pickup
     * gate answers true when the store is null, when the body is within
     * 200 ticks of despawning ({@code 6000 - age <= 200}), or when the
     * collector IS the owner. The server never stamps it (the reference's
     * own shape — the row round-trips the NBT alone); the gate stays
     * permissive for every engine drop.
     */
    private String owner;

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
     * Advances one tick of simplified physics: gravity, drag, ground snap
     * against the world's solid blocks. Returns true when the rounded
     * fixed-point position changed (so the publisher can decide to sync).
     */
    public boolean tick(Ground ground) {
        long before = fixedPoint(position);
        age++;
        if (pickupDelay > 0) {
            pickupDelay--;
        }
        if (onGround) {
            velocityY = 0.0;
        } else {
            velocityY = (velocityY - GRAVITY_PER_TICK) * 0.98;
        }
        double damping = onGround ? 0.6 : 0.98;
        velocityX *= damping;
        velocityZ *= damping;

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
            // The standing drift (the reference's gravity-AFTER-move cycle:
            // a resting body enters with (0 - g) * 0.98, never the zeroed
            // vertical — a zero dy never grounds and the flag would
            // flicker every other tick).
            double driftY = onGround && velocityY == 0.0
                    ? (0.0 - GRAVITY_PER_TICK) * 0.98 : velocityY;
            net.zaminmc.torch.server.physics.EntityMoveSolver.Result result =
                    net.zaminmc.torch.server.physics.EntityMoveSolver.move(
                            collisionWorld, body, velocityX, driftY, velocityZ,
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
                velocityY = 0.0;
            }
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

        if (velocityY <= 0 && ground.isSolidAtBox(newX, newY - HALF_HEIGHT - GROUND_EPSILON, newZ)) {
            // Snap the item's bottom onto the surface it landed on (the
            // slab half or the full cube's ceiling) — the box footprint's
            // own support (the highest shape top the bottom face touches).
            newY = ground.supportYAtBox(newX, newY - HALF_HEIGHT - GROUND_EPSILON, newZ) + HALF_HEIGHT;
            velocityY = 0.0;
            onGround = true;
        } else if (velocityY > 0 && ground.isSolidAtBox(newX, newY + HALF_HEIGHT + GROUND_EPSILON, newZ)) {
            velocityY = 0.0; // bumped a ceiling; do not pass through
            newY = position.y();
            onGround = false;
        } else {
            onGround = false;
        }

        position = new Position(newX, newY, newZ);
        return fixedPoint(position) != before;
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
