package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;

/**
 * Engine-internal world observation hook. This is an internal coordination
 * event, not a public plugin event (§76): the protocol adapter subscribes to
 * synchronize clients, nothing else sees it.
 */
public interface WorldChangeListener {

    /**
     * Called after a block state was committed in the world. Runs on the
     * simulation thread; implementers must not block or mutate world state.
     */
    void onBlockChanged(EngineWorld world, BlockPosition position, BlockType newType);

    /**
     * The complete pair in one call (Slice 14a — the public event's shape):
     * the old and the new type of one committed change. Runs first, inside
     * the same commit; the default ignores it.
     */
    default void onBlockReplaced(EngineWorld world, BlockPosition position,
                                 BlockType oldType, BlockType newType) {
    }

    /**
     * The removal companion: the OLD type at the position that just became
     * something else (air included). Runs inside the same commit, after
     * {@link #onBlockChanged}; the default ignores it. The redstone family
     * needs the leaving type (the reference's {@code onRemoved} arms read
     * the removed block's state — a departing wire's two-hop notification
     * ring, a departing lit torch's neighbor update).
     */
    default void onBlockRemoved(EngineWorld world, BlockPosition position, BlockType oldType) {
    }
}
