package net.zaminmc.torch.server.world.feature;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.TreeFeatures;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.world.Biome;
import net.zaminmc.torch.server.world.EngineChunk;
import net.zaminmc.torch.server.world.NormalWorldGenerator;
import net.zaminmc.torch.server.world.noise.PerlinSimplexNoise;
import net.zaminmc.torch.server.world.structure.StructureWorld;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The feature decorator's contracts (the VIII14 decorators slice): the
 * per-biome counts table against the reference's own ctor outcomes, the
 * determinism per seed, the flower-override rows (the plains' tulip band,
 * the swamp's orchid), the lake/dungeon/well/spike shapes and the
 * cactus/reed placement laws over a scripted world, and the wild
 * placements (the forest's birch share, the mushroom isle's huge
 * mushrooms, the tree shapes through the populate seam).
 */
class BiomeDecoratorTest {

    /** The full boot chain (the village palette's own freeze order). */
    private static BlockRegistry registry() {
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

    private static BlockType block(BlockRegistry registry, String value) {
        return registry.require(Identifier.parse("minecraft:" + value));
    }

    // -------------------------------------------------------- the counts table

    /**
     * The per-biome counts (the reference's subclass ctor outcomes —
     * ForestBiome lines 33-52, PlainsBiome 19-23, DesertBiome 12-21,
     * SwampBiome 11-25, JungleBiome 31-45, TaigaBiome 39-49,
     * MushroomBiome 11-17, MesaBiome 31-47, SavannaBiome 19-23,
     * ExtremeHillsBiome 23-29): the demanded seven plus the special
     * forms.
     */
    @Test
    void thePerBiomeCountsMatchTheReferenceCtors() {
        // The class defaults (FeatureDecorator lines 53-64).
        assertEquals(DecoratorProfile.defaults(),
                DecoratorProfile.of(0, 2, 1, 0, 0, 0, 0, 1, 3, 1, 0, 0,
                        DecoratorProfile.TreePick.DEFAULT,
                        DecoratorProfile.PlantPick.DEFAULT,
                        DecoratorProfile.FlowerPick.DEFAULT),
                "the class defaults are trees 0, flowers 2, grass 1, the patch pair 1/3/1");
        // Forest: trees 10, grass 2, the double plants.
        assertEquals(10, Biome.FOREST.decorator().treeAttempts());
        assertEquals(2, Biome.FOREST.decorator().grassAttempts());
        assertTrue(Biome.FOREST.decorator().flavor()
                .contains(DecoratorProfile.Flavor.FOREST_DOUBLE_PLANTS));
        // The flower forest: trees 6, flowers 100, grass 1, the +2 bonus.
        assertEquals(6, Biome.FLOWER_FOREST.decorator().treeAttempts());
        assertEquals(100, Biome.FLOWER_FOREST.decorator().flowerAttempts());
        assertEquals(1, Biome.FLOWER_FOREST.decorator().grassAttempts());
        assertTrue(Biome.FLOWER_FOREST.decorator().flavor()
                .contains(DecoratorProfile.Flavor.FLOWER_FOREST_DOUBLE_BONUS));
        // Plains: trees -999, flowers 4, grass 10, the band pass.
        assertEquals(-999, Biome.PLAINS.decorator().treeAttempts());
        assertEquals(4, Biome.PLAINS.decorator().flowerAttempts());
        assertEquals(10, Biome.PLAINS.decorator().grassAttempts());
        assertTrue(Biome.PLAINS.decorator().flavor()
                .contains(DecoratorProfile.Flavor.PLAINS_PASS));
        assertFalse(Biome.PLAINS.decorator().flavor()
                .contains(DecoratorProfile.Flavor.SUNFLOWER_PASS));
        assertTrue(Biome.SUNFLOWER_PLAINS.decorator().flavor()
                .contains(DecoratorProfile.Flavor.SUNFLOWER_PASS));
        // Desert: trees -999, deadBush 2, reeds 50, cacti 10, the well.
        assertEquals(-999, Biome.DESERT.decorator().treeAttempts());
        assertEquals(2, Biome.DESERT.decorator().deadBushAttempts());
        assertEquals(50, Biome.DESERT.decorator().sugarcaneAttempts());
        assertEquals(10, Biome.DESERT.decorator().cactusAttempts());
        assertTrue(Biome.DESERT.decorator().flavor()
                .contains(DecoratorProfile.Flavor.DESERT_WELL));
        // The desert's standard mutation carries the counts WITHOUT the well.
        assertEquals(50, Biome.DESERT_M.decorator().sugarcaneAttempts());
        assertTrue(Biome.DESERT_M.decorator().flavor().isEmpty(),
                "the standard mutations never run the flavor passes");
        // Swamp: trees 2, flowers 1, grass 5, deadBush 1, mushrooms 8,
        // reeds 10, clay 1, lilies 4, sand 0, gravel 0.
        DecoratorProfile swamp = Biome.SWAMPLAND.decorator();
        assertEquals(2, swamp.treeAttempts());
        assertEquals(1, swamp.flowerAttempts());
        assertEquals(5, swamp.grassAttempts());
        assertEquals(1, swamp.deadBushAttempts());
        assertEquals(8, swamp.mushroomAttempts());
        assertEquals(10, swamp.sugarcaneAttempts());
        assertEquals(1, swamp.clayPatchAttempts());
        assertEquals(4, swamp.lilyPadAttempts());
        assertEquals(0, swamp.sandPatchAttempts());
        assertEquals(0, swamp.gravelPatchAttempts());
        assertEquals(DecoratorProfile.TreePick.SWAMP, swamp.treePick());
        assertEquals(DecoratorProfile.FlowerPick.SWAMP_ORCHID, swamp.flowerPick());
        // Jungle: trees 50, flowers 4, grass 25, the melon + vine tail.
        assertEquals(50, Biome.JUNGLE.decorator().treeAttempts());
        assertEquals(4, Biome.JUNGLE.decorator().flowerAttempts());
        assertEquals(25, Biome.JUNGLE.decorator().grassAttempts());
        assertTrue(Biome.JUNGLE.decorator().flavor()
                .contains(DecoratorProfile.Flavor.JUNGLE_TAIL));
        assertEquals(2, Biome.JUNGLE_EDGE.decorator().treeAttempts());
        // The jungle's mutation carries 50 trees but never the tail.
        assertEquals(50, Biome.JUNGLE_M.decorator().treeAttempts());
        assertTrue(Biome.JUNGLE_M.decorator().flavor().isEmpty());
        // Taiga: trees 10, grass 1, mushrooms 1, the ferns.
        assertEquals(10, Biome.TAIGA.decorator().treeAttempts());
        assertEquals(1, Biome.TAIGA.decorator().grassAttempts());
        assertEquals(1, Biome.TAIGA.decorator().mushroomAttempts());
        assertEquals(DecoratorProfile.PlantPick.TAIGA_FERN,
                Biome.TAIGA.decorator().plantPick());
        assertTrue(Biome.TAIGA.decorator().flavor()
                .contains(DecoratorProfile.Flavor.TAIGA_FERNS));
        // The mega taiga: grass 7, deadBush 1, mushrooms 3, the boulders.
        assertEquals(7, Biome.MEGA_TAIGA.decorator().grassAttempts());
        assertEquals(1, Biome.MEGA_TAIGA.decorator().deadBushAttempts());
        assertEquals(3, Biome.MEGA_TAIGA.decorator().mushroomAttempts());
        assertTrue(Biome.MEGA_TAIGA.decorator().flavor()
                .contains(DecoratorProfile.Flavor.MEGA_BOULDERS));
        // The taiga's mutation carries the counts without the passes.
        assertEquals(1, Biome.TAIGA_M.decorator().mushroomAttempts());
        assertTrue(Biome.TAIGA_M.decorator().flavor().isEmpty());
        // The mushroom isle: the -100 sentinels, mushrooms 1, huge 1.
        assertEquals(-100, Biome.MUSHROOM_ISLAND.decorator().treeAttempts());
        assertEquals(-100, Biome.MUSHROOM_ISLAND.decorator().flowerAttempts());
        assertEquals(-100, Biome.MUSHROOM_ISLAND.decorator().grassAttempts());
        assertEquals(1, Biome.MUSHROOM_ISLAND.decorator().mushroomAttempts());
        assertEquals(1, Biome.MUSHROOM_ISLAND.decorator().hugeMushroomAttempts());
        // The savanna pair + the shattered counts.
        assertEquals(1, Biome.SAVANNA.decorator().treeAttempts());
        assertEquals(20, Biome.SAVANNA.decorator().grassAttempts());
        assertEquals(2, Biome.SAVANNA_M.decorator().treeAttempts());
        assertEquals(2, Biome.SAVANNA_M.decorator().flowerAttempts());
        assertEquals(5, Biome.SAVANNA_M.decorator().grassAttempts());
        // The mesa family: deadBush 20, reeds 3, cacti 5, flowers 0;
        // the plateau F carries the trees.
        assertEquals(20, Biome.MESA.decorator().deadBushAttempts());
        assertEquals(3, Biome.MESA.decorator().sugarcaneAttempts());
        assertEquals(5, Biome.MESA.decorator().cactusAttempts());
        assertEquals(0, Biome.MESA.decorator().flowerAttempts());
        assertEquals(-999, Biome.MESA.decorator().treeAttempts());
        assertEquals(5, Biome.MESA_PLATEAU_F.decorator().treeAttempts());
        // The hills: the base carries no trees, the edge + the plus 3.
        assertEquals(0, Biome.EXTREME_HILLS.decorator().treeAttempts());
        assertEquals(3, Biome.EXTREME_HILLS_EDGE.decorator().treeAttempts());
        assertEquals(3, Biome.EXTREME_HILLS_PLUS.decorator().treeAttempts());
        assertTrue(Biome.EXTREME_HILLS.decorator().flavor()
                .contains(DecoratorProfile.Flavor.HILLS_VEINS));
        // The roofed forest: the grid + the double plants.
        assertEquals(-999, Biome.ROOFED_FOREST.decorator().treeAttempts());
        assertTrue(Biome.ROOFED_FOREST.decorator().flavor()
                .contains(DecoratorProfile.Flavor.ROOFED_GRID));
        // The ice spikes carry their pass; the ice plains do not.
        assertTrue(Biome.ICE_PLAINS_SPIKES.decorator().flavor()
                .contains(DecoratorProfile.Flavor.ICE_SPIKES));
        assertTrue(Biome.ICE_PLAINS.decorator().flavor().isEmpty());
    }

    // ------------------------------------------------------------ determinism

    /**
     * The determinism per seed: two generators over the same world name
     * rebuild every probed chunk cell-identically (the region
     * re-derivation window's own contract — every foreign cell's writer
     * exactly one pass).
     */
    @Test
    void theSameSeedRebuildsIdenticalChunks() {
        BlockRegistry registry = registry();
        for (String world : new String[]{"decor-world", "decor-b"}) {
            NormalWorldGenerator first = new NormalWorldGenerator(registry, world);
            NormalWorldGenerator second = new NormalWorldGenerator(registry, world);
            for (int chunkX = 0; chunkX < 6; chunkX++) {
                for (int chunkZ = 0; chunkZ < 6; chunkZ++) {
                    EngineChunk a = new EngineChunk(
                            new net.zaminmc.torch.block.ChunkPosition(chunkX, chunkZ),
                            block(registry, "air"));
                    EngineChunk b = new EngineChunk(
                            new net.zaminmc.torch.block.ChunkPosition(chunkX, chunkZ),
                            block(registry, "air"));
                    first.generate(a);
                    second.generate(b);
                    for (int lx = 0; lx < 16; lx++) {
                        for (int y = 0; y < 256; y++) {
                            for (int lz = 0; lz < 16; lz++) {
                                assertEquals(a.getBlock(lx, y, lz), b.getBlock(lx, y, lz),
                                        "the cell " + lx + "," + y + "," + lz
                                                + " rebuilds identically");
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The walk places (the walk's own stages): a forest sweep carries
     * trunk wood with the birch share, and two worlds decorate
     * differently (the seed law).
     */
    @Test
    void theWalkPlacesForestTreesAndDistinguishesSeeds() {
        BlockRegistry registry = registry();
        NormalWorldGenerator generator =
                new NormalWorldGenerator(registry, "decor-forest");
        int placed = 0;
        boolean birch = false;
        for (int chunkX = 0; chunkX < 64 && placed < 40; chunkX++) {
            for (int chunkZ = 0; chunkZ < 64 && placed < 40; chunkZ++) {
                if (generator.biomeAt(chunkX * 16 + 8, chunkZ * 16 + 8) != Biome.FOREST) {
                    continue;
                }
                EngineChunk chunk = new EngineChunk(
                        new net.zaminmc.torch.block.ChunkPosition(chunkX, chunkZ),
                        block(registry, "air"));
                generator.generate(chunk);
                for (int lx = 0; lx < 16; lx++) {
                    for (int y = 60; y < 128; y++) {
                        for (int lz = 0; lz < 16; lz++) {
                            String value = chunk.getBlock(lx, y, lz)
                                    .identifier().value();
                            if (value.endsWith("_log")) {
                                placed++;
                                birch |= value.equals("birch_log");
                            }
                        }
                    }
                }
            }
        }
        assertTrue(placed >= 40, "the forest sweep carries trunk wood (" + placed + ")");
        // The birch share rides the 1-in-5 pick (some probed chunk carries
        // one over the 40-tree sample).
        assertTrue(birch, "the forest's birch share appears in the sweep");
        // Different worlds place differently (the seed law).
        NormalWorldGenerator other = new NormalWorldGenerator(registry, "decor-forest-2");
        EngineChunk otherChunk = new EngineChunk(
                new net.zaminmc.torch.block.ChunkPosition(0, 0),
                block(registry, "air"));
        other.generate(otherChunk);
        EngineChunk baseChunk = new EngineChunk(
                new net.zaminmc.torch.block.ChunkPosition(0, 0),
                block(registry, "air"));
        new NormalWorldGenerator(registry, "decor-forest").generate(baseChunk);
        boolean anyDifference = false;
        for (int lx = 0; lx < 16 && !anyDifference; lx++) {
            for (int y = 0; y < 256 && !anyDifference; y++) {
                for (int lz = 0; lz < 16 && !anyDifference; lz++) {
                    anyDifference = !baseChunk.getBlock(lx, y, lz)
                            .equals(otherChunk.getBlock(lx, y, lz));
                }
            }
        }
        assertTrue(anyDifference, "two worlds decorate differently");
    }

    // ------------------------------------------------- the flower override rows

    /** The reference's own FOLIAGE_NOISE (the fixed 2345 seed). */
    private static final PerlinSimplexNoise FOLIAGE_NOISE =
            new PerlinSimplexNoise(new Random(2345L), 1);

    /**
     * The plains' flower override (PlainsBiome.getRandomFlower lines
     * 24-46): the tulip band below -0.8 grows the four tulips; the other
     * band grows the poppy/houstonia/oxeye ladder — the walk's chunk
     * selection is the reference's own +16-corner biome read (the chunk
     * whose corner is the plains decorates as plains), the band counts'
     * consult the chunk's +8 center.
     */
    @Test
    void thePlainsTulipBandGrowsTheTulips() {
        BlockRegistry registry = registry();
        NormalWorldGenerator generator = new NormalWorldGenerator(registry, "decor-plains");
        int[] bandChunk = null;
        int[] plainChunk = null;
        for (int cx = 0; cx < 400 && (bandChunk == null || plainChunk == null); cx++) {
            for (int cz = 0; cz < 400 && (bandChunk == null || plainChunk == null); cz++) {
                // The walk's own biome read (populateChunk line 336: the
                // +16 corner) + the band-count consult (the +8 center).
                if (generator.biomeAt(cx * 16 + 16, cz * 16 + 16) != Biome.PLAINS) {
                    continue;
                }
                double d0 = FOLIAGE_NOISE.getValue(
                        (cx * 16 + 8) / 200.0, (cz * 16 + 8) / 200.0);
                if (d0 < -0.8 && bandChunk == null) {
                    bandChunk = new int[]{cx, cz};
                } else if (d0 >= -0.6 && plainChunk == null) {
                    plainChunk = new int[]{cx, cz};
                }
            }
        }
        assertNotNull(bandChunk, "a plains chunk inside the tulip band exists");
        assertNotNull(plainChunk, "a plains chunk outside the tulip band exists");
        // The band chunk's 2x2 block (the band's foreign writes land in
        // the +x/+z neighbors) grows tulips — the 15-attempt band counts.
        assertTrue(quadrantCarriesFamily(registry, generator, bandChunk[0], bandChunk[1],
                List.of("orange_tulip", "red_tulip", "white_tulip", "pink_tulip")),
                "the tulip band grows the four tulips");
        // The other band grows the ladder flowers — the reference's own
        // four attempts can come up empty on one chunk (the vanilla dice),
        // so the pin walks candidates until one answers.
        boolean ladder = false;
        int found = 0;
        for (int cx = plainChunk[0]; cx < 400 && !ladder && found < 12; cx++) {
            for (int cz = 0; cz < 400 && !ladder && found < 12; cz++) {
                if (generator.biomeAt(cx * 16 + 16, cz * 16 + 16) != Biome.PLAINS) {
                    continue;
                }
                double d0 = FOLIAGE_NOISE.getValue(
                        (cx * 16 + 8) / 200.0, (cz * 16 + 8) / 200.0);
                if (d0 < -0.6) {
                    continue;
                }
                found++;
                ladder = quadrantCarriesFamily(registry, generator, cx, cz,
                        List.of("poppy", "houstonia", "oxeye_daisy", "dandelion"));
            }
        }
        assertTrue(ladder, "the non-band plains grows the ladder flowers ("
                + found + " candidates probed)");
    }

    /**
     * The swamp's flower override (SwampBiome lines 47-49): the blue
     * orchid alone — a swamp-decorated chunk answers it.
     */
    @Test
    void theSwampGrowsTheBlueOrchid() {
        BlockRegistry registry = registry();
        NormalWorldGenerator generator = new NormalWorldGenerator(registry, "decor-swamp");
        for (int cx = 0; cx < 500; cx++) {
            for (int cz = 0; cz < 500; cz++) {
                if (generator.biomeAt(cx * 16 + 16, cz * 16 + 16) != Biome.SWAMPLAND) {
                    continue;
                }
                // The swamp's single flower attempt is a real roll — try
                // qualifying chunks until one carries the orchid (the
                // deterministic seed pins the answer for every run).
                if (quadrantCarriesFamily(registry, generator, cx, cz,
                        List.of("blue_orchid"))) {
                    return;
                }
            }
        }
        assertTrue(false, "a swamp-decorated chunk grows the blue orchid");
    }

    /**
     * The mushroom isle (MushroomBiome): the huge-mushroom stage grows
     * the 99/100 families' blocks — the chunk selection the walk's own
     * +16-corner read again.
     */
    @Test
    void theMushroomIsleGrowsHugeMushrooms() {
        BlockRegistry registry = registry();
        NormalWorldGenerator generator =
                new NormalWorldGenerator(registry, "decor-mushroom");
        for (int cx = 0; cx < 700; cx++) {
            for (int cz = 0; cz < 700; cz++) {
                if (generator.biomeAt(cx * 16 + 16, cz * 16 + 16) != Biome.MUSHROOM_ISLAND) {
                    continue;
                }
                if (quadrantCarriesFamily(registry, generator, cx, cz, List.of())) {
                    return;
                }
            }
        }
        assertTrue(false, "a mushroom-island-decorated chunk grows a huge mushroom");
    }

    /**
     * Generates one chunk quadrant (the chunk + its +x/+z neighbors —
     * the populate band's foreign writes) and answers whether any cell
     * carries one of the family's values.
     */
    private static boolean quadrantCarriesFamily(BlockRegistry registry,
            NormalWorldGenerator generator, int chunkX, int chunkZ,
            List<String> values) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                EngineChunk chunk = new EngineChunk(
                        new net.zaminmc.torch.block.ChunkPosition(chunkX + dx, chunkZ + dz),
                        block(registry, "air"));
                generator.generate(chunk);
                if (values.isEmpty()
                        ? chunkCarriesMushroomFamily(chunk)
                        : chunkCarriesFamily(chunk, values)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The huge-mushroom family consult (the 99/100 wire ids' fourteen
     * VARIANT states share the family prefixes — the stem, the pores and
     * the caps all ride them).
     */
    private static boolean chunkCarriesMushroomFamily(EngineChunk chunk) {
        for (int lx = 0; lx < 16; lx++) {
            for (int y = 0; y < 256; y++) {
                for (int lz = 0; lz < 16; lz++) {
                    String value = chunk.getBlock(lx, y, lz).identifier().value();
                    if (value.startsWith("brown_mushroom_block")
                            || value.startsWith("red_mushroom_block")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** @return whether any cell of the chunk carries one of the family's values. */
    private static boolean chunkCarriesFamily(EngineChunk chunk, List<String> values) {
        for (int lx = 0; lx < 16; lx++) {
            for (int y = 0; y < 256; y++) {
                for (int lz = 0; lz < 16; lz++) {
                    if (values.contains(chunk.getBlock(lx, y, lz)
                            .identifier().value())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ------------------------------------------------- the scripted-world laws

    /**
     * A scripted world for the feature laws: a flat grass floor at y 64
     * over stone, every consult answered from the map, the writes + the
     * pending queues recorded.
     */
    static final class ScriptedWorld implements FeatureWorld {
        private final Map<Long, BlockType> blocks = new HashMap<>();
        private final List<int[]> spawners = new ArrayList<>();
        private final List<int[]> chests = new ArrayList<>();
        private final BlockType air;
        private final Biome biome;

        ScriptedWorld(BlockRegistry registry, Biome biome) {
            this.air = registry.require(Identifier.parse("minecraft:air"));
            this.biome = biome;
            for (int x = -24; x < 40; x++) {
                for (int z = -24; z < 40; z++) {
                    for (int y = 0; y < 64; y++) {
                        set(x, y, z, registry.require(
                                Identifier.parse("minecraft:stone")));
                    }
                    set(x, 64, z, registry.require(
                            Identifier.parse("minecraft:grass_block")));
                }
            }
        }

        private static long key(int x, int y, int z) {
            return ((long) (x & 0xFFFFFF)) << 40 | ((long) (y & 0xFF)) << 32
                    | (long) (z & 0xFFFFFF);
        }

        void set(int x, int y, int z, BlockType type) {
            blocks.put(key(x, y, z), type);
        }

        @Override
        public BlockType getBlock(BlockPosition position) {
            return blockAt(position.x(), position.y(), position.z());
        }

        @Override
        public boolean setBlock(BlockPosition position, BlockType type) {
            set(position.x(), position.y(), position.z(), type);
            return true;
        }

        @Override
        public BlockType airType() {
            return air;
        }

        @Override
        public BlockType blockAt(int x, int y, int z) {
            if (y < 0 || y > 255) {
                return air;
            }
            return blocks.getOrDefault(key(x, y, z), air);
        }

        @Override
        public void setBlockAt(int x, int y, int z, BlockType type) {
            set(x, y, z, type);
        }

        @Override
        public boolean isAirAt(int x, int y, int z) {
            return blockAt(x, y, z).equals(air);
        }

        @Override
        public boolean isLiquidAt(int x, int y, int z) {
            String value = blockAt(x, y, z).identifier().value();
            return value.equals("water") || value.equals("lava");
        }

        @Override
        public boolean isSolidAt(int x, int y, int z) {
            return net.zaminmc.torch.server.block.WorldSolidity.isSolid(blockAt(x, y, z));
        }

        @Override
        public boolean isOpaqueAt(int x, int y, int z) {
            return net.zaminmc.torch.server.block.BlockLightTable.filter(
                    blockAt(x, y, z)) >= 15;
        }

        @Override
        public int heightAt(int x, int z) {
            for (int y = 255; y >= 0; y--) {
                if (net.zaminmc.torch.server.block.BlockLightTable.filter(
                        blockAt(x, y, z)) > 0) {
                    return y + 1;
                }
            }
            return 0;
        }

        @Override
        public int surfaceHeightAt(int x, int z) {
            for (int y = 255; y >= 0; y--) {
                BlockType at = blockAt(x, y, z);
                if (net.zaminmc.torch.server.block.WorldSolidity.isSolid(at)
                        && !net.zaminmc.torch.server.block.LeafDecayBlocks
                                .isLeafState(at.identifier())) {
                    return y + 1;
                }
            }
            return 0;
        }

        @Override
        public int seaLevel() {
            return 63;
        }

        @Override
        public Biome biomeAt(int x, int z) {
            return biome;
        }

        @Override
        public void placeMobSpawner(int x, int y, int z, MobType mobType) {
            spawners.add(new int[]{x, y, z, mobType.ordinal()});
        }

        @Override
        public void placeGenChest(int x, int y, int z,
                List<StructureWorld.GenLootSlot> slots) {
            chests.add(new int[]{x, y, z});
        }
    }

    /**
     * The cactus law (CactusFeature + CactusBlock.canSurvive): a cactus
     * grows on the open sand but never beside a solid block.
     */
    @Test
    void theCactusRefusesToGrowBesideSolids() {
        BlockRegistry registry = registry();
        ScriptedWorld world = new ScriptedWorld(registry, Biome.DESERT);
        // A sand floor.
        for (int x = -24; x < 40; x++) {
            for (int z = -24; z < 40; z++) {
                world.set(x, 64, z, block(registry, "sand"));
            }
        }
        // A stone block beside one column.
        world.set(10, 65, 10, block(registry, "stone"));
        new FloraFeatures.CactusFeature().place(world, new Random(1234L), 0, 65, 0);
        boolean openCactus = false;
        boolean walledCactus = false;
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                for (int y = 65; y < 72; y++) {
                    if (world.blockAt(x, y, z).identifier().value().equals("cactus")) {
                        boolean beside = false;
                        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                            if (!world.isAirAt(x + dir[0], y, z + dir[1])) {
                                beside = true;
                            }
                        }
                        if (beside) {
                            walledCactus = true;
                        } else {
                            openCactus = true;
                        }
                    }
                }
            }
        }
        assertTrue(openCactus, "the cactus grows on the open sand");
        assertFalse(walledCactus, "the cactus never grows beside a solid block");
    }

    /**
     * The reed law (SugarcaneFeature + ReedBlock.canBlockStay): the reed
     * requires the water-adjacent ground.
     */
    @Test
    void theReedsRequireWaterAdjacentGround() {
        BlockRegistry registry = registry();
        ScriptedWorld world = new ScriptedWorld(registry, Biome.PLAINS);
        // A pond at the shore.
        for (int x = 2; x <= 4; x++) {
            for (int z = -1; z <= 1; z++) {
                world.set(x, 64, z, block(registry, "water"));
                world.set(x, 63, z, block(registry, "dirt"));
            }
        }
        new FloraFeatures.SugarcaneFeature().place(world, new Random(55L), 1, 65, 0);
        boolean reedPlanted = false;
        boolean dryLandReed = false;
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                for (int y = 65; y < 72; y++) {
                    if (world.blockAt(x, y, z).identifier().value().equals("sugar_cane")) {
                        reedPlanted = true;
                        // The stack's own law: walk down to the stack's
                        // base — the BASE cell rides the water-adjacency
                        // gate (the stacked cells consult the reed below).
                        int baseY = y;
                        while (world.blockAt(x, baseY - 1, z).identifier()
                                .value().equals("sugar_cane")) {
                            baseY--;
                        }
                        boolean waterAdjacent = false;
                        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                            if (world.isLiquidAt(x + dir[0], baseY - 1, z + dir[1])) {
                                waterAdjacent = true;
                            }
                        }
                        dryLandReed |= !waterAdjacent;
                    }
                }
            }
        }
        assertTrue(reedPlanted, "the reeds grow at the pond's shore");
        assertFalse(dryLandReed, "the reeds never grow away from water");
    }

    /**
     * The lake's shape (LakeFeature): the bowl fills with the liquid,
     * the dome opens to air, and the cap under the open sky turns to
     * grass.
     */
    @Test
    void theLakeShapesItsBowlAndCap() {
        BlockRegistry registry = registry();
        ScriptedWorld world = new ScriptedWorld(registry, Biome.PLAINS);
        new TerrainFeatures.LakeFeature(block(registry, "water"))
                .place(world, new Random(7L), 8, 64, 8);
        boolean water = false;
        boolean carvedAir = false;
        boolean grassCap = false;
        for (int x = -8; x <= 24; x++) {
            for (int z = -8; z <= 24; z++) {
                for (int y = 50; y < 70; y++) {
                    String value = world.blockAt(x, y, z).identifier().value();
                    if (value.equals("water")) {
                        water = true;
                    }
                    if (world.blockAt(x, y, z).equals(world.airType())) {
                        carvedAir = true;
                    }
                    if (value.equals("grass_block")) {
                        grassCap = true;
                    }
                }
            }
        }
        assertTrue(water, "the lake fills its bowl");
        assertTrue(carvedAir, "the lake carves its dome");
        assertTrue(grassCap, "the dirt cap turns to grass under the open sky");
    }

    /**
     * The dungeon's shape (DungeonFeature): the mossy/cobble room, the
     * typed spawner record at the center, and the loot-carrying chest
     * records in the walls.
     */
    @Test
    void theDungeonCarvesTheMossyRoomWithSpawnerAndChests() {
        BlockRegistry registry = registry();
        ScriptedWorld world = new ScriptedWorld(registry, Biome.PLAINS);
        // A solid stone volume for the room to carve, laced with small
        // cave pockets on the walls (the dungeon's own gate needs 1-5
        // wall openings — it digs into cave walls, never solid stone).
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                for (int y = 40; y <= 56; y++) {
                    world.set(x, y, z, block(registry, "stone"));
                }
            }
        }
        for (int x = 3; x <= 5; x++) {
            for (int z = -1; z <= 1; z++) {
                world.set(x, 48, z, world.airType());
                world.set(x, 49, z, world.airType());
            }
        }
        boolean placed = false;
        for (int attempt = 0; !placed && attempt < 40; attempt++) {
            placed = new TerrainFeatures.DungeonFeature()
                    .place(world, new Random(99L + attempt), 0, 48, 0);
        }
        assertTrue(placed, "the dungeon finds its room in the stone volume");
        assertEquals(1, world.spawners.size(), "the dungeon places one spawner record");
        assertEquals("minecraft:mob_spawner",
                world.blockAt(world.spawners.get(0)[0], world.spawners.get(0)[1],
                        world.spawners.get(0)[2]).identifier().toString(),
                "the spawner block sits at the record's cell");
        assertTrue(!world.chests.isEmpty(), "the dungeon places its chest records");
        boolean mossy = false;
        boolean cobble = false;
        boolean cleared = false;
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                for (int y = 44; y <= 54; y++) {
                    String value = world.blockAt(x, y, z).identifier().value();
                    mossy |= value.equals("mossy_cobblestone");
                    cobble |= value.equals("cobblestone");
                    cleared |= world.blockAt(x, y, z).equals(world.airType());
                }
            }
        }
        assertTrue(mossy && cobble, "the room's shell carries the mossy/cobble pair");
        assertTrue(cleared, "the room's interior is cleared");
    }

    /**
     * The desert well's shape (DesertWellFeature): the sandstone base
     * pair, the rim, and the water cross.
     */
    @Test
    void theDesertWellBuildsItsRimAndWaterCross() {
        BlockRegistry registry = registry();
        ScriptedWorld world = new ScriptedWorld(registry, Biome.DESERT);
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                world.set(x, 64, z, block(registry, "sand"));
            }
        }
        boolean placed = new TerrainFeatures.DesertWellFeature(
                block(registry, "water")).place(world, new Random(3L), 0, 65, 0);
        assertTrue(placed, "the well builds on the sand floor");
        // The base pair spans the 5x5 rows (the drop-down lands on the
        // sand at y 64), the rim rides the +1 row's outer ring, and the
        // water cross sits at the base row.
        assertEquals("minecraft:water", world.blockAt(0, 64, 0).identifier().toString(),
                "the well's center is the water itself");
        assertEquals("minecraft:sandstone", world.blockAt(0, 63, 0).identifier().toString(),
                "the base pair's lower row spans under the water");
        assertEquals("minecraft:sandstone", world.blockAt(2, 64, 2).identifier().toString());
        assertEquals("minecraft:sandstone", world.blockAt(2, 65, 2).identifier().toString(),
                "the rim rides the +1 row's outer ring");
        assertEquals("minecraft:water", world.blockAt(1, 64, 0).identifier().toString(),
                "the water cross reaches the base row's sides");
    }

    /**
     * The ice spike (IceSpikeFeature): the packed-ice column rises from
     * the snow floor.
     */
    @Test
    void theIceSpikeRisesFromTheSnowFloor() {
        BlockRegistry registry = registry();
        ScriptedWorld world = new ScriptedWorld(registry, Biome.ICE_PLAINS_SPIKES);
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                world.set(x, 64, z, block(registry, "snow"));
            }
        }
        boolean placed = new TerrainFeatures.IceSpikeFeature()
                .place(world, new Random(11L), 0, 65, 0);
        assertTrue(placed, "the spike builds on the snow floor");
        boolean packedIce = false;
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                for (int y = 60; y < 90; y++) {
                    packedIce |= world.blockAt(x, y, z).identifier()
                            .value().equals("packed_ice");
                }
            }
        }
        assertTrue(packedIce, "the spike carries the packed ice");
    }

    /**
     * The tree consults (the TreeWorld seam over the populate view): the
     * swamp tree places its vine-draped shape and the pine its taper —
     * the walk's own vocabulary through the scripted world.
     */
    @Test
    void theTreeShapesPlaceThroughTheSeam() {
        BlockRegistry registry = registry();
        ScriptedWorld world = new ScriptedWorld(registry, Biome.SWAMPLAND);
        assertTrue(TreeFeatures.swampTree(new Random(1L)).place(world, new Random(2L),
                new BlockPosition(0, 65, 0)), "the swamp tree places");
        boolean log = false;
        boolean leaves = false;
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = 64; y < 80; y++) {
                    String value = world.blockAt(x, y, z).identifier().value();
                    log |= value.equals("oak_log");
                    leaves |= value.equals("oak_leaves");
                }
            }
        }
        assertTrue(log && leaves, "the swamp tree carries its trunk and shell");

        ScriptedWorld pine = new ScriptedWorld(registry, Biome.TAIGA);
        assertTrue(TreeFeatures.pine(new Random(1L)).place(pine, new Random(3L),
                new BlockPosition(0, 65, 0)), "the pine places");
        boolean spruceLog = false;
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = 64; y < 90; y++) {
                    spruceLog |= pine.blockAt(x, y, z).identifier()
                            .value().equals("spruce_log");
                }
            }
        }
        assertTrue(spruceLog, "the pine carries its spruce trunk");
    }
}
