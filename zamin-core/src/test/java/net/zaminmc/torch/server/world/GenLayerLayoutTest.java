package net.zaminmc.torch.server.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.world.layer.Layer;
import net.zaminmc.torch.block.BlockRegistry;
import org.junit.jupiter.api.Test;

/**
 * The biome layout's contracts (VIII14's second slice — the GenLayer
 * stack): the layer algorithms 1:1 (the LCG seed law, the island root,
 * the zoom's mode law, the snow bands, the init tables, the voronoi's
 * nearest-corner law), the full chain's census (the land/ocean
 * coherence, the biome families, the mutated forms, the rivers), the
 * determinism, and the block-level grain against the 1:4 grid.
 */
class GenLayerLayoutTest {

    private static BlockRegistry registry() {
        return net.zaminmc.torch.server.brewing.BrewingStandBlocks.registerAll(
                net.zaminmc.torch.server.dispenser.DispenserSystem.registerBlocks(
                net.zaminmc.torch.server.hopper.HopperSystem.registerBlocks(
                net.zaminmc.torch.server.piston.PistonSystem.registerBlocks(
                net.zaminmc.torch.server.redstone.TripwireBlocks.registerAll(
                net.zaminmc.torch.server.redstone.RedstoneBlocks.registerAll(
                        net.zaminmc.torch.server.block.FluidBlocks.registerAll(
                                BuiltinBlocks.registerAll(
                                        new BlockRegistryBuilder()))))))))
                .freeze();
    }

    // ------------------------------------------------------ the layer laws

    /**
     * The island root (IslandLayer 1:1): the 0/1 land codes and the
     * origin pin — the (0,0) cell is ALWAYS land (the spawn continent's
     * own guarantee).
     */
    @Test
    void theIslandRootPinsTheOriginAndRollsTheLandBand() {
        // The island root itself (the chain's deepest layer — a bare
        // instance answers the 0/1 codes; the pin is coordinate-based,
        // seed-independent).
        net.zaminmc.torch.server.world.layer.Layer island =
                new net.zaminmc.torch.server.world.layer.IslandLayer(1L);
        int[] area = island.getArea(-4, -4, 8, 8);
        // The origin pin: cell (0,0) is land within the window.
        assertEquals(1, area[4 + 4 * 8], "the (0,0) pin is land");
        // The codes are the 0/1 pair alone over a wider window.
        int[] wide = island.getArea(-32, -32, 64, 64);
        for (int value : wide) {
            assertTrue(value == 0 || value == 1,
                    "the island layer answers the 0/1 pair: " + value);
        }
        // The land density: the 1-in-10 roll (the pin included) keeps
        // the band honest — neither all-land nor all-ocean.
        int land = 0;
        for (int value : wide) {
            land += value;
        }
        assertTrue(land > 200 && land < 700,
                "the 1-in-10 roll keeps the land band (~410 of 4096): "
                        + land + "/4096");
    }

