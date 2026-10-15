package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The barrier (Slice 56 — BarrierBlock 1:1): the invisible indestructible
 * solid and its rows.
 */
class BarrierBlockTest {

    @Test
    void theBarrierCarriesTheReferenceRows() {
        BlockBehavior behavior = BlockBehaviorTable.of(
                Identifier.parse("minecraft:barrier")).orElseThrow();
        assertEquals(-1.0, behavior.hardness(), "setUnbreakable");
        assertFalse(behavior.diggable());
        assertTrue(behavior.drops().isEmpty(), "dropItems' empty override");
        // The light rows: the translucent invisible solid passes whole.
        assertEquals(0, BlockLightTable.lookup(
                Identifier.parse("minecraft:barrier")).orElseThrow().emission());
        assertEquals(0, BlockLightTable.lookup(
                Identifier.parse("minecraft:barrier")).orElseThrow().filter());
        // The invisible solid still blocks a body (the full cube).
        assertTrue(net.zaminmc.torch.server.block.WorldSolidity.isSolid(
                BuiltinBlocks.BARRIER));
        // The item exists (the /give route over the shared identifier 166).
        assertTrue(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:barrier")).isPresent());
    }
}
