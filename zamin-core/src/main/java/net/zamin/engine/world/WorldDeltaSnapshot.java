package net.zamin.engine.world;

import net.zamin.api.BlockType;

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
 * @param totalTicks total simulated ticks
 * @param timeOfDay  time-of-day ticks (0..23999)
 * @param deltas     chunk-packed position -> (local block index -> type)
 */
public record WorldDeltaSnapshot(
        long totalTicks,
        long timeOfDay,
        Map<Long, Map<Integer, BlockType>> deltas) {
}
