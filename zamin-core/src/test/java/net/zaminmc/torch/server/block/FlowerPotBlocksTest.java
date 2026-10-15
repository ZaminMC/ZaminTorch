package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flower-pot family (Slice 48 — FlowerPotBlock 1:1): the fourteen
 * states over the reference's LEGACY_DATA band, the pottable consult, and
 * the family tables.
 */
class FlowerPotBlocksTest {

    @Test
    void theFourteenStatesCarryTheReferenceBand() {
        for (int content = 0; content <= 13; content++) {
            EngineBlockType state = FlowerPotBlocks.state(content);
            assertEquals(content, state.legacyMetadata());
            assertEquals(content, FlowerPotBlocks.contentOf(state.identifier()));
            assertTrue(FlowerPotBlocks.isPot(state.identifier()));
            // The behavior rows: the 0.0 plant dropping the pot AND the
            // plant (the saplings over the wood damage, the fern the tall
            // grass's damage 2).
            BlockBehavior behavior = BlockBehaviorTable.of(state.identifier()).orElseThrow();
            assertEquals(0.0, behavior.hardness());
            assertEquals(FlowerPotBlocks.POT_ITEM, behavior.drops().get(0).item());
            if (content > 0) {
                assertEquals(FlowerPotBlocks.plantItemOf(content),
                        behavior.drops().get(1).item());
                assertEquals(FlowerPotBlocks.plantDamageOf(content),
                        behavior.drops().get(1).damage());
            }
            // The light rows: the 3/8 pot passes.
            assertEquals(0, BlockLightTable.lookup(state.identifier()).orElseThrow().filter());
        }
        // The empty form IS the plain name.
        assertEquals("minecraft:flower_pot", FlowerPotBlocks.stateId(0).toString());
        assertTrue(FlowerPotBlocks.isEmptyPot(Identifier.parse("minecraft:flower_pot")));
        // The pottable consult (isPottablePlant lines 84-93): the fern
        // damage alone of the tall grass, the poppy's TYPE-0 form.
        assertTrue(FlowerPotBlocks.isPottable(Identifier.parse("minecraft:dandelion"), 0));
        assertTrue(FlowerPotBlocks.isPottable(Identifier.parse("minecraft:poppy"), 0));
        assertTrue(FlowerPotBlocks.isPottable(Identifier.parse("minecraft:sapling"), 4));
        assertTrue(FlowerPotBlocks.isPottable(Identifier.parse("minecraft:tall_grass"), 2));
        assertTrue(!FlowerPotBlocks.isPottable(Identifier.parse("minecraft:tall_grass"), 1));
        assertTrue(!FlowerPotBlocks.isPottable(Identifier.parse("minecraft:stone"), 0));
        // The potted state's write (createBlockEntity's own mapping).
        assertEquals("minecraft:potted_dark_oak_sapling", FlowerPotBlocks.pottedStateOf(
                Identifier.parse("minecraft:sapling"), 5).orElseThrow().toString());
        assertEquals("minecraft:potted_fern", FlowerPotBlocks.pottedStateOf(
                Identifier.parse("minecraft:tall_grass"), 2).orElseThrow().toString());
        // The plant item/damage walks (onRemoved's own drop).
        assertEquals("minecraft:sapling", FlowerPotBlocks.plantItemOf(13).toString());
        assertEquals(5, FlowerPotBlocks.plantDamageOf(13));
        assertEquals("minecraft:tall_grass", FlowerPotBlocks.plantItemOf(11).toString());
        assertEquals(2, FlowerPotBlocks.plantDamageOf(11));
        assertEquals(140, FlowerPotBlocks.WIRE_ID);
    }
}
