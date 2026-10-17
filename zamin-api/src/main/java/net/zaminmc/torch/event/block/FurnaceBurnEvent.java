package net.zaminmc.torch.event.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.event.Cancellable;
import net.zaminmc.torch.event.Event;
import net.zaminmc.torch.item.ItemStack;

/**
 * A furnace is about to ignite its fuel (§27.7's pre-commit gate — the
 * burn observe hook).
 *
 * <p>Fires on the world's simulation context at the ignite decision —
 * the furnace is cold, a smeltable input waits, and one unit of fuel is
 * about to be consumed to start its burn cycle. Cancelling prevents the
 * ignition entirely: the fuel is NOT consumed and the burn does not
 * start (the furnace stays cold and unlit; the input keeps waiting).
 * The next tick re-attempts the ignition, so a plugin cancelling every
 * ignite keeps the furnace cold without losing anything.</p>
 *
 * <p>Threading: handlers run on the world's simulation context (the
 * furnace tick's own thread). Handlers must not block.</p>
 */
public final class FurnaceBurnEvent implements Event, Cancellable {

    private final String worldName;
    private final BlockPosition position;
    private final ItemStack fuel;
    private final int burnTicks;
    private boolean cancelled;

    /**
     * @param worldName the world's own name
     * @param position  the furnace's position
     * @param fuel      the fuel stack one unit of which the ignition
     *                  consumes (the live stack read — treat as
     *                  immutable)
     * @param burnTicks the fuel's full burn value in ticks (the
     *                  historical per-fuel table — coal 1600, planks
     *                  300, and so on)
     */
    public FurnaceBurnEvent(String worldName, BlockPosition position,
                            ItemStack fuel, int burnTicks) {
        this.worldName = worldName;
        this.position = position;
        this.fuel = fuel;
        this.burnTicks = burnTicks;
    }

    /** The world the furnace burns in (the world's own name). */
    public String worldName() {
        return worldName;
    }

    /** The furnace's position. */
    public BlockPosition position() {
        return position;
    }

    /**
     * @return the fuel stack the ignition draws from (one unit is
     *         consumed on commit)
     */
    public ItemStack fuel() {
        return fuel;
    }

    /** @return the burn cycle's length in ticks (the fuel's own value). */
    public int burnTicks() {
        return burnTicks;
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
        return "FurnaceBurn";
    }
}
