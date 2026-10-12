package net.zaminmc.torch.event.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.event.Event;

/**
 * A block state was committed in a world (Slice 14a — the §27.7
 * post-commit observation). Fired after the authoritative mutation —
 * whatever caused it: a player's hand, an explosion, a piston, a fluid,
 * growth, decay. Not cancellable: the change already happened; handlers
 * observe and react, they do not veto.
 *
 * <p>Threading: handlers run on the world's simulation context (the
 * commit's own thread). Handlers must not block or mutate world state
 * (queue through the engine's own paths instead).</p>
 */
public final class BlockChangedEvent implements Event {

    private final String worldName;
    private final BlockPosition position;
    private final String previousType;
    private final String newType;

    /**
     * @param worldName     the world the commit landed in
     * @param position      the committed cell
     * @param previousType  the type that left (an identifier string, or
     *                      null when unknown to the emitter)
     * @param newType       the type that arrived (an identifier string)
     */
    public BlockChangedEvent(String worldName, BlockPosition position,
                             String previousType, String newType) {
        this.worldName = worldName;
        this.position = position;
        this.previousType = previousType;
        this.newType = newType;
    }

    /** @return the world the commit landed in. */
    public String worldName() {
        return worldName;
    }

    /** @return the committed cell. */
    public BlockPosition position() {
        return position;
    }

    /** @return the type that left (null when unknown to the emitter). */
    public String previousType() {
        return previousType;
    }

    /** @return the type that arrived. */
    public String newType() {
        return newType;
    }

    @Override
    public String toString() {
        return "BlockChangedEvent[" + worldName + " " + position + " "
                + previousType + " -> " + newType + "]";
    }
}
