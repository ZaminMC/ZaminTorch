package net.zaminmc.torch.server.world.light;

import net.zaminmc.torch.block.ChunkPosition;

import java.util.ArrayList;
import java.util.List;

/**
 * A detached initial compute's output: every light write the SAME algorithm
 * would have made on the live routing, recorded in order — the fresh
 * chunk's writes (local indices), the loaded neighbors' writes (slot +
 * local index), and the relight columns the neighbor writes marked (the
 * first-insertion order the live queue preserves).
 *
 * <p>The owner-side apply ({@code LightEngine.applyInitial}) replays these
 * in order through the real chunks' guarded setters, so the resulting
 * light-section materialization matches the live path by construction.</p>
 */
public final class InitialLightResult {

    static final int CHANNEL_BLOCK = 0;
    static final int CHANNEL_SKY = 1;

    private final int[] freshWrites;
    private final int freshWriteCount;
    private final int[] neighborWrites;
    private final int neighborWriteCount;
    private final ChunkPosition[] neighborPositions;
    private final List<ChunkPosition> relightColumns;

    InitialLightResult(int[] freshWrites, int freshWriteCount,
                       int[] neighborWrites, int neighborWriteCount,
                       ChunkPosition[] neighborPositions,
                       List<ChunkPosition> relightColumns) {
        this.freshWrites = freshWrites;
        this.freshWriteCount = freshWriteCount;
        this.neighborWrites = neighborWrites;
        this.neighborWriteCount = neighborWriteCount;
        this.neighborPositions = neighborPositions;
        this.relightColumns = relightColumns;
    }

    /** The fresh chunk's recorded write count (both channels). */
    public int freshWriteCount() {
        return freshWriteCount;
    }

    /** The loaded neighbors' recorded write count (both channels). */
    public int neighborWriteCount() {
        return neighborWriteCount;
    }

    /** The recorded write at index i (fresh chunk). Package-private: the apply's replay. */
    int freshWrite(int i) {
        return freshWrites[i];
    }

    /** The recorded write at index i (a loaded neighbor). Package-private: the apply's replay. */
    int neighborWrite(int i) {
        return neighborWrites[i];
    }

    /** The captured neighbor's position at the 3x3 slot (null when none was captured). */
    ChunkPosition neighborPosition(int slot) {
        return neighborPositions[slot];
    }

    /** The relight columns in first-marked order — the live queue's own order. */
    public List<ChunkPosition> relightColumns() {
        return relightColumns;
    }

    /** The relight column count. */
    public int relightColumnCount() {
        return relightColumns.size();
    }

    /** A diagnostics summary line for the measurement logs. */
    public String summary() {
        return freshWriteCount + " fresh writes, " + neighborWriteCount + " neighbor writes, "
                + relightColumns.size() + " relight columns";
    }

    static List<ChunkPosition> emptyRelightList() {
        return new ArrayList<>();
    }
}
