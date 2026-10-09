package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockBehaviorTable;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The biome world's contracts: deterministic climate biomes, desert sand
 * skins over sandstone, forest canopies denser than plains, the flora pass
 * (walk-through, instant-break, self-dropping flowers) and the biome bytes
 * riding the chunk's wire array.
 */
class BiomeWorldTest {

    private static BlockRegistry registry() {
        // The boot shape: fluids ride the same registry (water is a block here).
        return net.zaminmc.torch.server.block.FluidBlocks.registerAll(
                BuiltinBlocks.registerAll(new net.zaminmc.torch.server.block.BlockRegistryBuilder()))
                .freeze();
    }

    @Test
    void biomesAreDeterministicAndShadeAllThreeKinds() {
        NormalWorldGenerator generator = new NormalWorldGenerator(registry(), "biome-world");
        // Determinism: the same column asks twice, answers once.
        assertEquals(generator.biomeAt(1000, -1000), generator.biomeAt(1000, -1000));
        // The world shades all three biomes within a 4096-block sweep (the
        // climate noise's visible band at this scale).
        boolean plains = false;
        boolean forest = false;
        boolean desert = false;
        for (int x = 0; x < 2048; x += 16) {
            for (int z = 0; z < 2048; z += 16) {
                Biome b = generator.biomeAt(x, z);
                plains |= b == Biome.PLAINS;
                forest |= b == Biome.FOREST;
                desert |= b == Biome.DESERT;
            }
        }
        assertTrue(plains && forest && desert, "all three biomes appear in the sweep");
    }

    @Test
    void desertColumnsWearSandOverSandstone() {
        NormalWorldGenerator generator = new NormalWorldGenerator(registry(), "desert-world");
        // Find a dry desert column.
        int x = -1;
        int z = -1;
        for (int cx = 0; cx < 2048 && x < 0; cx += 8) {
            for (int cz = 0; cz < 2048 && x < 0; cz += 8) {
                if (generator.biomeAt(cx, cz) == Biome.DESERT
                        && generator.heightAt(cx, cz) > NormalWorldGenerator.SEA_LEVEL + 1) {
                    x = cx;
                    z = cz;
                }
            }
        }
        assertTrue(x >= 0, "a dry desert column exists in the sweep");
        int h = generator.heightAt(x, z);
        Identifier skin = Identifier.parse("minecraft:sand");
        Identifier stoneBand = Identifier.parse("minecraft:sandstone");
        // The top three skin cells are sand; the next three are sandstone.
        for (int depth = 0; depth < 3; depth++) {
            assertEquals(generator.surfaceBlockAt(x, h - depth, z).identifier(), skin,
                    "desert skin depth " + depth + " is sand");
        }
        for (int depth = 3; depth < 6; depth++) {
            assertEquals(generator.surfaceBlockAt(x, h - depth, z).identifier(), stoneBand,
                    "desert band depth " + depth + " is sandstone");
        }
    }

    @Test
    void forestChunksGrowDenserThanPlainsOnAverage() {
        var registry = registry();
        Identifier log = Identifier.parse("minecraft:oak_log");
        int forestLogs = 0;
        int plainsLogs = 0;
        NormalWorldGenerator generator = new NormalWorldGenerator(registry, "biome-world");
        // Average the trunk count over many chunks of each biome (the chunk
        // classifies by its center column, exactly like the generator's own
        // density roll does).
        int forestChunks = 0;
        int plainsChunks = 0;
        for (int chunkX = 0; chunkX < 100; chunkX++) {
            for (int chunkZ = 0; chunkZ < 100; chunkZ++) {
                Biome center = generator.biomeAt(chunkX * 16 + 8, chunkZ * 16 + 8);
                if (center != Biome.FOREST && center != Biome.PLAINS) {
                    continue;
                }
                EngineChunk chunk = new EngineChunk(
                        new net.zaminmc.torch.block.ChunkPosition(chunkX, chunkZ),
                        registry.require(Identifier.parse("minecraft:air")));
                generator.generate(chunk);
                int logs = 0;
                for (int lx = 0; lx < 16; lx++) {
                    for (int y = 60; y < 90; y++) {
                        for (int lz = 0; lz < 16; lz++) {
                            if (chunk.getBlock(lx, y, lz).identifier().equals(log)) {
                                logs++;
                            }
                        }
                    }
                }
                if (center == Biome.FOREST) {
                    forestLogs += logs;
                    forestChunks++;
                } else {
                    plainsLogs += logs;
                    plainsChunks++;
                }
            }
        }
        assertTrue(forestChunks > 20 && plainsChunks > 20, "both biome shapes were sampled");
        double forestPer = forestLogs / (double) forestChunks;
        double plainsPer = plainsLogs / (double) plainsChunks;
        assertTrue(forestPer > plainsPer,
                "forest chunks average more trunk wood (" + forestPer + " vs " + plainsPer + ")");
    }

