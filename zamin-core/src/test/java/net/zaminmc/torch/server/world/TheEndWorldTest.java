package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The End generator's contracts (Slice 61, ported from reference/1.8.8
 * world/gen/chunk/TheEndChunkGenerator.java + EndIslandDecorator +
 * EndPillarFeature): the island exists near the origin (end stone at the
 * center, void beyond the ~100-block envelope), the biome is THE_END
 * (wire id 9) everywhere, the whole chunk rebuilds byte-identically from
 * the same seed, and the obsidian-pillar decorate can land (the bedrock
 * cap family) without ever writing outside its own chunk.
 */
class TheEndWorldTest {

    private static BlockRegistry registry() {
        // The boot shape (the nether test's own chain — the palette the
        // generator's require() calls read).
        return net.zaminmc.torch.server.brewing.BrewingStandBlocks.registerAll(
                net.zaminmc.torch.server.dispenser.DispenserSystem.registerBlocks(
                net.zaminmc.torch.server.hopper.HopperSystem.registerBlocks(
                net.zaminmc.torch.server.piston.PistonSystem.registerBlocks(
                net.zaminmc.torch.server.redstone.TripwireBlocks.registerAll(
                net.zaminmc.torch.server.redstone.RedstoneBlocks.registerAll(
                        FluidBlocks.registerAll(
                                BuiltinBlocks.registerAll(new net.zaminmc.torch.server.block.BlockRegistryBuilder()))))))))
                .freeze();
    }

    private static BlockType endStone(BlockRegistry registry) {
        return registry.require(Identifier.parse("minecraft:end_stone"));
    }

    @Test
    void constantsPinTheReferenceNumbers() {
        // The End's 128-tall world (the 33-slice heightmap at quarter
        // resolution) and the fixed THE_END biome (wire id 9).
        assertEquals(128, TheEndWorldGenerator.WORLD_HEIGHT);
        assertEquals(9, TheEndWorldGenerator.THE_END_BIOME_ID);
    }

    @Test
    void theCentralIslandExistsAtTheOrigin() {
        // The envelope's center: the island's own 100-point band — the
        // origin column carries end stone somewhere in its band.
        BlockRegistry registry = registry();
        TheEndWorldGenerator generator = new TheEndWorldGenerator(registry, 12345L);
        EngineChunk origin = new EngineChunk(new ChunkPosition(0, 0),
                registry.require(Identifier.parse("minecraft:air")));
        generator.generate(origin);
        boolean found = false;
        for (int y = 0; y < TheEndWorldGenerator.WORLD_HEIGHT; y++) {
            if (origin.getBlock(8, y, 8).equals(endStone(registry))) {
                found = true;
                break;
            }
        }
        assertTrue(found, "the origin column must carry end stone (the central island)");
    }

    @Test
    void theVoidBeyondTheEnvelope() {
        // At chunk (20, 20) — block 320,320, sqrt(204800) ~ 452 — the
        // envelope's 100 - 452*8 is far below zero: every cell is air.
        BlockRegistry registry = registry();
        TheEndWorldGenerator generator = new TheEndWorldGenerator(registry, 12345L);
        EngineChunk far = new EngineChunk(new ChunkPosition(20, 20),
                endStone(registry)); // seeded SOLID: air only if the generator writes it
        generator.generate(far);
        for (int y = 0; y < TheEndWorldGenerator.WORLD_HEIGHT; y++) {
            assertFalse(far.getBlock(8, y, 8).equals(endStone(registry)),
                    "the far column must be void (y=" + y + ")");
        }
    }

    @Test
    void everyBiomeCellIsTheEnd() {
        BlockRegistry registry = registry();
        TheEndWorldGenerator generator = new TheEndWorldGenerator(registry, 987L);
        EngineChunk chunk = new EngineChunk(new ChunkPosition(3, -2),
                registry.require(Identifier.parse("minecraft:air")));
        generator.generate(chunk);
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                assertEquals(9, chunk.biome(lx, lz),
                        "the End's fixed biome (cell " + lx + "," + lz + ")");
            }
        }
    }

    @Test
    void theChunkRebuildsIdentically() {
        // The rebuild-identical contract: two fresh generations of the
        // same chunk produce byte-identical grids.
        BlockRegistry registry = registry();
        TheEndWorldGenerator generator = new TheEndWorldGenerator(registry, 4242L);
        EngineChunk first = new EngineChunk(new ChunkPosition(-3, 5),
                registry.require(Identifier.parse("minecraft:air")));
        EngineChunk second = new EngineChunk(new ChunkPosition(-3, 5),
                registry.require(Identifier.parse("minecraft:air")));
        generator.generate(first);
        generator.generate(second);
        for (int lx = 0; lx < 16; lx++) {
            for (int y = 0; y < TheEndWorldGenerator.WORLD_HEIGHT; y++) {
                for (int lz = 0; lz < 16; lz++) {
                    assertEquals(first.getBlock(lx, y, lz), second.getBlock(lx, y, lz),
                            "identical rebuild at " + lx + "," + y + "," + lz);
                }
            }
        }
    }

    @Test
    void thePillarDecorateLandsSomewhereDeterministic() {
        // Over a deterministic seed band, some chunk near the island
        // carries a pillar (the obsidian cylinder + the bedrock cap): the
        // 1-in-5 roll fires and the family writes stay in-chunk. The
        // dragon/crystal arms ride their own slices.
        BlockRegistry registry = registry();
        TheEndWorldGenerator generator = new TheEndWorldGenerator(registry, 77L);
        BlockType obsidian = registry.require(Identifier.parse("minecraft:obsidian"));
        BlockType bedrock = registry.require(Identifier.parse("minecraft:bedrock"));
        boolean sawPillar = false;
        for (int cx = -4; cx <= 4 && !sawPillar; cx++) {
            for (int cz = -4; cz <= 4 && !sawPillar; cz++) {
                EngineChunk chunk = new EngineChunk(new ChunkPosition(cx, cz),
                        registry.require(Identifier.parse("minecraft:air")));
                generator.generate(chunk);
                boolean hasObsidian = false;
                for (int lx = 0; lx < 16 && !hasObsidian; lx++) {
                    for (int y = 0; y < TheEndWorldGenerator.WORLD_HEIGHT && !hasObsidian; y++) {
                        for (int lz = 0; lz < 16 && !hasObsidian; lz++) {
                            if (chunk.getBlock(lx, y, lz).equals(obsidian)) {
                                hasObsidian = true;
                            }
                        }
                    }
                }
                if (hasObsidian) {
                    sawPillar = true;
                    // The bedrock cap: somewhere above the obsidian band in
                    // the same chunk, the pillar's own cap cell.
                    boolean hasBedrock = false;
                    for (int lx = 0; lx < 16 && !hasBedrock; lx++) {
                        for (int y = 0; y < TheEndWorldGenerator.WORLD_HEIGHT && !hasBedrock; y++) {
                            for (int lz = 0; lz < 16 && !hasBedrock; lz++) {
                                if (chunk.getBlock(lx, y, lz).equals(bedrock)) {
                                    hasBedrock = true;
                                }
                            }
                        }
                    }
                    assertTrue(hasBedrock, "a pillar carries its bedrock cap");
                }
            }
        }
        assertTrue(sawPillar, "the 1-in-5 pillar roll must fire somewhere in the 9x9 ring");
    }
}
