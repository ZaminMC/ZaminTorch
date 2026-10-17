package net.zaminmc.torch.event.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.event.Cancellable;
import net.zaminmc.torch.event.Event;
import net.zaminmc.torch.item.ItemStack;

/**
 * A furnace's smelt completed (§27.7's pre-commit gate — the cook
 * observe hook).
 *
 * <p>Fires on the world's simulation context at the moment the cook
 * cycle reaches its 200-tick term — one input unit is about to be
 * consumed and the result produced into the output slot. Cancelling
 * prevents the commit: the input is NOT consumed and the output stays
 * unchanged; the cycle's progress is already spent (the reference's
 * own reset happens before the commit — a cancelled smelt restarts the
 * cook cycle, exactly like a cycle whose result stopped fitting). The
 * furnace keeps burning its fuel.</p>
 *
 * <p>Threading: handlers run on the world's simulation context (the
 * furnace tick's own thread). Handlers must not block.</p>
 */
public final class FurnaceSmeltEvent implements Event, Cancellable {

    private final String worldName;
    private final BlockPosition position;
    private final ItemStack input;
    private final ItemStack result;
    private boolean cancelled;

    /**
     * @param worldName the world's own name
     * @param position  the furnace's position
     * @param input     the input stack one unit of which the smelt
     *                  consumes (the live stack read — treat as
     *                  immutable)
     * @param result    the result the smelt would produce (a fresh
     *                  one-smelt stack — for example one iron ingot)
     */
    public FurnaceSmeltEvent(String worldName, BlockPosition position,
                             ItemStack input, ItemStack result) {
        this.worldName = worldName;
        this.position = position;
        this.input = input;
        this.result = result;
    }

    /** The world the furnace smelts in (the world's own name). */
    public String worldName() {
        return worldName;
    }

    /** The furnace's position. */
    public BlockPosition position() {
        return position;
    }

    /**
     * @return the input stack the smelt draws from (one unit is
     *         consumed on commit)
     */
    public ItemStack input() {
        return input;
    }

    /** @return the result stack the smelt would produce. */
    public ItemStack result() {
        return result;
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
        return "FurnaceSmelt";
    }
}
