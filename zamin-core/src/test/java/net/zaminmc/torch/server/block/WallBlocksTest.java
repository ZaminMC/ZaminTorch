package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wall family (Slice 47 — WallBlock 1:1): the two VARIANT states with
 * the reference's exact metadata band (the connections are the client's
 * own virtual reads), the crafting pair, and the family tables.
 */
class WallBlocksTest {

    @Test
    void theTwoStatesCarryTheReferenceBand() {
        assertEquals("minecraft:cobblestone_wall", WallBlocks.stateId(0).toString());
        assertEquals("minecraft:mossy_cobblestone_wall", WallBlocks.stateId(1).toString());
        for (int variant = 0; variant <= 1; variant++) {
            EngineBlockType state = WallBlocks.state(variant);
            assertEquals(variant, state.legacyMetadata());
            assertEquals(variant, WallBlocks.variantOf(state.identifier()));
            assertTrue(WallBlocks.isWall(state.identifier()));
            // The behavior rows: the cobblestone's own 2.0 ROCK with the
            // pickaxe gate, the family item drop with the VARIANT damage.
            BlockBehavior behavior = BlockBehaviorTable.of(state.identifier()).orElseThrow();
            assertEquals(2.0, behavior.hardness());
            assertTrue(behavior.requiresTool());
            assertEquals(1, behavior.harvestLevel());
            assertEquals(WallBlocks.FAMILY_ITEM, behavior.drops().get(0).item());
            assertEquals(variant, behavior.drops().get(0).damage());
            // The light rows: the non-solid-render partial column passes.
            assertEquals(0, BlockLightTable.lookup(state.identifier()).orElseThrow().filter());
        }
        // The family item's damage consult (Variant.byId's clamp to the
        // NORMAL form on out-of-band damage).
        assertEquals("minecraft:mossy_cobblestone_wall", WallBlocks.placementBlockOf(
                Identifier.parse("minecraft:cobblestone_wall"), 1).orElseThrow().toString());
        assertEquals("minecraft:cobblestone_wall", WallBlocks.placementBlockOf(
                Identifier.parse("minecraft:cobblestone_wall"), 99).orElseThrow().toString());
        assertTrue(WallBlocks.placementBlockOf(
                Identifier.parse("minecraft:dye"), 1).isEmpty());
        // The wire id.
        assertEquals(139, WallBlocks.WIRE_ID);
        // The audit repair: the mossy cobblestone item now exists (the
        // pre-47 behavior row dropped it unregistered).
        assertTrue(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:mossy_cobblestone")).isPresent());
    }
}
