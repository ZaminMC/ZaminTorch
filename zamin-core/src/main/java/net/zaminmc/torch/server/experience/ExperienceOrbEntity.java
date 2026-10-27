package net.zaminmc.torch.server.experience;

import net.zaminmc.torch.util.Position;

import java.util.Objects;

/**
 * One experience orb in the world (the historical EntityXPOrb): a small
 * physics body that carries a bundle of XP points and dies into the first
 * player's level that touches it.
 *
 * <p>Physics reuses the item-entity model (gravity, drag, epsilon ground
 * snap) — the orb is a smaller box of the same kind. The entity is engine
 * state owned by the simulation context; the id is engine-global from the
 * experience band so every observer agrees on it.</p>
 */
public final class ExperienceOrbEntity implements net.zaminmc.torch.entity.ExperienceOrb {

    /** The public kind identifier (§27.3): the experience-orb body. */
    private static final net.zaminmc.torch.util.Identifier TYPE_IDENTIFIER =
            net.zaminmc.torch.util.Identifier.parse("minecraft:xp_orb");

    /** Shared with items (the physics slice will unify the small-body model). */
    public static final double GRAVITY_PER_TICK = 0.04;
    public static final double HALF_HEIGHT = 0.125;
    public static final double HALF_WIDTH = 0.125;
    /** Historical orb lifespan: 5 minutes, then it fades (the item rule). */
    public static final int DESPAWN_TICKS = 6_000;
    /** Position epsilon so a pushed bottom queries the block *below* the surface (the item arm's own form). */
    private static final double PUSH_GROUND_EPSILON = 1.0E-7;
    /** Fresh orbs wait a beat before pickup (the item-drop rhythm). */
    public static final int PICKUP_DELAY_TICKS = 10;
    /** The vanilla kill plane: an orb below it is gone without payout. */
    public static final double VOID_KILL_Y = -64.0;

    /** Minimal ground query the orb needs (tick-thread context only). */
    public interface Ground {
        /**
         * The dimension context switch (Wave TF-10 — the reference's
         * Entity.world, the item family's own TF-9 shape): the manager
         * flips the shared ground to the body's own dimension before
         * every tick walk. The default is a no-op so stub grounds keep
         * their overworld-only semantics.
         */
        default void setActiveDimension(int dimension) {
        }

        /** @return whether the block containing this point is solid. */
        boolean isSolid(double x, double y, double z);
    }

    private final int entityId;
    private final int amount;
    private Position position;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private int age;
    private int pickupDelay;
    private boolean onGround;
    /**
     * The body's dimension (Wave TF-10 — the reference's Entity.world, the
     * TF-9 shape over the orb family): 0 the overworld, -1 the nether. The
     * shared ground flips to the body's own world before every tick walk;
     * only the body's own dimension's players collect it.
     */
    private int dimension;

    /** @return the dimension the body lives in (0 overworld, -1 nether). */
    public int dimension() {
        return dimension;
    }

