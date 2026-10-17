package net.zaminmc.torch.event.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.event.Cancellable;
import net.zaminmc.torch.event.Event;
import net.zaminmc.torch.item.ItemStack;

/**
 * A hopper is about to move items (§27.7's pre-commit gate — the
 * transfer observe hook).
 *
 * <p>Fires on the world's simulation context inside the transfer's
 * boundary scope, once per prospective item move, right before any
 * slot mutation: the push (one item from the hopper into the container
 * it faces), the pull (one item from the container above into the
 * hopper), or the suck (the item entities in the hopper's collection
 * box). Cancelling prevents THIS move only — the item stays where it
 * is and the walk's own rules continue (the next slot's item gets its
 * own event on the same cycle; a fully-cancelled cycle arms no
 * cooldown and retries next transfer cadence).</p>
 *
 * <p>Threading: handlers run on the world's simulation context (the
 * hopper tick's own thread, inside the transfer's region scope).
 * Handlers must not block.</p>
 */
public final class HopperTransferEvent implements Event, Cancellable {

    /** The transfer direction (the reference's three arms). */
    public enum Direction {
        /** One item from the hopper into the container it faces. */
        PUSH,
        /** One item from the container above into the hopper. */
        PULL,
        /** The item entities in the hopper's collection box above. */
        SUCK
    }

    private final String worldName;
    private final BlockPosition hopper;
    private final Direction direction;
    private final BlockPosition counterpart;
    private final ItemStack item;
    private boolean cancelled;

    /**
     * @param worldName   the world's own name
     * @param hopper      the hopper's position
     * @param direction   the transfer arm (push/pull/suck)
     * @param counterpart the other end — the faced container (PUSH),
     *                    the container above (PULL), or the item
     *                    entity's block cell (SUCK)
     * @param item        the stack being moved — count 1 for PUSH/PULL
     *                    (one item per transfer, the reference's own
     *                    law); the entity's whole stack for SUCK (a
     *                    partial suck keeps the remainder)
     */
    public HopperTransferEvent(String worldName, BlockPosition hopper,
                               Direction direction, BlockPosition counterpart,
                               ItemStack item) {
        this.worldName = worldName;
        this.hopper = hopper;
        this.direction = direction;
        this.counterpart = counterpart;
        this.item = item;
    }

    /** The world the transfer happens in (the world's own name). */
    public String worldName() {
        return worldName;
    }

    /** The hopper's position. */
    public BlockPosition hopper() {
        return hopper;
    }

    /** The transfer arm (push/pull/suck). */
    public Direction direction() {
        return direction;
    }

    /**
     * @return the other end — the faced container (PUSH), the
     *         container above (PULL), or the item entity's block cell
     *         (SUCK)
     */
    public BlockPosition counterpart() {
        return counterpart;
    }

    /**
     * @return the stack being moved (count 1 for PUSH/PULL; the
     *         entity's whole stack for SUCK)
     */
    public ItemStack item() {
        return item;
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
        return "HopperTransfer";
    }
}
