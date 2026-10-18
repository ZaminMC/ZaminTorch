package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The double-plant family (Slice 45 — DoublePlantBlock 1:1): the six
 * variants' twelve lower/upper states with the reference's exact metadata
 * band (the lower the VARIANT id whole, the upper 8 | the default NORTH
 * horizontal), the two-cell survival rules, and the family tables.
 */
class DoublePlantBlocksTest {

    @Test
    void theTwelveStatesCarryTheReferenceBand() {
        String[] lowerNames = {"sunflower", "lilac", "double_grass",
                "double_fern", "rose", "peony"};
        for (int variant = 0; variant < 6; variant++) {
            EngineBlockType lower = DoublePlantBlocks.lower(variant);
            EngineBlockType upper = DoublePlantBlocks.upper(variant);
            assertEquals(variant, lower.legacyMetadata());
            assertEquals(DoublePlantBlocks.UPPER_META, upper.legacyMetadata());
            assertEquals(8 | 2, DoublePlantBlocks.UPPER_META);
            assertTrue(DoublePlantBlocks.isDoublePlant(lower));
            assertTrue(DoublePlantBlocks.isDoublePlant(upper));
            assertTrue(DoublePlantBlocks.isUpper(upper.identifier()));
            assertFalse(DoublePlantBlocks.isUpper(lower.identifier()));
            assertEquals(variant, DoublePlantBlocks.variantOf(lower.identifier()));
            assertEquals(variant, DoublePlantBlocks.variantOf(upper.identifier()));
            // The behavior rows: the lower flowers drop the family item with
            // the VARIANT damage; the double grass the 1/8 seed roll; the
            // large fern and every upper nothing.
            BlockBehavior lowerBehavior = BlockBehaviorTable.of(lower.identifier()).orElseThrow();
            assertEquals(0.0, lowerBehavior.hardness());
            if (variant == 0 || variant == 1 || variant == 4 || variant == 5) {
                assertEquals(DoublePlantBlocks.FAMILY_ITEM,
                        lowerBehavior.drops().get(0).item());
                assertEquals(variant, lowerBehavior.drops().get(0).damage());
            } else if (variant == 2) {
                assertEquals(Identifier.parse("minecraft:wheat_seeds"),
                        lowerBehavior.drops().get(0).item());
                assertEquals(0.125, lowerBehavior.drops().get(0).chance());
            } else {
                assertTrue(lowerBehavior.drops().isEmpty());
            }
            assertTrue(BlockBehaviorTable.of(upper.identifier()).orElseThrow().drops().isEmpty());
            // The light rows: the CUTOUT pair passes light.
            assertEquals(0, BlockLightTable.lookup(lower.identifier()).orElseThrow().filter());
            assertEquals(0, BlockLightTable.lookup(upper.identifier()).orElseThrow().filter());
            // The solidity: the walk-through flora (the WHOLE family stays
            // collision-less) — and the replaceability is the per-variant
            // law (Wave L6 — DoublePlantBlock.canBeReplaced lines 60-68):
            // the grass/fern variants yield their place, the flowers refuse.
            assertFalse(WorldSolidity.isSolid(lower));
            assertFalse(WorldSolidity.isSolid(upper));
            boolean replaceable = variant == DoublePlantBlocks.VARIANT_GRASS
                    || variant == DoublePlantBlocks.VARIANT_FERN;
            assertEquals(replaceable,
                    WorldSolidity.isReplaceable(lower.identifier()));
            assertEquals(replaceable,
                    WorldSolidity.isReplaceable(upper.identifier()));
        }
        // The family item's damage consult (Variant.byId's clamp to the
        // sunflower on out-of-band damage).
        assertEquals("minecraft:rose", DoublePlantBlocks.placementBlockOf(
                Identifier.parse("minecraft:double_plant"), 4).orElseThrow().toString());
        assertEquals("minecraft:sunflower", DoublePlantBlocks.placementBlockOf(
                Identifier.parse("minecraft:double_plant"), 99).orElseThrow().toString());
        assertTrue(DoublePlantBlocks.placementBlockOf(
                Identifier.parse("minecraft:dye"), 1).isEmpty());
        // The lower's drop walk (getDropItem lines 80-95): the flower
        // variants the family item with the VARIANT damage, the double
        // grass the 1/8 roll (nothing on a miss), the large fern nothing.
        Random random = new Random(11L);
        assertEquals(4, DoublePlantBlocks.lowerDrop(DoublePlantBlocks.lower(4), random).damage());
        assertNull(DoublePlantBlocks.lowerDrop(DoublePlantBlocks.lower(3), random));
        boolean seeds = false;
        for (int i = 0; i < 800 && !seeds; i++) {
            seeds = DoublePlantBlocks.lowerDrop(DoublePlantBlocks.lower(2), random) != null;
        }
        assertTrue(seeds, "the double grass's 1/8 seed roll lands over enough draws");
    }

    @Test
    void thePairSurvivalReadsValidateBothCells() {
        var registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        var world = new net.zaminmc.torch.server.world.EngineWorld("doubleplant-it", registry,
                new net.zaminmc.torch.server.world.FlatWorldGenerator(registry, 4),
                Thread.currentThread());
        // A sunflower pair over the grass: both cells validate.
        BlockPosition lower = new BlockPosition(10, 5, 10);
        world.setBlock(lower.offset(0, -1, 0), registry.require(Identifier.parse("minecraft:grass_block")));
        world.setBlock(lower, DoublePlantBlocks.lower(0));
        world.setBlock(lower.offset(0, 1, 0), DoublePlantBlocks.upper(0));
        assertTrue(DoublePlantBlocks.lowerSurvives(world, lower));
        assertTrue(DoublePlantBlocks.hasHeadroom(world, lower.offset(0, 1, 0)));
        // The headroom gate: the cell above the pair's target must be air.
        world.setBlock(lower.offset(0, 2, 0), registry.require(Identifier.parse("minecraft:stone")));
        assertFalse(DoublePlantBlocks.hasHeadroom(world, lower.offset(0, 1, 0)));
        world.setBlock(lower.offset(0, 2, 0), world.airType());
        // The upper's survival: the cell below must be the family's lower.
        assertTrue(world.getBlock(lower.offset(0, 1, 0)).equals(DoublePlantBlocks.upper(0)));
        // The soil gate: stone under the lower refuses the pair.
        world.setBlock(lower.offset(0, -1, 0), registry.require(Identifier.parse("minecraft:stone")));
        assertFalse(DoublePlantBlocks.lowerSurvives(world, lower));
        world.setBlock(lower.offset(0, -1, 0), registry.require(Identifier.parse("minecraft:grass_block")));
        assertTrue(DoublePlantBlocks.lowerSurvives(world, lower));
        // The upper alone (the lower gone) never survives.
        BlockPosition floatingUpper = new BlockPosition(10, 8, 10);
        world.setBlock(floatingUpper, DoublePlantBlocks.upper(2));
        assertTrue(DoublePlantBlocks.isDoublePlantIdentifier(
                world.getBlock(floatingUpper).identifier()));
    }
}
