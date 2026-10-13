package net.zaminmc.torch.entity;

import net.zaminmc.torch.util.Position;

/**
 * The public handle of one projectile in flight (architecture §27.3's
 * projectile family: arrows, snowballs, eggs, fireballs, splash potions,
 * ender pearls).
 *
 * <p>Threading contract (§27.6): every method touches live authoritative
 * state and must run on the world's simulation context — the documented
 * owner path is {@code World.execute(runnable)}. For off-owner
 * observation use {@link #snapshot()}. Obtain handles through
 * {@code World.projectiles()} (a point-in-time population view).</p>
 */
public interface Projectile extends Entity {

    /** The projectile's kind. Simulation context. */
    ProjectileKind projectileKind();

    /**
     * The thrower's engine entity id, or {@code -1} for engine-sourced
     * bodies (a dispenserless world event). Simulation context.
     */
    int throwerEntityId();

    /** The current world position. Simulation context. */
    Position position();

    /** x velocity in blocks per tick. Simulation context. */
    double velocityX();

    /** y velocity in blocks per tick. Simulation context. */
    double velocityY();

    /** z velocity in blocks per tick. Simulation context. */
    double velocityZ();

    /** The age in ticks since the launch. Simulation context. */
    int age();

    /**
     * Whether the body sticks in the ground (an arrow's resting state —
     * the pickup window). Simulation context.
     */
    boolean inGround();

    /** Whether an arrow carries the critical-hit flag. Simulation context. */
    boolean critical();

    /** Whether the body burns (the flame arrows and fireballs). Simulation context. */
    boolean burning();
}
