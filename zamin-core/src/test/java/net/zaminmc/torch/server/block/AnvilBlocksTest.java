package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The anvil family (Slice 53 — AnvilBlock 1:1): the twelve states over the
 * reference's exact band, the family tables, the placement consults, and
 * the progression walk.
 */
class AnvilBlocksTest {

    @Test
    void theTwelveStatesCarryTheReferenceBand() {
        // The nibble: the FACING horizontal id | DAMAGE << 2 (the
        // reference's getMetadataFromState band).
        assertEquals(0, AnvilBlocks.state(0, 0).legacyMetadata());
        assertEquals(3, AnvilBlocks.state(3, 0).legacyMetadata());
        assertEquals(4, AnvilBlocks.state(0, 1).legacyMetadata());
        assertEquals(7, AnvilBlocks.state(3, 1).legacyMetadata());
        assertEquals(8, AnvilBlocks.state(0, 2).legacyMetadata());
        assertEquals(11, AnvilBlocks.state(3, 2).legacyMetadata());
        assertEquals(12, AnvilBlocks.familyBlocks().size());
        for (EngineBlockType anvil : AnvilBlocks.familyBlocks()) {
            assertTrue(AnvilBlocks.isAnvil(anvil.identifier()));
            assertEquals(AnvilBlocks.WIRE_ID, 145);
            // The behavior rows: the reference's own
            // setStrength(5.0F) with the pickaxe gate; the drop the family
            // item with the DAMAGE damage (getDropItemMetadata's band).
            BlockBehavior behavior = BlockBehaviorTable.of(anvil.identifier()).orElseThrow();
            assertEquals(5.0, behavior.hardness());
            assertTrue(behavior.requiresTool());
            assertEquals(1, behavior.harvestLevel());
            assertEquals("rock", behavior.material());
            assertEquals(AnvilBlocks.FAMILY_ITEM, behavior.drops().get(0).item());
            assertEquals(AnvilBlocks.damageOf(anvil.identifier()),
                    behavior.drops().get(0).damage());
            // The light rows: the CUTOUT 0.98 block passes whole.
            assertEquals(0, BlockLightTable.lookup(anvil.identifier()).orElseThrow().filter());
        }
        // The reads: damageOf / facingOf over the flattened names.
        assertEquals(2, AnvilBlocks.damageOf(Identifier.parse("minecraft:anvil_damaged_north")));
        assertEquals(2, AnvilBlocks.facingOf(Identifier.parse("minecraft:anvil_damaged_north")));
        assertEquals(1, AnvilBlocks.damageOf(Identifier.parse("minecraft:anvil_chipped")));
        assertEquals(0, AnvilBlocks.facingOf(Identifier.parse("minecraft:anvil_chipped")));
        // The placement damage consult: the item's damage field is the
        // DAMAGE id (the AnvilItem's domain, the creative menu's three).
        assertEquals(1, AnvilBlocks.placementDamageOf(1));
        assertEquals(0, AnvilBlocks.placementDamageOf(99));
    }

    @Test
    void theProgressionWalkMatchesTheReference() {
        // progressedDamage: 0->1, 1->2, 2->broke (-1) — the reference's
        // ++j > 2 arm.
        assertEquals(1, AnvilBlocks.progressedDamage(0));
        assertEquals(2, AnvilBlocks.progressedDamage(1));
        assertEquals(-1, AnvilBlocks.progressedDamage(2));
    }

    @Test
    void theStatsMergesFoldOntoTheAnvilRow() {
        // The vanilla per-block-id scheme: mining any of the twelve states
        // counts on stat.mineBlock.minecraft.anvil.
        assertEquals("stat.mineBlock.minecraft.anvil",
                net.zaminmc.torch.server.stat.EngineStats.mineBlockOf(
                        Identifier.parse("minecraft:anvil_chipped_north")));
        assertEquals("stat.mineBlock.minecraft.anvil",
                net.zaminmc.torch.server.stat.EngineStats.mineBlockOf(
                        Identifier.parse("minecraft:anvil_damaged_east")));
        assertEquals("stat.mineBlock.minecraft.anvil",
                net.zaminmc.torch.server.stat.EngineStats.mineBlockOf(
                        Identifier.parse("minecraft:anvil")));
    }

    @Test
    void theCollisionBoxCarriesTheAxisBand() {
        // The reference's updateShape: the X axis facings (W/E) the
        // z-thin box, the Z axis facings (S/N) the x-thin box — the DAMAGE
        // never moves the box, and the full 1.0 height carries through the
        // shape queries (a body rests on top).
        assertTrue(net.zaminmc.torch.server.block.CollisionShapes.contains(
                BuiltinBlocks.ANVIL_WEST, 0.5, 0.5, 0.5, CollisionShapes.ISOLATED));
        assertTrue(!net.zaminmc.torch.server.block.CollisionShapes.contains(
                BuiltinBlocks.ANVIL_WEST, 0.5, 0.5, 0.05, CollisionShapes.ISOLATED), "the west anvil is z-thin");
        assertTrue(net.zaminmc.torch.server.block.CollisionShapes.contains(
                BuiltinBlocks.ANVIL_DAMAGED_NORTH, 0.5, 0.5, 0.5, CollisionShapes.ISOLATED));
        assertTrue(!net.zaminmc.torch.server.block.CollisionShapes.contains(
                BuiltinBlocks.ANVIL_DAMAGED_NORTH, 0.05, 0.5, 0.5, CollisionShapes.ISOLATED),
                "the north anvil is x-thin");
        assertEquals(1.0, net.zaminmc.torch.server.block.CollisionShapes.top(
                BuiltinBlocks.ANVIL, 0.5, 0.5, 0.5, CollisionShapes.ISOLATED), 1.0E-9,
                "the anvil's top is the cell ceiling");
    }
}
