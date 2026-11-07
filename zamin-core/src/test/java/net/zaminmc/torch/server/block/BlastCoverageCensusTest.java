package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-EX3'S COVERAGE SEAT (the census the row names, made permanent): every
 * block identifier the behavior table registers must resolve through the
 * {@link BlastResistanceTable} — the reference's own
 * {@code Block.getBlastResistance} read family, never the retired
 * hardness&times;5 approximation's last resort. The 2026-11-07 census:
 * 1,235/1,235 resolved, ZERO fallback misses. The seat guards the tail:
 * any future block registration that forgets its blast-resistance row
 * falls to the approximation and fails here (the row's own value is the
 * contract — stone 6, never 1.5).
 */
class BlastCoverageCensusTest {

    @Test
    void everyRegisteredBlockResolvesThroughTheTable() throws Exception {
        Field f = BlockBehaviorTable.class.getDeclaredField("BEHAVIORS");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Identifier, BlockBehavior> behaviors =
                (Map<Identifier, BlockBehavior>) f.get(null);
        List<String> misses = new ArrayList<>();
        for (Identifier id : behaviors.keySet()) {
            if (BlastResistanceTable.read(id) == null) {
                misses.add(id.value());
            }
        }
        assertTrue(misses.isEmpty(),
                "the blast-resistance table must cover every registered"
                        + " block (F-EX3's census — the fallback misses: "
                        + misses + ")");
    }
}
