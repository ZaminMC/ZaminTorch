package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockType;

import java.util.Map;

/**
 * The persistable projection of one world's variable state.
 *
 * <p>Persisting only deltas is a deliberate scope decision: the flat-world
 * generator reproduces base terrain deterministically, so only player-caused
 * changes and simulation time need to survive a restart. A full-world format
 * adapter (e.g. Anvil import) is future compatibility work when real demand
 * exists — this format never pretends to be it (§541).</p>
 *
 * @param totalTicks     total simulated ticks
 * @param timeOfDay      time-of-day ticks (0..23999, the wrapped sky-cycle clock)
 * @param rawTimeOfDay   the RAW day clock (VIII14 — the reference's unwrapped
 *                       WorldData.timeOfDay; the moon phase's own day count
 *                       rides its high bits). The ZWD v1 migration reads it
 *                       as the wrapped value (day 0) — the historical
 *                       behavior, named in the storage's own walk.
 * @param inhabitedTimes chunk-packed position -> accrued inhabited ticks
 *                       (VIII14 — the local-difficulty completion's own
 *                       per-chunk rows; only nonzero rows ride)
 * @param deltas         chunk-packed position -> (local block index -> type)
 */
public record WorldDeltaSnapshot(
        long totalTicks,
        long timeOfDay,
        long rawTimeOfDay,
        Map<Long, Long> inhabitedTimes,
        Map<Long, Map<Integer, BlockType>> deltas) {

    /**
     * The historical three-field shape (the ZWD v1 projection): the raw day
     * clock reads as the wrapped value (day 0) and the inhabited table is
     * empty — the pre-VIII14 behavior exactly, kept for the call sites that
     * never carried the new rows.
     */
    public WorldDeltaSnapshot(long totalTicks, long timeOfDay,
                              Map<Long, Map<Integer, BlockType>> deltas) {
        this(totalTicks, timeOfDay, timeOfDay, Map.of(), deltas);
    }
}
