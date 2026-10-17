package net.zaminmc.torch.event.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.event.Cancellable;
import net.zaminmc.torch.event.Event;

/**
 * A fluid is about to flow into a cell (§27.7's pre-commit gate — the
 * observe/cancel hook of the flow walk).
 *
 * <p>Fires on the world's simulation context right before the flow
 * commits — one event per prospective landing cell (a source spreading
 * sideways fires one per open neighbor). Cancelling prevents THIS flow
 * step only: the cell behaves as blocked ground for this spread — the
 * fluid never enters it, and the walk's own rules continue around it
 * (a cancelled down-pour lets the source spread sideways, exactly as
 * solid ground below would).</p>
 *
 * <p>The event covers flow steps only. The contact reactions (water
 * hardening lava into obsidian or cobblestone) and every committed flow
 * cell ride the post-commit {@code BlockChangedEvent} — the destruction
 * and conversion observations belong to that event's own contract.</p>
 *
 * <p>Threading: handlers run on the world's simulation context (the
 * fluid tick's own thread). Handlers must not block; mutations inside
 * the handler are legal only through the owner's validated context.</p>
 */
public final class FluidFlowEvent implements Event, Cancellable {

    private final String worldName;
    private final BlockPosition from;
    private final BlockPosition to;
    private final String fluid;
    private final int level;
    private boolean cancelled;

    /**
     * @param worldName the world's own name
     * @param from      the cell the fluid spreads from
     * @param to        the landing cell the fluid is about to enter
     * @param fluid     the fluid's identifier (for example
     *                  {@code minecraft:water})
     * @param level     the flow level landing in the cell (8 for a
     *                  falling column, the decayed level for a
     *                  sideways stream)
     */
    public FluidFlowEvent(String worldName, BlockPosition from, BlockPosition to,
                          String fluid, int level) {
        this.worldName = worldName;
        this.from = from;
        this.to = to;
        this.fluid = fluid;
        this.level = level;
    }

    /** The world the flow happens in (the world's own name). */
    public String worldName() {
        return worldName;
    }

    /** The cell the fluid spreads from. */
    public BlockPosition from() {
        return from;
    }

    /** The landing cell the fluid is about to enter. */
    public BlockPosition to() {
        return to;
    }

    /** The fluid's identifier (for example {@code minecraft:water}). */
    public String fluid() {
        return fluid;
    }

    /**
     * @return the flow level landing in the cell — 8 for a falling
     *         column, the decayed level for a sideways stream (0 at a
     *         source's own cells)
     */
    public int level() {
        return level;
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
        return "FluidFlow";
    }
}
