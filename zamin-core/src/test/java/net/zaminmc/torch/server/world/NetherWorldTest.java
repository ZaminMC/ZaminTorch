package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.world.noise.PerlinNoise;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The nether generator's contracts (the 8b-i slice, ported from
 * reference/1.8.8 world/gen/chunk/NetherChunkGenerator.java + carver/
 * NetherCaveCarver.java + noise/PerlinNoise.java + noise/ImprovedNoise.java):
 * the seven-PerlinNoise stack is reproducible per seed, the trilinear terrain
 * fills the 0..127 band with netherrack over a lava sea, the bedrock bands
 * ride both walls, the decorate pass lands glowstone/quartz/fire/lava
 * pockets/the mushrooms, every biome cell is HELL (wire id 8), and the whole
 * chunk rebuilds byte-identically from the same seed.
 */
class NetherWorldTest {

    private static BlockRegistry registry() {
        // The boot shape: fluids ride the same registry (lava is a block here).
        return FluidBlocks.registerAll(
                BuiltinBlocks.registerAll(new net.zaminmc.torch.server.block.BlockRegistryBuilder()))
                .freeze();
    }

    private static NetherWorldGenerator generator(BlockRegistry registry, long seed) {
        return new NetherWorldGenerator(registry, seed);
    }

    @Test
    void constantsPinTheReferenceNumbers() {
        // The constructor's setSeaLevel(63), the lava fill j = 63/2 + 1, the
        // nether's 128-tall world.
        assertEquals(63, NetherWorldGenerator.SEA_LEVEL);
        assertEquals(32, NetherWorldGenerator.LAVA_LEVEL);
        assertEquals(128, NetherWorldGenerator.WORLD_HEIGHT);
    }

