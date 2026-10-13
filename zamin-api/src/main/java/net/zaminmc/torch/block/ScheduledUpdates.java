package net.zaminmc.torch.block;

import net.zaminmc.torch.World;

import java.util.List;
import java.util.Optional;

/**
 * The scheduled block-update surface (§27.3's scheduled-block-tick
 * family): the engine's neighbor-update queue and its gravity fuses —
 * what plugins may query, cancel, and request, reached through
 * {@link World#scheduledUpdates()}.
 *
 * <p>The engine's own model (the historical one-update-per-position
 * rule): every committed block change schedules an update for the cell
 * and its six neighbors due the same tick; a cell carries at most one
 * pending update (later requests coalesce — the earliest due tick
 * wins); sand and gravel wake a two-tick gravity fuse before they
 * convert into falling bodies.</p>
 *
 * <p><b>Threading (§27.6):</b> the queries answer best-effort from any
 * thread (owner-context consistent when read inside {@link World#execute});
 * the mutations ({@link #cancelAt}, {@link #cancelFallCheckAt},
 * {@link #requestAt}) must run on the world's authoritative simulation
 * context — call them through {@link World#execute} off the lane (they
 * throw outside it).</p>
 */
public interface ScheduledUpdates {

    /** The empty world's answer (nothing pending, nothing cancellable). */
    ScheduledUpdates EMPTY = new ScheduledUpdates() {
        @Override public List<PendingUpdate> pending() {
            return List.of();
        }

        @Override public List<PendingUpdate> pendingFallChecks() {
            return List.of();
        }

        @Override public Optional<PendingUpdate> pendingAt(BlockPosition position) {
            return Optional.empty();
        }

        @Override public boolean cancelAt(BlockPosition position) {
            return false;
        }

        @Override public boolean cancelFallCheckAt(BlockPosition position) {
            return false;
        }

        @Override public void requestAt(BlockPosition position) {
            // nothing scheduled in an empty world
        }
    };

    /**
     * @return a copy of the pending neighbor updates (the scheduled
     *         block ticks), in the queue's own order.
     */
    List<PendingUpdate> pending();

    /**
     * @return a copy of the pending gravity checks (the sand/gravel
     *         two-tick fuses), in the fuse queue's own order.
     */
    List<PendingUpdate> pendingFallChecks();

    /** @return the pending neighbor update for the position, or empty. */
    Optional<PendingUpdate> pendingAt(BlockPosition position);

    /**
     * Cancels the position's pending neighbor update. Owner context.
     *
     * @return whether a pending update was cancelled
     */
    boolean cancelAt(BlockPosition position);

    /**
     * Cancels the position's pending gravity fuse. Owner context.
     *
     * @return whether a pending fuse was cancelled
     */
    boolean cancelFallCheckAt(BlockPosition position);

    /**
     * Requests a neighbor update for the position due the current tick
     * (the engine's own schedule arm — the update runs its rules on the
     * due tick; a cell already carrying a pending update keeps its
     * earlier due tick). Owner context.
     */
    void requestAt(BlockPosition position);

    /** One pending scheduled update: the cell and its due tick. */
    record PendingUpdate(BlockPosition position, long dueTick) {
    }
}