    /** Sets the body's dimension (the spawn walks and the restore stamp it). */
    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public ExperienceOrbEntity(int entityId, Position position, int amount, int pickupDelayTicks) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Orbs carry a positive amount: " + amount);
        }
        this.entityId = entityId;
        this.amount = amount;
        this.position = Objects.requireNonNull(position, "position");
        this.pickupDelay = pickupDelayTicks;
    }

    public int entityId() {
        return entityId;
    }

    public int amount() {
        return amount;
    }

    /** The despawn clock (readCustomNbt's own {@code Age} row resumes it). */
    public int age() {
        return age;
    }

    public Position position() {
        return position;
    }

    /**
     * The seated rider's plain position set (the riding-follow surface —
     * the reference's updateRiderPositon writes the rider's position
     * whatever family it is; the orb's own drift keeps running, the
     * seat re-anchoring the body each tick after it).
     */
    public void teleport(Position to) {
        this.position = Objects.requireNonNull(to, "to");
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

    public boolean onGround() {
        return onGround;
    }

    public boolean pickupAllowed() {
        return pickupDelay <= 0;
    }

    public boolean expired() {
        return age >= DESPAWN_TICKS;
    }

    /** @return true when the orb fell out of the world (silent removal due). */
    public boolean inVoid() {
        return position.y() < VOID_KILL_Y;
    }

    public void setVelocity(double vx, double vy, double vz) {
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
    }

    /**
     * The piston push (the VIII13 additive families — the reference's
     * {@code Entity.move(amount * facing)} over MovingBlockEntity's
     * interpolated box, lines 80-115): the IMMEDIATE, per-axis clamped
     * position shift — the same leading-face law the item family's own
     * arm carries (the orb's physics reuses the item model whole, this
     * class's own unify note; the box's leading face against the world's
     * solid cells, the blocked axis refusing its shift), the vertical
     * clamped by the box's own top/bottom faces. Outside the velocity
     * integration (the push is direct, the reference's own shape).
     */
    public void pushBy(Ground ground, double dx, double dy, double dz) {
        double newX = position.x();
        double newY = position.y();
        double newZ = position.z();
        if (dx != 0.0) {
            double leadX = position.x() + dx + Math.signum(dx) * HALF_WIDTH;
            if (!faceBlockedX(ground, leadX, position.y(), position.z())) {
                newX = position.x() + dx;
            }
        }
        if (dz != 0.0) {
            double leadZ = position.z() + dz + Math.signum(dz) * HALF_WIDTH;
            if (!faceBlockedZ(ground, newX, position.y(), leadZ)) {
                newZ = position.z() + dz;
            }
        }
        if (dy != 0.0) {
            double probeY = dy > 0
                    ? position.y() + dy + HALF_HEIGHT - PUSH_GROUND_EPSILON
                    : position.y() + dy - HALF_HEIGHT + PUSH_GROUND_EPSILON;
            if (!ground.isSolid(newX, probeY, newZ)) {
                newY = position.y() + dy;
            }
        }
        position = new Position(newX, newY, newZ);
    }

    /**
     * The X-leading-face test (the item family's own three-sample sweep):
     * the face at {@code leadX} over the box's z-width and y-height — the
     * bottom sits a hair above the resting surface so the ground the orb
     * slides on never blocks its own slide.
     */
    private static boolean faceBlockedX(Ground ground, double leadX,
                                        double centerY, double centerZ) {
        double bottom = centerY - HALF_HEIGHT + PUSH_GROUND_EPSILON;
        double top = centerY + HALF_HEIGHT - PUSH_GROUND_EPSILON;
        for (double y : new double[]{bottom, centerY, top}) {
            if (ground.isSolid(leadX, y, centerZ - HALF_WIDTH)
                    || ground.isSolid(leadX, y, centerZ)
                    || ground.isSolid(leadX, y, centerZ + HALF_WIDTH)) {
                return true;
            }
        }
        return false;
    }

    /** The Z-leading-face test: the face at {@code leadZ} over the x-width. */
    private static boolean faceBlockedZ(Ground ground, double centerX,
                                        double centerY, double leadZ) {
        double bottom = centerY - HALF_HEIGHT + PUSH_GROUND_EPSILON;
        double top = centerY + HALF_HEIGHT - PUSH_GROUND_EPSILON;
        for (double y : new double[]{bottom, centerY, top}) {
            if (ground.isSolid(centerX - HALF_WIDTH, y, leadZ)
                    || ground.isSolid(centerX, y, leadZ)
                    || ground.isSolid(centerX + HALF_WIDTH, y, leadZ)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The restored age (the Anvil import's exact-value landing — the
     * reference's readCustomNbt {@code Age} short): the saved orb's
     * despawn clock resumes where the save left it. Package-private: the
     * manager's spawnRestored arm is the only caller.
     */
    void setAge(int age) {
        this.age = age;
    }

    /**
     * Advances one tick of the item-entity physics model. Returns true when
     * the rounded fixed-point position changed (the publisher's sync hint).
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

        double newY = position.y() + velocityY;
        double newX = position.x() + velocityX;
        double newZ = position.z() + velocityZ;

        double groundEpsilon = 1.0E-7;
        if (velocityY <= 0 && ground.isSolid(newX, newY - HALF_HEIGHT - groundEpsilon, newZ)) {
            newY = Math.floor(newY - HALF_HEIGHT - groundEpsilon) + 1.0 + HALF_HEIGHT;
            velocityY = 0.0;
            onGround = true;
        } else if (velocityY > 0 && ground.isSolid(newX, newY + HALF_HEIGHT + groundEpsilon, newZ)) {
            velocityY = 0.0;
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

    // ------------------------------------------------------------------
    // The public API surface (§27.3 — the ExperienceOrb handle).
    // ------------------------------------------------------------------

    @Override
    public net.zaminmc.torch.util.Identifier typeIdentifier() {
        return TYPE_IDENTIFIER;
    }

    /**
     * The any-thread-consumable view (§27.6): captured on the owner
     * context inside {@code World.execute}. Health/maxHealth do not
     * apply to the orb body (1/1); {@code dead} answers the lifetime's
     * expiry state.
     */
    @Override
    public net.zaminmc.torch.entity.EntitySnapshot snapshot() {
        return new net.zaminmc.torch.entity.EntitySnapshot(
                entityId, TYPE_IDENTIFIER, position, 0.0f,
                velocityX, velocityY, velocityZ,
                1.0f, 1.0f, onGround, expired());
    }
}