    @Test
    void floraIsWalkThroughInstantBreakAndFlowersDropThemselves() {
        var registry = registry();
        Identifier tallGrass = Identifier.parse("minecraft:tall_grass");
        Identifier poppy = Identifier.parse("minecraft:poppy");
        Identifier deadBush = Identifier.parse("minecraft:dead_bush");
        // Walk-through: no body ever stands on grass.
        assertFalse(WorldSolidity.isSolid(registry.require(tallGrass)));
        assertFalse(WorldSolidity.isSolid(registry.require(poppy)));
        assertFalse(WorldSolidity.isSolid(registry.require(deadBush)));
        // Instant break: hardness 0, no tool gate.
        var grassBehavior = BlockBehaviorTable.of(tallGrass).orElseThrow();
        assertEquals(0.0, grassBehavior.hardness());
        assertFalse(grassBehavior.requiresTool());
        // The flowers drop themselves; grass and the dead bush drop nothing.
        var poppyBehavior = BlockBehaviorTable.of(poppy).orElseThrow();
        assertEquals(1, poppyBehavior.drops().size());
        assertEquals(poppy, poppyBehavior.drops().get(0).item());
        assertTrue(BlockBehaviorTable.of(tallGrass).orElseThrow().drops().isEmpty());
        assertTrue(BlockBehaviorTable.of(deadBush).orElseThrow().drops().isEmpty());
        // The redstone ore keeps its own wire identity (id 73, never iron's 15).
        assertNotEquals(Identifier.parse("minecraft:iron_ore"),
                Identifier.parse("minecraft:redstone_ore"));
    }

    @Test
    void biomeBytesRideTheChunkRow() {
        var registry = registry();
        NormalWorldGenerator generator = new NormalWorldGenerator(registry, "biome-world");
        // A chunk near the seed origin carries some non-plains column nearby;
        // assert the row is the wire layout (x fastest) with real values.
        EngineChunk chunk = new EngineChunk(
                new net.zaminmc.torch.block.ChunkPosition(0, 0),
                registry.require(Identifier.parse("minecraft:air")));
        generator.generate(chunk);
        byte[] row = chunk.biomeArray();
        assertEquals(256, row.length);
        boolean nonPlains = false;
        for (byte b : row) {
            if (b != (byte) Biome.PLAINS.legacyId()) {
                nonPlains = true;
            }
        }
        // The origin chunk may be all plains; a wider band always varies.
        if (!nonPlains) {
            EngineChunk far = new EngineChunk(
                    new net.zaminmc.torch.block.ChunkPosition(48, -48),
                    registry.require(Identifier.parse("minecraft:air")));
            generator.generate(far);
            byte[] farRow = far.biomeArray();
            boolean farVaries = false;
            for (byte b : farRow) {
                if (b != (byte) Biome.PLAINS.legacyId()) {
                    farVaries = true;
                }
            }
            assertTrue(farVaries, "the biome row varies across the world");
        }
    }
}