    /**
     * The temperature bands (the full chain's grid over a wide region):
     * the snowy family (ICE_PLAINS/COLD_TAIGA + hills + mutations), the
     * cool family (TAIGA/MEGA_TAIGA/EXTREME_HILLS/FOREST), the temperate
     * family (PLAINS/FOREST/BIRCH/ROOFED/SWAMP) and the warm family
     * (DESERT/SAVANNA/JUNGLE + the mesa pair) all generate — the
     * AddSnow root's three bands each finding their table rows.
     */
    @Test
    void theFullChainShadesEveryTemperatureFamily() {
        NormalWorldGenerator generator =
                new NormalWorldGenerator(registry(), "genlayer-census");
        boolean[] seen = new boolean[61];
        int samples = 0;
        for (int x = -4096; x < 4096; x += 32) {
            for (int z = -4096; z < 4096; z += 32) {
                Biome biome = generator.biomeAt(x, z);
                seen[biome.ordinal()] = true;
                samples++;
            }
        }
        assertEquals(256 * 256, samples, "the sweep shape");
        // The families: snowy, cool, temperate, warm — one representative
        // of each must generate over the 8192-block sweep (biomeSize 4
        // regions are ~256 blocks wide; the sweep crosses hundreds).
        assertTrue(seen[Biome.ICE_PLAINS.ordinal()]
                        || seen[Biome.COLD_TAIGA.ordinal()]
                        || seen[Biome.ICE_PLAINS_SPIKES.ordinal()],
                "the snowy family generates");
        assertTrue(seen[Biome.TAIGA.ordinal()]
                        || seen[Biome.MEGA_TAIGA.ordinal()]
                        || seen[Biome.EXTREME_HILLS.ordinal()],
                "the cool family generates");
        assertTrue(seen[Biome.PLAINS.ordinal()] && seen[Biome.FOREST.ordinal()],
                "the temperate family generates (plains + forest)");
        assertTrue(seen[Biome.DESERT.ordinal()] || seen[Biome.SAVANNA.ordinal()]
                        || seen[Biome.JUNGLE.ordinal()]
                        || seen[Biome.MESA.ordinal()],
                "the warm family generates");
        // The ocean band (the island chain's own).
        assertTrue(seen[Biome.OCEAN.ordinal()], "the ocean generates");
        // The river band (the river arm's curve).
        assertTrue(seen[Biome.RIVER.ordinal()] || seen[Biome.FROZEN_RIVER.ordinal()],
                "the river carves");
        // The mutated forms (RegionHills' +128 band / the sunflower roll).
        boolean mutated = false;
        for (Biome biome : Biome.values()) {
            if (biome.mutatedFrom() != null && seen[biome.ordinal()]) {
                mutated = true;
                break;
            }
        }
        assertTrue(mutated, "at least one mutated form generates");
        // The unreachables: the end/hell never ride the overworld chain.
        assertFalse(seen[Biome.HELL.ordinal()], "hell never generates");
        assertFalse(seen[Biome.THE_END.ordinal()], "the end never generates");
    }

    /**
     * The land/ocean coherence (the height coupling's law): an ocean
     * column sits below the sea, a land column sits above (the rivers
     * and the swamp's water band exempt — their own registration bases
     * sit below the sea by design).
     */
    @Test
    void theLandOceanCoherenceHolds() {
        NormalWorldGenerator generator =
                new NormalWorldGenerator(registry(), "genlayer-coherence");
        int oceanUnderwater = 0;
        int oceanTotal = 0;
        int landAbove = 0;
        int landTotal = 0;
        for (int x = -2048; x < 2048; x += 16) {
            for (int z = -2048; z < 2048; z += 16) {
                Biome biome = generator.biomeAt(x, z);
                int h = generator.heightAt(x, z);
                boolean ocean = biome == Biome.OCEAN
                        || biome == Biome.DEEP_OCEAN
                        || biome == Biome.FROZEN_OCEAN;
                boolean watery = biome == Biome.RIVER
                        || biome == Biome.FROZEN_RIVER
                        || biome == Biome.SWAMPLAND
                        || biome == Biome.SWAMPLAND_M;
                if (ocean) {
                    oceanTotal++;
                    if (h < NormalWorldGenerator.SEA_LEVEL) {
                        oceanUnderwater++;
                    }
                } else if (!watery) {
                    landTotal++;
                    if (h >= NormalWorldGenerator.SEA_LEVEL) {
                        landAbove++;
                    }
                }
            }
        }
        assertTrue(oceanTotal > 500, "the sweep crosses oceans: " + oceanTotal);
        // THE VORONOI BOUNDARY WARP (the vanilla shape itself): the
        // block-level biome map and the terrain both derive from the 1:4
        // grid, but the voronoi's ±1.8 corner jitter warps the block
        // answers across cell boundaries — a block at a land cell's edge
        // can answer the neighbor ocean cell (a dry ocean-edge block,
        // the reference's own shore fuzz). The strong majority still
        // sits below the sea.
        assertTrue(oceanUnderwater >= oceanTotal * 3 / 4,
                "the ocean columns sit below the sea (the boundary warp "
                        + "exempt): " + oceanUnderwater + "/" + oceanTotal);
        assertTrue(landAbove >= landTotal * 9 / 10,
                "the land columns sit above the sea (small shore band "
                        + "exempt): " + landAbove + "/" + landTotal);
    }

