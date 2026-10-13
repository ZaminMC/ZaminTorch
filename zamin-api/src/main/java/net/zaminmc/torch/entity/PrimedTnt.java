package net.zaminmc.torch.entity;

import net.zaminmc.torch.util.Position;

/**
 * The public handle of one primed TNT body in flight (§27.3's TNT
 * family): the block has left the world, the fuse is burning.
 *
 * <p>Threading contract (§27.6): every method touches live authoritative
 * state and must run on the world's simulation context — the documented
 * owner path is {@code World.execute(runnable)}. For off-owner
 * observation use {@link #snapshot()}. Obtain handles through
 * {@code World.primedTnt()} (a point-in-time population view).</p>
 */
public interface PrimedTnt extends Entity {

    /** The body's world position. Simulation context. */
    Position position();

    /** x velocity in blocks per tick. Simulation context. */
    double velocityX();

    /** y velocity in blocks per tick. Simulation context. */
    double velocityY();

    /** z velocity in blocks per tick. Simulation context. */
    double velocityZ();

    /**
     * The remaining fuse ticks (the vanilla prime starts at 80; the
     * blast-primed chain reaction starts at a quarter or less).
     * Simulation context.
     */
    int fuseTicks();

    /** Whether the body rests on the ground. Simulation context. */
    boolean onGround();
}
