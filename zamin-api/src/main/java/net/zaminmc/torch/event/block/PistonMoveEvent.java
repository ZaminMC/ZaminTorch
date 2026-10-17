package net.zaminmc.torch.event.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.Direction;
import net.zaminmc.torch.event.Cancellable;
import net.zaminmc.torch.event.Event;

import java.util.List;

/**
 * A piston is about to move (§27.7's pre-commit gate — the observe/cancel
 * hook of the extend/retract walk).
 *
 * <p>Fires inside the movement's boundary scope, after the block-event
 * re-check gates and BEFORE any world write of the movement: cancelling
 * prevents the whole movement — the piston keeps its current block state
 * (retracted on a cancelled extend, extended on a cancelled retract), no
 * head departs, no block moves or breaks, and no sound plays. The next
 * neighbor change re-arms the vanilla state machine as usual.</p>
 *
 * <p>The move lists are the resolver's own prospective answer for this
 * event's direction (the same walk the movement itself runs): the push
 * chain on an extend, the pull chain on a sticky retract (empty for a
 * plain retract — nothing follows the head home). A cancelled event
 * moves nothing.</p>
 *
 * <p>Threading: handlers run on the committing owner context (inside the
 * movement's boundary scope — the region domains the movement spans).
 * Handlers must not block; mutations inside the handler are legal only
 * for state the handler's own context owns (the engine's position-aware
 * assertions govern every write).</p>
 */
public final class PistonMoveEvent implements Event, Cancellable {

    private final String worldName;
    private final BlockPosition position;
    private final Direction facing;
    private final boolean extending;
    private final boolean sticky;
    private final List<BlockPosition> movedCells;
    private final List<BlockPosition> brokenCells;
    private boolean cancelled;

    public PistonMoveEvent(String worldName, BlockPosition position, Direction facing,
                           boolean extending, boolean sticky,
                           List<BlockPosition> movedCells, List<BlockPosition> brokenCells) {
        this.worldName = worldName;
        this.position = position;
        this.facing = facing;
        this.extending = extending;
        this.sticky = sticky;
        this.movedCells = List.copyOf(movedCells);
        this.brokenCells = List.copyOf(brokenCells);
    }

    /** The world the piston moves in (the world's own name). */
    public String worldName() {
        return worldName;
    }

    /** The piston base's position. */
    public BlockPosition position() {
        return position;
    }

    /** The piston's facing (the head's direction). */
    public Direction facing() {
        return facing;
    }

    /** @return true when the piston extends (pushes); false when it retracts. */
    public boolean extending() {
        return extending;
    }

    /** @return true when the base is a sticky piston (the retract may pull). */
    public boolean sticky() {
        return sticky;
    }

    /**
     * @return the cells the movement would move (immutable, the
     *         resolver's own order — nearest to the piston first)
     */
    public List<BlockPosition> movedCells() {
        return movedCells;
    }

    /**
     * @return the cells the movement would destroy instead of moving
     *         (immutable — the DESTROY-material blocks in the chain's way)
     */
    public List<BlockPosition> brokenCells() {
        return brokenCells;
    }

    @Override
    public boolean cancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public String eventName() {
        return "PistonMove";
    }
}
