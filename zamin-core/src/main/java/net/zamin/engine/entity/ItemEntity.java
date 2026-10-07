package net.zamin.engine.entity;

import net.zamin.api.ItemStack;
import net.zamin.api.Position;

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
public final class ItemEntity {

    /** Historical constants, documented for the physics slice to revisit. */
    public static final double GRAVITY_PER_TICK = 0.04;
    public static final double HALF_HEIGHT = 0.125;   // item box 0.25 like the historical model
    public static final double HALF_WIDTH = 0.125;
    public static final int DESPAWN_TICKS = 6_000;    // 5 minutes
    public static final int PICKUP_DELAY_DROP_TICKS = 10;         // 0.5s for block drops
    public static final int PICKUP_DELAY_PLAYER_THROW_TICKS = 40; // 2s for player-thrown

    /** Minimal ground query the entity needs from its world (tick-thread context only). */
    public interface Ground {
        /** @return whether the block containing this point is solid. */
        boolean isSolid(double x, double y, double z);
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
        return age >= DESPAWN_TICKS;
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

        double newY = position.y() + velocityY;
        double newX = position.x() + velocityX;
        double newZ = position.z() + velocityZ;

        if (velocityY <= 0 && ground.isSolid(newX, newY - HALF_HEIGHT - GROUND_EPSILON, newZ)) {
            // Snap the item's bottom onto the block surface it landed on.
            newY = Math.floor(newY - HALF_HEIGHT - GROUND_EPSILON) + 1.0 + HALF_HEIGHT;
            velocityY = 0.0;
            onGround = true;
        } else if (velocityY > 0 && ground.isSolid(newX, newY + HALF_HEIGHT + GROUND_EPSILON, newZ)) {
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
}
