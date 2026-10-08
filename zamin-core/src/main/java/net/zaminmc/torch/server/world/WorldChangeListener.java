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
}
