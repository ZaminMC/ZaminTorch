package net.zaminmc.torch.server.entity.ai.pathing;

/**
 * The mob the path stack navigates — the vanilla Entity surface the
 * pathing classes touch, narrowed to what the algorithm reads: the body
 * box (x/z center, y at the FEET — vanilla {@code getShape().minY}), the
 * dimensions, the ground state and the two fluid flags.
 */
public interface MobView {
    /** The body's X center. */
    double x();

    /** The body's Y — the box BOTTOM (vanilla {@code getShape().minY}). */
    double y();

    /** The body's Z center. */
    double z();

    /** The body width (blocks). */
    double width();

    /** The body height (blocks). */
    double height();

    /** Whether the body rests on ground (the follow gate). */
    boolean onGround();

    /** Whether any part of the body is in water. */
    boolean inWater();

    /** Whether any part of the body is in lava. */
    boolean inLava();
}
