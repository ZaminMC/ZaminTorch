package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flower family (Slice 44 — FlowerBlock 1:1): the red flower's nine
 * TYPE states with the reference's exact metadata band, the flora support
 * rules (the flowers and the tall grass on the grass/dirt/farmland band,
 * the dead bush on the sand/clay band), and the family tables.
 */
class FlowerBlocksTest {

    @Test
    void theNineTypesCarryTheReferenceBand() {
        String[] referenceNames = {
                "poppy", "blue_orchid", "allium", "houstonia",
                "red_tulip", "orange_tulip", "white_tulip", "pink_tulip",
                "oxeye_daisy"};
        for (int type = 0; type < 9; type++) {
            EngineBlockType state = FlowerBlocks.state(type);
            assertEquals(referenceNames[type], state.identifier().value());
            assertEquals(type, state.legacyMetadata());
            assertEquals(type, FlowerBlocks.typeOf(state));
            assertTrue(FlowerBlocks.isFlower(state));
            // The behavior rows: the 0.0 plant, the family-item drop with
            // the TYPE damage (the eight variants; the poppy keeps its own
            // shipped self-drop).
            if (type > 0) {
                java.util.Optional<BlockBehavior> behavior =
                        BlockBehaviorTable.of(state.identifier());
                assertTrue(behavior.isPresent());
                assertEquals(0.0, behavior.orElseThrow().hardness());
                assertEquals(FlowerBlocks.FAMILY_ITEM,
                        behavior.orElseThrow().drops().get(0).item());
                assertEquals(type, behavior.orElseThrow().drops().get(0).damage());
                // The light rows: the CUTOUT flora passes light.
                assertEquals(0, BlockLightTable.lookup(state.identifier())
                        .orElseThrow().filter());
                // The solidity: the walk-through flora.
                assertTrue(WorldSolidity.isReplaceable(state.identifier()));
                assertFalse(WorldSolidity.isSolid(state));
            }
        }
        // The family item's placement consult: the damage domain resolves
        // every variant, the poppy's damage 0 the plain poppy.
        assertEquals("minecraft:blue_orchid", FlowerBlocks.placementBlockOf(
                Identifier.parse("minecraft:red_flower"), 1).orElseThrow().toString());
        assertEquals("minecraft:oxeye_daisy", FlowerBlocks.placementBlockOf(
                Identifier.parse("minecraft:red_flower"), 8).orElseThrow().toString());
        assertEquals("minecraft:poppy", FlowerBlocks.placementBlockOf(
                Identifier.parse("minecraft:red_flower"), 0).orElseThrow().toString());
        // The out-of-band damage clamps to the domain (FlowerBlock.Type.byId
        // lines 120-127: the id clamps to 0).
        assertEquals("minecraft:poppy", FlowerBlocks.placementBlockOf(
                Identifier.parse("minecraft:red_flower"), 99).orElseThrow().toString());
        assertTrue(FlowerBlocks.placementBlockOf(
                Identifier.parse("minecraft:dye"), 1).isEmpty());
    }

    @Test
    void theFloraSupportRulesPopOnTheWrongSoil() {
        var registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        var world = new net.zaminmc.torch.server.world.EngineWorld("flowers-it", registry,
                new net.zaminmc.torch.server.world.FlatWorldGenerator(registry, 4),
                Thread.currentThread());
        // The soils are set by hand (the live-engine convention): the
        // grass, the dirt and the farmland carry (the grass/dirt/farmland
        // band); the stone never does.
        BlockPosition onGrass = new BlockPosition(8, 5, 8);
        world.setBlock(onGrass.offset(0, -1, 0), registry.require(Identifier.parse("minecraft:grass_block")));
        assertTrue(FlowerBlocks.isSupported(world, onGrass));
        world.setBlock(onGrass.offset(0, -1, 0), registry.require(Identifier.parse("minecraft:dirt")));
        assertTrue(FlowerBlocks.isSupported(world, onGrass));
        world.setBlock(onGrass.offset(0, -1, 0), registry.require(Identifier.parse("minecraft:farmland_wet")));
        assertTrue(FlowerBlocks.isSupported(world, onGrass));
        world.setBlock(onGrass.offset(0, -1, 0), registry.require(Identifier.parse("minecraft:stone")));
        assertFalse(FlowerBlocks.isSupported(world, onGrass));
        world.setBlock(onGrass.offset(0, -1, 0), registry.require(Identifier.parse("minecraft:sand")));
        assertFalse(FlowerBlocks.isSupported(world, onGrass));
        // The family states register and the support consult reads them.
        assertTrue(registry.lookup(Identifier.parse("minecraft:blue_orchid")).isPresent());
        assertTrue(registry.lookup(Identifier.parse("minecraft:oxeye_daisy")).isPresent());
        assertTrue(FlowerBlocks.isFlowerIdentifier(Identifier.parse("minecraft:poppy")));
        assertFalse(FlowerBlocks.isFlowerIdentifier(Identifier.parse("minecraft:stone")));
    }
}