    @Test
    void noiseStackIsReproducibleAndSeedSeparated() {
        // The octave stack: same seed, same region, byte-identical doubles;
        // a different seed walks differently.
        double[] a = new PerlinNoise(new Random(4711L), 16).getRegion(null, 0, 0, 0, 5, 17, 5, 684.412, 2053.236, 684.412);
        double[] b = new PerlinNoise(new Random(4711L), 16).getRegion(null, 0, 0, 0, 5, 17, 5, 684.412, 2053.236, 684.412);
        double[] c = new PerlinNoise(new Random(4712L), 16).getRegion(null, 0, 0, 0, 5, 17, 5, 684.412, 2053.236, 684.412);
        assertEquals(a.length, 5 * 17 * 5, "the region is the full 5x17x5 lattice");
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i], b[i], "reproducible at " + i);
        }
        boolean differs = false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != c[i]) {
                differs = true;
                break;
            }
        }
        assertTrue(differs, "a different seed walks a different lattice");
    }

    @Test
    void terrainRebuildsByteIdentically() {
        BlockRegistry registry = registry();
        NetherWorldGenerator g1 = generator(registry, 0x4E4554484552L);
        NetherWorldGenerator g2 = generator(registry, 0x4E4554484552L);
        EngineChunk c1 = fill(g1, 3, -2);
        EngineChunk c2 = fill(g2, 3, -2);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 128; y++) {
                    assertEquals(c1.getBlock(x, y, z), c2.getBlock(x, y, z),
                            "identical rebuild at " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void terrainShapeHoldsTheReferenceBands() {
        BlockRegistry registry = registry();
        NetherWorldGenerator gen = generator(registry, 0x4E4554484552L);
        BlockType netherrack = registry.require(Identifier.parse("minecraft:netherrack"));
        BlockType bedrock = registry.require(Identifier.parse("minecraft:bedrock"));
        BlockType lava = registry.require(Identifier.parse("minecraft:lava"));

        long netherrackCells = 0;
        long lavaSeaCells = 0;
        boolean topBedrock = false;
        boolean bottomBedrock = false;
        boolean ceilingHolds = true;

        // A 4x4 chunk patch wide enough for the feature rolls to land.
        Map<String, Long> counts = new HashMap<>();
        for (int cx = 0; cx < 4; cx++) {
            for (int cz = 0; cz < 4; cz++) {
                EngineChunk chunk = fill(gen, cx, cz);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 128; y++) {
                            String id = chunk.getBlock(x, y, z).identifier().value();
                            counts.merge(id, 1L, Long::sum);
                            if (id.equals("netherrack")) {
                                netherrackCells++;
                            }
                            if (id.equals("lava") && y < NetherWorldGenerator.LAVA_LEVEL) {
                                lavaSeaCells++;
                            }
                            if (id.equals("bedrock")) {
                                if (y > 120) {
                                    topBedrock = true;
                                }
                                if (y < 6) {
                                    bottomBedrock = true;
                                }
                            }
                        }
                        // Nothing lives above the nether's 128.
                        for (int y = 128; y < 256; y++) {
                            if (!chunk.getBlock(x, y, z).equals(registry.require(Identifier.parse("minecraft:air")))) {
                                ceilingHolds = false;
                            }
                        }
                    }
                }
            }
        }

        assertTrue(netherrackCells > 4L * 16 * 16 * 40, "netherrack dominates the shape (" + netherrackCells + ")");
        assertTrue(lavaSeaCells > 100, "the lava sea fills below 32 (" + lavaSeaCells + ")");
        assertTrue(topBedrock, "the top wall carries its bedrock band");
        assertTrue(bottomBedrock, "the floor carries its bedrock band");
        assertTrue(ceilingHolds, "the nether stays within its 128-tall band");
        assertTrue(counts.containsKey("glowstone"), "glowstone clusters land");
        assertTrue(counts.containsKey("quartz_ore"), "quartz veins land");
        assertTrue(counts.containsKey("fire"), "fire patches land");
        assertTrue(counts.containsKey("soul_sand"), "the soul sand skin lands");
        // The veins are veins: quartz stays under 5% of the netherrack body
        // (a size-14 vein per 16 rolls replaces a lot, never the world).
        assertTrue(counts.getOrDefault("quartz_ore", 0L) * 20 < counts.getOrDefault("netherrack", 0L),
                "quartz veins are veins, not floods");
    }

    @Test
    void everyBiomeCellIsHell() {
        BlockRegistry registry = registry();
        NetherWorldGenerator gen = generator(registry, 99L);
        EngineChunk chunk = fill(gen, -5, 7);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertEquals(8, chunk.biome(x, z), "the FixedBiomeSource(HELL) fills every cell");
            }
        }
    }

    @Test
    void cavesPunchAirPocketsBelowTheCeiling() {
        BlockRegistry registry = registry();
        NetherWorldGenerator gen = generator(registry, 0x43415645L);
        BlockType air = registry.require(Identifier.parse("minecraft:air"));
        long airBelow = 0;
        for (int cx = 0; cx < 6; cx++) {
            EngineChunk chunk = fill(gen, cx, 1);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 36; y < 100; y++) {
                        if (chunk.getBlock(x, y, z).equals(air)) {
                            airBelow++;
                        }
                    }
                }
            }
        }
        // The carve pass (the reference's range-8 neighbor loop) opens caves
        // through the middle band; a fully solid band would mean the carver
        // never fires.
        assertTrue(airBelow > 500, "the carver opens the middle band (" + airBelow + " air cells)");
    }

    @Test
    void spawnScanFindsAStandableCellAboveTheSea() {
        BlockRegistry registry = registry();
        NetherWorldGenerator gen = generator(registry, 0x4E4554484552L);
        int ground = gen.groundLevel();
        assertTrue(ground >= NetherWorldGenerator.LAVA_LEVEL,
                "the spawn anchor sits above the lava sea (got " + ground + ")");
        assertTrue(ground < NetherWorldGenerator.WORLD_HEIGHT - 1, "the anchor is inside the world");
        // Asking twice answers once (the memoized scan).
        assertEquals(ground, gen.groundLevel());
    }

    @Test
    void newBlocksAndItemsResolveInTheRegistries() {
        BlockRegistry registry = registry();
        for (String id : new String[]{"minecraft:netherrack", "minecraft:soul_sand",
                "minecraft:glowstone", "minecraft:quartz_ore",
                "minecraft:brown_mushroom", "minecraft:red_mushroom"}) {
            assertTrue(registry.lookup(Identifier.parse(id)).isPresent(), id + " resolves");
        }
        for (net.zaminmc.torch.item.ItemType item : new net.zaminmc.torch.item.ItemType[]{
                net.zaminmc.torch.server.item.BuiltinItems.BROWN_MUSHROOM,
                net.zaminmc.torch.server.item.BuiltinItems.RED_MUSHROOM,
                net.zaminmc.torch.server.item.BuiltinItems.GLOWSTONE_DUST,
                net.zaminmc.torch.server.item.BuiltinItems.QUARTZ,
                net.zaminmc.torch.server.item.BuiltinItems.NETHERRACK,
                net.zaminmc.torch.server.item.BuiltinItems.SOUL_SAND}) {
            assertTrue(net.zaminmc.torch.server.item.BuiltinItems.lookup(item.identifier()).isPresent(),
                    item.identifier() + " resolves");
        }
    }

    private static EngineChunk fill(WorldGenerator generator, int chunkX, int chunkZ) {
        BlockRegistry registry = registry();
        EngineChunk chunk = new EngineChunk(new ChunkPosition(chunkX, chunkZ),
                registry.require(Identifier.parse("minecraft:air")));
        generator.generate(chunk);
        return chunk;
    }
}
