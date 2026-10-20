package net.zaminmc.torch.block;

import net.zaminmc.torch.World;
import net.zaminmc.torch.util.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * The scheduled block-tick surface (§27.3's family): the reference
 * 1.8.8's own pending TileTicks queue — entries carrying a position,
 * the block family that asked for the wake-up, a due time in total
 * world ticks, and a priority — reached through
 * {@link World#scheduledBlockTicks()}.
 *
 * <p>This is the engine's second, distinct queue: the neighbor-update
 * family rides {@link ScheduledUpdates}; this surface carries the
 * reference's {@code ServerWorld.scheduleTick}/{@code tickUpdates}
 * machinery (the anvil TileTicks import lands here, and the families'
 * delayed arms migrate onto it family by family).</p>
 *
 * <p><b>Threading (§27.6):</b> the queries answer best-effort from any
 * thread (owner-context consistent when read inside
 * {@link World#execute}); the mutations ({@link #cancelAt},
 * {@link #scheduleAt}) must run on the world's authoritative simulation
 * context — call them through {@link World#execute} off the lane (they
 * throw outside it).</p>
 */
public interface ScheduledBlockTicks {

    /** The empty world's answer (nothing pending, nothing cancellable). */
    ScheduledBlockTicks EMPTY = new ScheduledBlockTicks() {
        @Override public List<PendingTick> pending() {
            return List.of();
        }

        @Override public Optional<PendingTick> pendingAt(BlockPosition position, Identifier blockFamily) {
            return Optional.empty();
        }

        @Override public boolean cancelAt(BlockPosition position, Identifier blockFamily) {
            return false;
        }

        @Override public void scheduleAt(BlockPosition position, Identifier blockFamily, int delay, int priority) {
            // nothing scheduled in an empty world
        }
    };

    /**
     * @return a copy of the pending scheduled block ticks in the queue's
     *         own order (due time, then priority, then insertion).
     */
    List<PendingTick> pending();

    /**
     * @param position the tick's position
     * @param blockFamily the block family the entry named
     * @return the pending entry for the position and family, or empty
     */
    Optional<PendingTick> pendingAt(BlockPosition position, Identifier blockFamily);

    /**
     * Cancels the pending entry for the position and family.
     *
     * @return whether a pending entry was cancelled
     */
    boolean cancelAt(BlockPosition position, Identifier blockFamily);

    /**
     * Schedules one entry (the reference's scheduleTick): the block
     * family names the waker; the delay counts from the current total
     * world time; priority orders same-time entries (smaller first).
     * A second schedule while an entry is pending changes nothing.
     */
    void scheduleAt(BlockPosition position, Identifier blockFamily, int delay, int priority);

    /** One pending scheduled block tick. */
    record PendingTick(BlockPosition position, Identifier blockFamily, long dueTick, int priority) {
    }
}
