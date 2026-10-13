package net.zaminmc.torch.event.block;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.Direction;
import net.zaminmc.torch.event.Cancellable;
import net.zaminmc.torch.event.Event;
import net.zaminmc.torch.item.ItemStack;

/**
 * A dispenser or dropper is about to fire its picked stack (§27.7's
 * observe/cancel gate over the dispense family — the scheduled redstone
 * tick, whatever behavior would have run).
 *
 * <p>Fires on the world's simulation context BEFORE the behavior walk:
 * the stack is still in the reservoir, no item has left, no projectile
 * spawned, no block placed. Cancelling prevents the dispense entirely —
 * the stack stays in its slot untouched and nothing happens (not even
 * the empty click sound). The custom-behavior surface
 * ({@code Server.dispenserBehaviors()}) is unaffected: the gate wraps
 * the whole family, built-in and custom behaviors alike.</p>
 */
public final class DispenseEvent implements Event, Cancellable {

    private final World world;
    private final BlockPosition origin;
    private final Direction facing;
    private final ItemStack stack;
    private final BlockPosition facingCell;
    private final boolean dropper;
    private boolean cancelled;

    /**
     * @param dropper true when the firing block is a dropper (the plain
     *                item-egress family, never a behavior)
     */
    public DispenseEvent(World world, BlockPosition origin, Direction facing,
                         ItemStack stack, BlockPosition facingCell, boolean dropper) {
        this.world = world;
        this.origin = origin;
        this.facing = facing;
        this.stack = stack;
        this.facingCell = facingCell;
        this.dropper = dropper;
    }

    /** The world the dispenser stands in. */
    public World world() {
        return world;
    }

    /** The dispensing block's position. */
    public BlockPosition origin() {
        return origin;
    }

    /** The dispenser's facing (the historical 0-5 id order). */
    public Direction facing() {
        return facing;
    }

    /** The stack the reservoir picked (a copy — the slot keeps its own). */
    public ItemStack stack() {
        return stack;
    }

    /** The cell the dispenser faces — the classic dispense target. */
    public BlockPosition facingCell() {
        return facingCell;
    }

    /** @return whether the firing block is a dropper (no behavior runs). */
    public boolean dropper() {
        return dropper;
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
        return "Dispense";
    }
}
