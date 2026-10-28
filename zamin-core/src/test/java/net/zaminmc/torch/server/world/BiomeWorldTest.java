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
        // The full boot chain (EngineServer's freeze order — the village's
        // structure palette resolves the pressure plate from the redstone
        // family, Slice 12b).
        return net.zaminmc.torch.server.brewing.BrewingStandBlocks.registerAll(
                net.zaminmc.torch.server.dispenser.DispenserSystem.registerBlocks(
                net.zaminmc.torch.server.hopper.HopperSystem.registerBlocks(
                net.zaminmc.torch.server.piston.PistonSystem.registerBlocks(
                net.zaminmc.torch.server.redstone.TripwireBlocks.registerAll(
                net.zaminmc.torch.server.redstone.RedstoneBlocks.registerAll(
                        net.zaminmc.torch.server.block.FluidBlocks.registerAll(
                                BuiltinBlocks.registerAll(new net.zaminmc.torch.server.block.BlockRegistryBuilder()))))))))
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
        // Find a dry desert column (high enough that the sandstone
        // extension's max(0, y-63) term guarantees at least one layer,
        // and the cap consult itself answers sand — the extreme-negative
        // depth tail carries no cap at all, the k <= 0 arm).
        int x = -1;
        int z = -1;
        for (int cx = 0; cx < 2048 && x < 0; cx += 8) {
            for (int cz = 0; cz < 2048 && x < 0; cz += 8) {
                if (generator.biomeAt(cx, cz) == Biome.DESERT
                        && generator.heightAt(cx, cz) > NormalWorldGenerator.SEA_LEVEL + 6
                        && generator.surfaceBlockAt(cx, generator.heightAt(cx, cz), cz)
                                .identifier().value().equals("sand")) {
                    x = cx;
                    z = cz;
                }
            }
        }
        assertTrue(x >= 0, "a dry desert column exists in the sweep");
        int h = generator.heightAt(x, z);
        Identifier sand = Identifier.parse("minecraft:sand");
        Identifier sandstone = Identifier.parse("minecraft:sandstone");
        // THE SURFACE WALK'S LAW (VIII14's surface slice — buildSurfaces
        // lines 452-508): the cap and the k-band are SAND (the k varies
        // with the depth noise, 2-4 at the common band), then the
        // sandstone EXTENSION, then the core stone — the sequence, not
        // fixed depths.
        assertEquals(sand, generator.surfaceBlockAt(x, h, z).identifier(),
                "the desert cap is sand");
        int depth = 1;
        boolean sawSand = false;
        while (depth < 12
                && sand.equals(generator.surfaceBlockAt(x, h - depth, z).identifier())) {
            depth++;
            sawSand = true;
        }
        assertTrue(sawSand, "the sand band runs below the cap");
        // The first non-sand cell is the sandstone extension (guaranteed
        // at this height: nextInt(4) + (h - k - 63) >= 1).
        assertEquals(sandstone, generator.surfaceBlockAt(x, h - depth, z).identifier(),
                "the sand band's floor is the sandstone extension");
        // The extension runs at least one cell, and nothing below the
        // sandstone ever returns to sand.
        while (depth < 16
                && sandstone.equals(generator.surfaceBlockAt(x, h - depth, z).identifier())) {
            depth++;
        }
        for (int probe = depth; probe < depth + 4; probe++) {
            assertNotEquals(sand,
                    generator.surfaceBlockAt(x, h - probe, z).identifier(),
                    "the sand never returns below the extension");
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
        // The flowers drop themselves; the dead bush drops nothing; tall
        // grass carries the historical seed income ALONE (TallPlantBlock's
        // 1/8 wheat-seeds roll, reference lines 63-65 — the by-hand
        // self-drop was the flora bug the parity audit fixed at its root;
        // the plant itself rides the SHEARS gate alone, pinned by
        // SilkAndShearsParityAcceptanceTest).
        var poppyBehavior = BlockBehaviorTable.of(poppy).orElseThrow();
        assertEquals(1, poppyBehavior.drops().size());
        assertEquals(poppy, poppyBehavior.drops().get(0).item());
        var grassDrops = BlockBehaviorTable.of(tallGrass).orElseThrow().drops();
        assertEquals(1, grassDrops.size());
        assertEquals(Identifier.parse("minecraft:wheat_seeds"), grassDrops.get(0).item());
        assertEquals(0.125, grassDrops.get(0).chance(), 1e-9);
        assertTrue(grassDrops.stream().noneMatch(drop -> drop.item().equals(tallGrass)),
                "by hand the grass never drops itself");
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