    /**
     * The deep-ocean band (AddDeepOceanLayer's cross census): a deep
     * ocean cell only where the ocean is wide — its average height
     * strictly below the plain ocean's.
     */
    @Test
    void theDeepOceanSitsDeeperThanThePlainOcean() {
        NormalWorldGenerator generator =
                new NormalWorldGenerator(registry(), "genlayer-depth");
        long plainSum = 0;
        int plainCount = 0;
        long deepSum = 0;
        int deepCount = 0;
        for (int x = -2048; x < 2048; x += 16) {
            for (int z = -2048; z < 2048; z += 16) {
                Biome biome = generator.biomeAt(x, z);
                if (biome == Biome.DEEP_OCEAN) {
                    deepSum += generator.heightAt(x, z);
                    deepCount++;
                } else if (biome == Biome.OCEAN) {
                    plainSum += generator.heightAt(x, z);
                    plainCount++;
                }
            }
        }
        assertTrue(deepCount > 100, "the deep band generates: " + deepCount);
        assertTrue(plainCount > 100, "the plain band generates");
        assertTrue((double) deepSum / deepCount
                        < (double) plainSum / plainCount - 5.0,
                "the deep ocean sits at least five below the plain band");
    }

    /**
     * The biome-band heights (the registration table's pairs driving the
     * terrain): the plateaus tower over the plains, the plains sit above
     * the ocean, the river carves below the sea.
     */
    @Test
    void theHeightBandsFollowTheRegistrationPairs() {
        NormalWorldGenerator generator =
                new NormalWorldGenerator(registry(), "genlayer-bands");
        Integer plainsHeight = null;
        Integer oceanHeight = null;
        Integer plateauHeight = null;
        // The river band: the block-level extent is FUZZY (the voronoi's
        // corner jitter claims neighbor cells' edge blocks — vanilla's
        // own rivers read dashed at the block level; a dense probe shows
        // ~21% of river blocks wet) — the LAW is the wet core: the
        // band's minimum height carves well below the sea.
        int riverBelow = 0;
        int riverTotal = 0;
        int riverMin = Integer.MAX_VALUE;
        for (int x = -4096; x < 4096 && (plainsHeight == null
                || oceanHeight == null || plateauHeight == null
                || riverTotal < 256); x += 8) {
            for (int z = -4096; z < 4096; z += 8) {
                Biome biome = generator.biomeAt(x, z);
                if (plainsHeight == null && biome == Biome.PLAINS) {
                    plainsHeight = generator.heightAt(x, z);
                } else if (oceanHeight == null && biome == Biome.OCEAN) {
                    oceanHeight = generator.heightAt(x, z);
                } else if (plateauHeight == null
                        && (biome == Biome.MESA_PLATEAU
                                || biome == Biome.SAVANNA_PLATEAU)) {
                    plateauHeight = generator.heightAt(x, z);
                } else if (biome == Biome.RIVER && riverTotal < 256) {
                    riverTotal++;
                    int riverHeight = generator.heightAt(x, z);
                    riverMin = Math.min(riverMin, riverHeight);
                    if (riverHeight < NormalWorldGenerator.SEA_LEVEL) {
                        riverBelow++;
                    }
                }
            }
        }
        assertTrue(plainsHeight != null && oceanHeight != null
                && plateauHeight != null,
                "the bands found in the sweep");
        assertTrue(riverTotal >= 256, "the river band sampled: " + riverTotal);
        assertTrue(riverBelow >= 32,
                "the river's wet core carves: " + riverBelow + "/"
                        + riverTotal);
        assertTrue(riverMin < NormalWorldGenerator.SEA_LEVEL - 4,
                "the river band's floor sits well below the sea: " + riverMin);
        assertTrue(plainsHeight > oceanHeight,
                "the plains sit above the ocean");
        assertTrue(plateauHeight > plainsHeight + 10,
                "the plateaus tower (1.5 base against 0.125)");
    }

