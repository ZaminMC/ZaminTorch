package net.zaminmc.torch.entity;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Position;

/**
 * The public handle of one falling block entity (the gravity blocks:
 * sand and gravel in flight — architecture §27.3).
 *
 * <p>Threading contract (§27.6): every method touches live authoritative
 * state and must run on the world's simulation context — the documented
 * owner path is {@code World.execute(runnable)}. For off-owner
 * observation use {@link #snapshot()}. Obtain handles through
 * {@code World.fallingBlocks()} (a point-in-time population view).</p>
 */
public interface FallingBlock extends Entity {

    /** The block kind in flight. Simulation context. */
    BlockType block();

    /** The body's current center position. Simulation context. */
    Position position();

    /** The fall's age in ticks. Simulation context. */
    int fallingTicks();

    /** Whether the body rests on solid ground. Simulation context. */
    boolean onGround();
}
