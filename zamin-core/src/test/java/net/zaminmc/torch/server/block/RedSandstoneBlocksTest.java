package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The red sandstone family (Slice 52 — 1.8's own four, 1:1): the red sand
 * variant, the three TYPE states, the four facing stairs, the slab pair +
 * the double — the reference's exact bands, the crafting five, and the
 * family tables.
 */
class RedSandstoneBlocksTest {

    @Test
    void theTypeStatesCarryTheReferenceBand() {
        assertEquals("minecraft:red_sandstone", RedSandstoneBlocks.stateId(0).toString());
        assertEquals("minecraft:chiseled_red_sandstone", RedSandstoneBlocks.stateId(1).toString());
        assertEquals("minecraft:smooth_red_sandstone", RedSandstoneBlocks.stateId(2).toString());
        for (int type = 0; type <= 2; type++) {
            EngineBlockType state = RedSandstoneBlocks.state(type);
            assertEquals(type, state.legacyMetadata());
            assertEquals(type, RedSandstoneBlocks.typeOf(state.identifier()));
            assertTrue(RedSandstoneBlocks.isRedSandstone(state.identifier()));
            // The behavior rows: the block's own 0.8 ROCK with the pickaxe
            // gate (RedSandstoneBlock's setStrength(0.8F), Material.STONE),
            // the family item drop with the TYPE damage
            // (getDropItemMetadata's own band).
            BlockBehavior behavior = BlockBehaviorTable.of(state.identifier()).orElseThrow();
            assertEquals(0.8, behavior.hardness());
            assertTrue(behavior.requiresTool());
            assertEquals(1, behavior.harvestLevel());
            assertEquals("rock", behavior.material());
            assertEquals(RedSandstoneBlocks.FAMILY_ITEM, behavior.drops().get(0).item());
            assertEquals(type, behavior.drops().get(0).damage());
            // The light rows: the full opaque cube.
            assertEquals(15, BlockLightTable.lookup(state.identifier()).orElseThrow().filter());
        }
        // The family item's damage consult (Type.byId's clamp to the
        // DEFAULT form on out-of-band damage).
        assertEquals("minecraft:chiseled_red_sandstone", RedSandstoneBlocks.placementBlockOf(
                Identifier.parse("minecraft:red_sandstone"), 1).orElseThrow().toString());
        assertEquals("minecraft:red_sandstone", RedSandstoneBlocks.placementBlockOf(
                Identifier.parse("minecraft:red_sandstone"), 99).orElseThrow().toString());
        assertTrue(RedSandstoneBlocks.placementBlockOf(
                Identifier.parse("minecraft:dye"), 1).isEmpty());
        // The wire ids.
        assertEquals(179, RedSandstoneBlocks.WIRE_ID);
        assertEquals(180, RedSandstoneBlocks.STAIRS_WIRE_ID);
        assertEquals(181, RedSandstoneBlocks.DOUBLE_SLAB_WIRE_ID);
        assertEquals(182, RedSandstoneBlocks.SLAB_WIRE_ID);
    }

    @Test
    void theRedSandRidesTheSandRow() {
        EngineBlockType redSand = RedSandstoneBlocks.redSand();
        assertEquals("minecraft:red_sand", redSand.identifier().toString());
        assertEquals(1, redSand.legacyMetadata());
        assertTrue(RedSandstoneBlocks.isRedSand(redSand.identifier()));
        // The behavior row: the sand's own 0.5 shovel row — no tool gate,
        // the drop the sand item itself at the RED_SAND variant's damage 1.
        BlockBehavior behavior = BlockBehaviorTable.of(redSand.identifier()).orElseThrow();
        assertEquals(0.5, behavior.hardness());
        assertTrue(!behavior.requiresTool());
        assertEquals(Identifier.parse("minecraft:sand"), behavior.drops().get(0).item());
        assertEquals(1, behavior.drops().get(0).damage());
        // The light row: the sand's own opaque cube.
        assertEquals(15, BlockLightTable.lookup(redSand.identifier()).orElseThrow().filter());
        // The placement consult: the sand item's damage 1 is the red sand;
        // the plain sand (damage 0) never consults here.
        assertEquals("minecraft:red_sand", RedSandstoneBlocks.placementBlockOf(
                Identifier.parse("minecraft:sand"), 1).orElseThrow().toString());
        assertTrue(RedSandstoneBlocks.placementBlockOf(
                Identifier.parse("minecraft:sand"), 0).isEmpty());
    }

    @Test
    void theStairsCarryTheBukkitBand() {
        String[] facings = {"east", "west", "south", "north"};
        for (int facing = 0; facing < 4; facing++) {
            EngineBlockType state = RedSandstoneBlocks.stairsState(facing);
            assertEquals(facing, state.legacyMetadata());
            assertTrue(RedSandstoneBlocks.isStairs(state.identifier()));
            // The behavior row: the SMOOTH base's own strength carried
            // through the reference's new StairsBlock(state).
            BlockBehavior behavior = BlockBehaviorTable.of(state.identifier()).orElseThrow();
            assertEquals(0.8, behavior.hardness());
            assertEquals("rock", behavior.material());
            assertEquals("minecraft:red_sandstone_stairs", behavior.drops().get(0).item().toString());
            // The light row: the opaque-partial band like the masonry
            // stairs.
            assertEquals(15, BlockLightTable.lookup(state.identifier()).orElseThrow().filter());
        }
    }

    @Test
    void theSlabTrioCarriesTheReferenceRows() {
        Identifier[] trio = {
                Identifier.parse("minecraft:stone_slab2"),
                Identifier.parse("minecraft:stone_slab2_top"),
                Identifier.parse("minecraft:double_stone_slab2")};
        for (Identifier slab : trio) {
            // The behavior rows: the SingleRedSandstoneSlabBlock's own
            // setStrength(2.0F) — every form's drop the single-slab item
            // (getDropItem's consult: the double and the top alike yield
            // the one slab, the variant id 0).
            BlockBehavior behavior = BlockBehaviorTable.of(slab).orElseThrow();
            assertEquals(2.0, behavior.hardness());
            assertTrue(behavior.requiresTool());
            assertEquals("minecraft:stone_slab2", behavior.drops().get(0).item().toString());
            assertEquals(0, behavior.drops().get(0).damage());
            assertEquals(15, BlockLightTable.lookup(slab).orElseThrow().filter());
        }
        assertEquals(8, new EngineBlockType(
                Identifier.parse("minecraft:stone_slab2_top"), "", 8).legacyMetadata());
        // The items exist (Item.java's own rows over 179/180/182).
        assertTrue(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:red_sandstone")).isPresent());
        assertTrue(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:red_sandstone_stairs")).isPresent());
        assertTrue(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:stone_slab2")).isPresent());
    }

    @Test
    void theStatsMergesFoldTheFamily() {
        // The mined-block stat consult (EngineStats.merged's own band): the
        // red sand counts on the sand row, the double slab on the single,
        // the TYPE states on the vanilla registry names direct.
        assertEquals("stat.mineBlock.minecraft.sand",
                net.zaminmc.torch.server.stat.EngineStats.mineBlockOf(
                        Identifier.parse("minecraft:red_sand")));
        assertEquals("stat.mineBlock.minecraft.stone_slab2",
                net.zaminmc.torch.server.stat.EngineStats.mineBlockOf(
                        Identifier.parse("minecraft:double_stone_slab2")));
        assertEquals("stat.mineBlock.minecraft.red_sandstone",
                net.zaminmc.torch.server.stat.EngineStats.mineBlockOf(
                        Identifier.parse("minecraft:smooth_red_sandstone")));
    }
}
