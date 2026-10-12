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
public final class ExperienceOrbEntity {

    /** Shared with items (the physics slice will unify the small-body model). */
    public static final double GRAVITY_PER_TICK = 0.04;
    public static final double HALF_HEIGHT = 0.125;
    public static final double HALF_WIDTH = 0.125;
    /** Historical orb lifespan: 5 minutes, then it fades (the item rule). */
    public static final int DESPAWN_TICKS = 6_000;
    /** Fresh orbs wait a beat before pickup (the item-drop rhythm). */
    public static final int PICKUP_DELAY_TICKS = 10;
    /** The vanilla kill plane: an orb below it is gone without payout. */
    public static final double VOID_KILL_Y = -64.0;

    /** Minimal ground query the orb needs (tick-thread context only). */
    public interface Ground {
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

    public Position position() {
        return position;
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
}
