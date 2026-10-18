package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The double wooden slab family (Slice 58 — DoubleWoodenSlabBlock 1:1):
 * the six variants over 125 with the two-slab drop law.
 */
class DoubleWoodenSlabTest {

    @Test
    void theSixDoublesCarryTheReferenceBand() {
        // The oak carries the wire's own name; the other woods their own
        // double_{wood}_slab — the variant id whole in the nibble.
        assertEquals("minecraft:double_wooden_slab",
                WoodShapeBlocks.doubleSlabState(0).identifier().toString());
        assertEquals(0, WoodShapeBlocks.doubleSlabState(0).legacyMetadata());
        assertEquals("minecraft:double_dark_oak_slab",
                WoodShapeBlocks.doubleSlabState(5).identifier().toString());
        assertEquals(5, WoodShapeBlocks.doubleSlabState(5).legacyMetadata());
        for (int variant = 0; variant < WoodVariantBlocks.WOODS.size(); variant++) {
            Identifier id = WoodShapeBlocks.doubleSlabState(variant).identifier();
            BlockBehavior behavior = BlockBehaviorTable.of(id).orElseThrow();
            assertEquals(2.0, behavior.hardness());
            assertTrue(!behavior.requiresTool());
            assertEquals("wood", behavior.material());
            // The drop: TWO single slabs (getBaseDropCount's isDouble ? 2
            // : 1) with the variant damage (getDropItemMetadata's band).
            assertEquals(1, behavior.drops().size());
            assertEquals("minecraft:wooden_slab", behavior.drops().get(0).item().toString());
            assertEquals(2, behavior.drops().get(0).count());
            assertEquals(variant, behavior.drops().get(0).damage());
            // The light band: the opaque-partial cube like the singles.
            assertEquals(15, BlockLightTable.lookup(id).orElseThrow().filter());
        }
    }

    @Test
    void theStatsMergesFoldOntoTheWoodenSlabRow() {
        // Slice L4's registry scheme: every 125 state counts on the 125
        // row (the double wooden slab is a registry Block in its own
        // right — the old fold onto the 126 single row was the engine's
        // own simplification).
        assertEquals("stat.mineBlock.minecraft.double_wooden_slab",
                net.zaminmc.torch.server.stat.EngineStats.mineBlockOf(
                        Identifier.parse("minecraft:double_dark_oak_slab")));
        assertEquals("stat.mineBlock.minecraft.double_wooden_slab",
                net.zaminmc.torch.server.stat.EngineStats.mineBlockOf(
                        Identifier.parse("minecraft:double_wooden_slab")));
    }
}
