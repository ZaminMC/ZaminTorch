package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.RedstoneSignals;

/**
 * The public redstone read views (§27.3's redstone family): the
 * engine's own power walks dressed as the {@link RedstoneSignals}
 * interface — every consult delegates to the system's own neighbor
 * signal walk (owner-context consistent reads, pure — no component
 * state advances).
 */
public final class RedstoneSignalViews implements RedstoneSignals {

    private final RedstoneSystem system;

    public RedstoneSignalViews(RedstoneSystem system) {
        this.system = system;
    }

    @Override
    public int powerAt(BlockPosition position) {
        return system.neighborSignal(position);
    }

    @Override
    public boolean poweredAt(BlockPosition position) {
        return system.hasNeighborSignal(position);
    }
}
