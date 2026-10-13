package net.zaminmc.torch.event.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.event.Event;

import java.util.UUID;

/**
 * A player opened a chest's window (§27.7's post-commit observation over
 * the container family). Fired after the window state committed: the
 * window id is assigned, the lid swings for every other observer. Not
 * cancellable — the window already opened; handlers observe and react.
 *
 * <p>Threading: handlers run on the world's simulation context (the
 * interact walk's own thread). Handlers must not block or mutate world
 * state (queue through the engine's own paths instead).</p>
 */
public final class ChestOpenEvent implements Event {

    private final String worldName;
    private final BlockPosition position;
    private final UUID openerId;
    private final String openerName;

    public ChestOpenEvent(String worldName, BlockPosition position,
                          UUID openerId, String openerName) {
        this.worldName = worldName;
        this.position = position;
        this.openerId = openerId;
        this.openerName = openerName;
    }

    /** @return the world the chest stands in (by name — the handle-free form). */
    public String worldName() {
        return worldName;
    }

    /** @return the chest's position. */
    public BlockPosition position() {
        return position;
    }

    /** @return the opening player's uuid. */
    public UUID openerId() {
        return openerId;
    }

    /** @return the opening player's name. */
    public String openerName() {
        return openerName;
    }

    @Override
    public String eventName() {
        return "ChestOpen";
    }
}
