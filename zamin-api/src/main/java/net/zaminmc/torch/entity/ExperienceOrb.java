package net.zaminmc.torch.entity;

import net.zaminmc.torch.util.Position;

/**
 * The public handle of one experience orb in the world (the mining and
 * combat XP bodies — architecture §27.3).
 *
 * <p>Threading contract (§27.6): every method touches live authoritative
 * state and must run on the world's simulation context — the documented
 * owner path is {@code World.execute(runnable)}. For off-owner
 * observation use {@link #snapshot()}. Obtain handles through
 * {@code World.experienceOrbs()} (a point-in-time population view).</p>
 */
public interface ExperienceOrb extends Entity {

    /** The experience points the orb carries. Simulation context. */
    int amount();

    /** The orb's world position. Simulation context. */
    Position position();

    /** x velocity in blocks per tick. Simulation context. */
    double velocityX();

    /** y velocity in blocks per tick. Simulation context. */
    double velocityY();

    /** z velocity in blocks per tick. Simulation context. */
    double velocityZ();

    /** Whether the orb rests on solid ground. Simulation context. */
    boolean onGround();

    /** Whether the orb's 5-minute lifetime has run out. Simulation context. */
    boolean expired();
}
