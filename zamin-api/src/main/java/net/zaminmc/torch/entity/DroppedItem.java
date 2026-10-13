package net.zaminmc.torch.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;

/**
 * The public handle of one dropped item in the world (architecture §27.3's
 * entity-tracking family).
 *
 * <p>Threading contract (§27.6): every method touches live authoritative
 * state and must run on the world's simulation context — the documented
 * owner path is {@code World.execute(runnable)}. For off-owner
 * observation use {@link #snapshot()}. Obtain handles through
 * {@code World.itemEntities()} (a point-in-time population view).</p>
 */
public interface DroppedItem extends Entity {

    /** The item contents. Simulation context. */
    ItemStack stack();

    /** Feet-anchored world position. Simulation context. */
    Position position();

    /** x velocity in blocks per tick. Simulation context. */
    double velocityX();

    /** y velocity in blocks per tick. Simulation context. */
    double velocityY();

    /** z velocity in blocks per tick. Simulation context. */
    double velocityZ();

    /** The age in ticks since the drop appeared. Simulation context. */
    int age();

    /**
     * The remaining pickup-delay ticks (a fresh block drop answers 10; a
     * player-thrown drop 40). Simulation context.
     */
    int pickupDelayTicks();

    /** Whether the item rests on solid ground. Simulation context. */
    boolean onGround();
}