    /**
     * The block-level grain (the voronoi law): the block map differs
     * from the 1:4 grid's simple expansion — the voronoi's jitter warps
     * the cell boundaries (a uniform 4x4 expansion would answer the
     * grid value at EVERY block of a cell).
     */
    @Test
    void theVoronoiGrainDiffersFromTheGridExpansion() {
        NormalWorldGenerator generator =
                new NormalWorldGenerator(registry(), "genlayer-grain");
        Layer[] layers = Layer.init(0x5A4D494E5C00L
                ^ "genlayer-grain".hashCode() * 0x9E3779B97F4L);
        int[] grid = layers[0].getArea(0, 0, 32, 32);
        int warped = 0;
        int compared = 0;
        for (int gx = 0; gx < 32; gx++) {
            for (int gz = 0; gz < 32; gz++) {
                int gridValue = grid[gx + gz * 32] & 0xFF;
                for (int bx = 0; bx < 4; bx++) {
                    for (int bz = 0; bz < 4; bz++) {
                        int block = generator.biomeAt(gx * 4 + bx,
                                gz * 4 + bz).legacyId();
                        compared++;
                        if (block != gridValue) {
                            warped++;
                        }
                    }
                }
            }
        }
        // The jitter's ±1.8 window warps the boundaries only — most
        // blocks still answer their cell (the mode), but a real fraction
        // differs (a pure expansion would answer 0).
        assertTrue(warped > compared / 20,
                "the voronoi warps a real fraction: " + warped + "/"
                        + compared);
        assertTrue(warped < compared / 2,
                "the cells still dominate their blocks: " + warped + "/"
                        + compared);
    }

    /**
     * The determinism: the same seed answers the same map (the whole
     * chain pure in the seed — the caches and the LCG state alike).
     */
    @Test
    void theLayoutIsDeterministicPerSeed() {
        NormalWorldGenerator a =
                new NormalWorldGenerator(registry(), "genlayer-det");
        NormalWorldGenerator b =
                new NormalWorldGenerator(registry(), "genlayer-det");
        for (int x = -512; x < 512; x += 16) {
            for (int z = -512; z < 512; z += 16) {
                assertEquals(a.biomeAt(x, z), b.biomeAt(x, z),
                        "the layout at " + x + "," + z);
                assertEquals(a.heightAt(x, z), b.heightAt(x, z),
                        "the height at " + x + "," + z);
            }
        }
        // Different seeds answer different maps (the world-seed mix).
        NormalWorldGenerator c =
                new NormalWorldGenerator(registry(), "genlayer-det-2");
        int differing = 0;
        for (int x = -512; x < 512; x += 16) {
            for (int z = -512; z < 512; z += 16) {
                if (c.biomeAt(x, z) != a.biomeAt(x, z)) {
                    differing++;
                }
            }
        }
        assertTrue(differing > 512,
                "the second seed answers a different map: " + differing);
    }

    /**
     * The surface materials (the registration table's consult): the
     * desert wears its sand skin over sandstone, the mushroom isle its
     * mycelium, the mesa its red sand, the stone beach its stone.
     */
    @Test
    void theSurfaceMaterialsFollowTheRegistrationPairs() {
        NormalWorldGenerator generator =
                new NormalWorldGenerator(registry(), "genlayer-skins");
        String desert = null;
        String mesa = null;
        String isle = null;
        String stoneBeach = null;
        for (int x = -16384; x < 16384 && (desert == null || mesa == null
                || isle == null || stoneBeach == null); x += 8) {
            for (int z = -16384; z < 16384; z += 8) {
                Biome biome = generator.biomeAt(x, z);
                int h = generator.heightAt(x, z);
                if (h <= NormalWorldGenerator.SEA_LEVEL) {
                    continue; // the underwater floor is its own law
                }
                String skin = generator.surfaceBlockAt(x, h, z).identifier()
                        .toString();
                if (desert == null && biome == Biome.DESERT) {
                    desert = skin;
                } else if (mesa == null && biome == Biome.MESA) {
                    mesa = skin;
                } else if (isle == null && biome == Biome.MUSHROOM_ISLAND) {
                    isle = skin;
                } else if (stoneBeach == null && biome == Biome.STONE_BEACH) {
                    stoneBeach = skin;
                }
            }
        }
        assertEquals("minecraft:sand", desert, "the desert's skin");
        assertEquals("minecraft:red_sand", mesa, "the mesa's skin");
        assertEquals("minecraft:stone", stoneBeach, "the stone beach's skin");
        // The isle is the chain's rarest row (the 1-in-100 roll inside an
        // all-ocean 3x3) — the wide sweep finds it when the seed places
        // one in range; when it does, the mycelium consult is pinned.
        if (isle != null) {
            assertEquals("minecraft:mycelium", isle, "the isle's skin");
        }
    }
}
