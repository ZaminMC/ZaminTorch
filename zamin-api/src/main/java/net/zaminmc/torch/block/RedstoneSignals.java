package net.zaminmc.torch.block;

import net.zaminmc.torch.World;

/**
 * The redstone read surface (§27.3's redstone family): the power walks
 * a plugin may consult — what a cell receives from its neighbors, the
 * same walks the engine's own components run.
 *
 * <p><b>Threading (§27.6):</b> the walks read live block state —
 * owner-context consistent (wrap reads in {@link World#execute} off
 * the lane). They are pure reads (no component state advances), but
 * each consult walks the cell's neighborhood exactly like the engine's
 * own update paths.</p>
 */
public interface RedstoneSignals {

    /** The empty world's answer (nothing powered). */
    RedstoneSignals EMPTY = new RedstoneSignals() {
        @Override public int powerAt(BlockPosition position) {
            return 0;
        }

        @Override public boolean poweredAt(BlockPosition position) {
            return false;
        }
    };

    /**
     * @param position the cell being fed
     * @return the power the cell receives from its six neighbors (the
     *         strong-power walk — the engine's own neighbor consult,
     *         wire decay and source strengths included), 0 when nothing
     *         feeds it
     */
    int powerAt(BlockPosition position);

    /**
     * @param position the cell being fed
     * @return whether any neighbor feeds the cell (the power walk's
     *         boolean form)
     */
    boolean poweredAt(BlockPosition position);
}
