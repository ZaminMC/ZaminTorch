package net.zaminmc.torch.entity;

import net.zaminmc.torch.entity.ai.GoalRegistration;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;

/**
 * The public live surface of one living engine entity (a mob).
 *
 * <p>Threading contract (architecture §27.6): every method on this
 * interface touches live authoritative state and must run on the
 * entity's simulation context. The documented owner path for plugins is
 * {@code World.execute(runnable)} — work submitted there runs on the
 * authoritative context between ticks. Mutations from any other context
 * are ownership violations and fail loudly in development builds. For
 * off-owner observation use {@link Entity#snapshot()}.</p>
 */
public interface LivingEntity extends Entity {

    /** Feet-anchored world position. Simulation context. */
    Position position();

    /** Body rotation (mobs carry no pitch — always 0). Simulation context. */
    Rotation rotation();

    /** x velocity in blocks per tick. Simulation context. */
    double velocityX();

    /** y velocity in blocks per tick. Simulation context. */
    double velocityY();

    /** z velocity in blocks per tick. Simulation context. */
    double velocityZ();

    /** Current health. Simulation context. */
    float health();

    /** The kind's maximum health. */
    float maxHealth();

    /** Whether the entity is dead or in its death animation. */
    boolean dead();

    /** Whether the entity is grounded. */
    boolean onGround();

    /** Restores health up to the kind's maximum. Simulation context. */
    void heal(float amount);

    /** Applies direct damage (the raw amount — no source, no armor walk). */
    void hurt(float amount);

    /**
     * Adds an impulse to the entity's velocity (knockback-style:
     * the component is added to the current velocity and consumed by the
     * next movement tick).
     */
    void addVelocity(double dx, double dy, double dz);

    /**
     * Points the entity's navigation at a world position (a hint for the
     * next AI ticks; combat and panic goals may override it).
     */
    void navigateToward(double x, double y, double z);

    /**
     * Whether the entity is exempt from the despawn distance rules
     * (named pets, persistent engine mobs).
     */
    boolean persistent();

    /** Marks or clears the despawn exemption. Simulation context. */
    void setPersistent(boolean persistent);

    /**
     * Removes the entity from the world (despawn: no drops, no death
     * animation). Idempotent. Simulation context.
     */
    void despawn();

    /**
     * The mind's registration surface (§27.3's mob-AI family): register,
     * remove, restore, and observe this body's goals. The mutation verbs
     * carry their own admission onto the owner lane (see
     * {@link GoalRegistration}) — the handle itself is safe to obtain
     * from any thread.
     */
    GoalRegistration goals();
}
