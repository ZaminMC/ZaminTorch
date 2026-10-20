package net.zaminmc.torch.event.player;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.event.Event;

import java.util.UUID;

/**
 * A player opened any container window (§27.7's shared post-commit
 * observation over the whole container family — the chest family's own
 * {@link net.zaminmc.torch.event.block.ChestOpenEvent} stays, this is the
 * shared form every container fires). Fired after the window state
 * committed: the window id is assigned, the family's own observations (the
 * chest's lid, the trapped chest's neighbor ring, the stat rows) already
 * ran. Not cancellable — the window already opened; handlers observe and
 * react.
 *
 * <p>Covers every container family the engine opens: the block containers
 * (the chest pair, the ender chest, the furnace, the dispenser pair, the
 * hopper, the brewing stand, the beacon, the anvil, the enchanting table,
 * the crafting table) and the entity containers (the horse inventory, the
 * chest and hopper minecarts, the villager trades) — one event, the
 * {@link #kind() kind} naming the family, the {@link #position() position}
 * carrying the block cell for block containers (null for the entity
 * forms), the {@link #mountId() mount id} the entity id for entity
 * containers (-1 for the block forms).</p>
 *
 * <p>Threading: handlers run on the world's simulation context (the
 * interact walk's own thread). Handlers must not block or mutate world
 * state (queue through the engine's own paths instead).</p>
 */
public final class ContainerOpenEvent implements Event {

    private final UUID openerId;
    private final String openerName;
    private final String kind;
    private final BlockPosition position;
    private final int mountId;
    private final int windowId;

    public ContainerOpenEvent(UUID openerId, String openerName, String kind,
                              BlockPosition position, int mountId, int windowId) {
        this.openerId = openerId;
        this.openerName = openerName;
        this.kind = kind;
        this.position = position;
        this.mountId = mountId;
        this.windowId = windowId;
    }

    /** @return the opening player's uuid. */
    public UUID openerId() {
        return openerId;
    }

    /** @return the opening player's name. */
    public String openerName() {
        return openerName;
    }

    /** @return the container family's own name (the session's kind name). */
    public String kind() {
        return kind;
    }

    /** @return the container's block cell, or null for the entity forms. */
    public BlockPosition position() {
        return position;
    }

    /** @return the container entity's engine id, or -1 for the block forms. */
    public int mountId() {
        return mountId;
    }

    /** @return the assigned window id (the wire's own counter). */
    public int windowId() {
        return windowId;
    }

    @Override
    public String eventName() {
        return "ContainerOpen";
    }
}
