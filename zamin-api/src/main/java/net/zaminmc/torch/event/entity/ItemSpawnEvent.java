package net.zaminmc.torch.event.entity;

import net.zaminmc.torch.event.Cancellable;
import net.zaminmc.torch.event.Event;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;

/**
 * An item entity is about to enter the world (§27.7's observe/cancel
 * gate over the drop family — the block-break loot, the mob deaths, the
 * player throws, the container spills, the dispenser's plain egress all
 * ride the same chokepoint).
 *
 * <p>Fires on the world's simulation context BEFORE the entity joins
 * the world. Cancelling prevents the drop entirely: the stack never
 * enters the world, no id is consumed, nothing lies on the ground — the
 * clean-drops plugin's own verb. The stack itself is untouched (a
 * cancelled drop does not destroy the items, it withholds them — what
 * the caller does with the withheld stack is the caller's business).</p>
 */
public final class ItemSpawnEvent implements Event, Cancellable {

    private final ItemStack stack;
    private final Position position;
    private boolean cancelled;

    public ItemSpawnEvent(ItemStack stack, Position position) {
        this.stack = stack;
        this.position = position;
    }

    /** The stack about to drop (the event's own copy — mutate nothing). */
    public ItemStack stack() {
        return stack;
    }

    /** The drop position in world space. */
    public Position position() {
        return position;
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
        return "ItemSpawn";
    }
}
