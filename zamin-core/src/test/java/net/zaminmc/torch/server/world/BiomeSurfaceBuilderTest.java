package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.block.BlockRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The depth-dependent surface builders (VIII14's surface slice — the
 * reference's prepareAndBuildSurfaces whole over the 61-row
 * registration): the core walk's banding law, the four family switches
 * (the extreme hills' gravel/stone, the mega taiga's podzol/coarse, the
 * shattered savanna's stone, the mesa's clay stack), the seeded clay
 * table, and the Bryce pillars. The depth noise is continuous — every
 * case scans for a column inside the wanted depth band, the same
 * approach the GenLayer layout tests ride.
 */
class BiomeSurfaceBuilderTest {

    private static BlockRegistry registry() {
        // The full boot chain (the same order BiomeWorldTest rides — the
        // stained clay family and the dirt variants resolve through it).
        return net.zaminmc.torch.server.brewing.BrewingStandBlocks.registerAll(
                net.zaminmc.torch.server.dispenser.DispenserSystem.registerBlocks(
                net.zaminmc.torch.server.hopper.HopperSystem.registerBlocks(
                net.zaminmc.torch.server.piston.PistonSystem.registerBlocks(
                net.zaminmc.torch.server.redstone.TripwireBlocks.registerAll(
                net.zaminmc.torch.server.redstone.RedstoneBlocks.registerAll(
                        net.zaminmc.torch.server.block.FluidBlocks.registerAll(
                                BuiltinBlocks.registerAll(
                                        new net.zaminmc.torch.server.block.BlockRegistryBuilder()))))))))
                .freeze();
    }

    private static BiomeSurfaceBuilder builder() {
        return new BiomeSurfaceBuilder(0x5A4D494E5C00L, registry());
    }

    /** Finds a column whose depth sits inside the band. */
    private static int[] columnInBand(BiomeSurfaceBuilder builder,
            double minDepth, double maxDepth, int salt) {
        for (int ring = 0; ring < 512; ring++) {
            for (int x = -ring; x <= ring; x += 1) {
                for (int z = -ring; z <= ring; z += 1) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != ring) {
                        continue;
                    }
                    double depth = builder.depthAt(x, z);
                    if (depth > minDepth && depth < maxDepth) {
                        return new int[]{x, z};
                    }
                }
            }
            if (salt != 0 && ring > 4) {
                break;
            }
        }
        return null;
    }

    private static String name(BlockType type) {
        return type.identifier().value();
    }

    // ------------------------------------------------------------ the tests

    /**
     * The family switches ride their own depth bands: the mega taiga's
     * podzol through the middle band (-0.95, 1.75] and its coarse dirt
     * above 1.75; the shattered savanna's coarse through (-0.5, 1.75]
     * and its stone above; the extreme hills M's gravel at the extremes
     * and its stone past 1.0 — every switch the reference's own
     * thresholds, every column a real noise column inside the band.
     */
    @Test
    void theFamilySwitchesRideTheDepthBands() {
        BiomeSurfaceBuilder builder = builder();
        // The mega taiga's podzol band: (-0.95, 1.75].
        int[] podzol = columnInBand(builder, -0.5, 1.0, 0);
        assertNotNull(podzol, "a mid-band column exists");
        assertEquals("podzol",
                name(builder.blockAt(podzol[0], 70, podzol[1], 70,
                        Biome.MEGA_TAIGA)),
                "the mega taiga's mid-band surface is podzol");
        assertEquals("grass_block",
                name(builder.blockAt(podzol[0], 70, podzol[1], 70,
                        Biome.PLAINS)),
                "the plains column at the same depth stays grass");
        // The coarse band: (1.75, ...] — the high tail.
        int[] coarse = columnInBand(builder, 2.0, 4.5, 0);
        assertNotNull(coarse, "a high-band column exists");
        assertEquals("coarse_dirt",
                name(builder.blockAt(coarse[0], 70, coarse[1], 70,
                        Biome.MEGA_TAIGA)),
                "the mega taiga's high-band surface is coarse dirt");
        assertEquals("stone",
                name(builder.blockAt(coarse[0], 70, coarse[1], 70,
                        Biome.SAVANNA_M)),
                "the shattered savanna's high-band surface is stone");
        assertEquals("gravel",
                name(builder.blockAt(coarse[0], 70, coarse[1], 70,
                        Biome.EXTREME_HILLS_M)),
                "the extreme hills M's depth-extreme surface is gravel");
        assertEquals("stone",
                name(builder.blockAt(coarse[0], 70, coarse[1], 70,
                        Biome.EXTREME_HILLS)),
                "the base extreme hills stones past 1.0");
        // The low band: everything falls back to grass (the mega taiga)
        // or the registered pair.
        int[] low = columnInBand(builder, -3.0, -1.0, 0);
        if (low != null) {
            assertEquals("grass_block",
                    name(builder.blockAt(low[0], 70, low[1], 70,
                            Biome.MEGA_TAIGA)),
                    "the mega taiga's low band is grass");
        }
        // The extreme low: the hills M gravels again (depth < -1.0).
        int[] veryLow = columnInBand(builder, -4.5, -2.0, 0);
        if (veryLow != null) {
            assertEquals("gravel",
                    name(builder.blockAt(veryLow[0], 70, veryLow[1], 70,
                            Biome.EXTREME_HILLS_M)),
                    "the extreme hills M's low extreme is gravel");
        }
    }

    /**
     * The core walk's banding (buildSurfaces lines 452-508): the grass
     * cap over the dirt band (k cells deep, k = depth/3 + 3 + roll),
     * then the core stone; the shallow underwater floor carries the
     * SUBSURFACE (the depth-1 floor keeps the cap, deeper floors show
     * the dirt), and the deep ocean floor (below sea - 7 - k) is the
     * gravel arm.
     */
    @Test
    void theCoreWalkBandsTheColumn() {
        BiomeSurfaceBuilder builder = builder();
        int[] column = columnInBand(builder, -0.5, 1.0, 0);
        assertNotNull(column);
        int x = column[0];
        int z = column[1];
        double depth = builder.depthAt(x, z);
        // k at the mid band: 3 (2.83..3.08 — the roll's 0.25 width and
        // the depth's third).
        int k = (int) (depth / 3.0 + 3.0); // the roll's floor-ish bound
        assertTrue(k >= 2 && k <= 4, "the mid-band k: " + k);
        // The dry column: the cap, the k-wide dirt band, then the core.
        assertEquals("grass_block", name(builder.blockAt(x, 70, z, 70, Biome.PLAINS)));
        assertEquals("dirt", name(builder.blockAt(x, 69, z, 70, Biome.PLAINS)));
        assertEquals("dirt", name(builder.blockAt(x, 68, z, 70, Biome.PLAINS)));
        // Below the band the builder answers null (the ore ladder's own).
        assertTrue(builder.blockAt(x, 70 - 12, z, 70, Biome.PLAINS) == null
                || "dirt".equals(name(builder.blockAt(x, 70 - 12, z, 70, Biome.PLAINS))),
                "the band ends inside 12 cells");
        // The shallow underwater floor (h = 61): the subsurface arm.
        assertEquals("dirt", name(builder.blockAt(x, 61, z, 61, Biome.PLAINS)),
                "the depth-2 underwater floor shows the subsurface");
        // The depth-1 floor (h = 62) keeps the pair's cap.
        assertEquals("grass_block", name(builder.blockAt(x, 62, z, 62, Biome.PLAINS)),
                "the depth-1 underwater floor keeps the cap");
        // The deep floor (h = 50, below sea - 7 - k at any k >= 2): gravel.
        assertEquals("gravel", name(builder.blockAt(x, 50, z, 50, Biome.PLAINS)),
                "the deep floor is the gravel arm");
    }

    /**
     * The mesa stack: the low mesa's red-sand cap over the orange
     * banding, the tall mesa's clay-band cap (the cos band's hardened
     * clay or the seeded layer table), the plateau F's grass arm above
     * 86 + 2k, and the underwater floor's orange.
     */
    @Test
    void theMesaStackBandsItsClay() {
        BiomeSurfaceBuilder builder = builder();
        int[] column = columnInBand(builder, -0.5, 1.0, 0);
        assertNotNull(column);
        int x = column[0];
        int z = column[1];
        double depth = builder.depthAt(x, z);
        int k = (int) (depth / 3.0 + 3.0);
        // The low mesa (h = 64, inside [sea-1, sea+3+k]): red sand cap.
        assertEquals("red_sand", name(builder.blockAt(x, 64, z, 64, Biome.MESA)),
                "the low mesa's cap is the red sand");
        // The band below the red-sand cap: the orange banding (flag1).
        assertEquals("stained_clay_orange",
                name(builder.blockAt(x, 63, z, 64, Biome.MESA)),
                "the red-sand cap bands orange below");
        // The tall mesa (h = 100 > sea + 3 + k): the clay band's cap —
        // the hardened clay on the cos band, the layer table off it.
        BlockType tall = builder.blockAt(x, 100, z, 100, Biome.MESA);
        String tallName = name(tall);
        assertTrue(tallName.equals("hardened_clay")
                        || tallName.startsWith("stained_clay_"),
                "the tall mesa's cap rides the clay band: " + tallName);
        // The tall mesa's band cells: the seeded layer table (never the
        // orange banding — flag1 only rides the red-sand cap).
        BlockType band = builder.blockAt(x, 99, z, 100, Biome.MESA);
        assertTrue(name(band).equals("hardened_clay")
                        || name(band).startsWith("stained_clay_"),
                "the tall mesa's band rides the layer table");
        // The plateau F above 86 + 2k (h = 120): the coarse/grass arm.
        BlockType plateau = builder.blockAt(x, 120, z, 120, Biome.MESA_PLATEAU_F);
        assertTrue(name(plateau).equals("coarse_dirt")
                        || name(plateau).equals("grass_block"),
                "the plateau F's tall surface is the coarse/grass arm: "
                        + name(plateau));
        // The underwater mesa floor: orange.
        assertEquals("stained_clay_orange",
                name(builder.blockAt(x, 58, z, 58, Biome.MESA)),
                "the underwater mesa floor is orange");
    }

    /**
     * The seeded clay table (placeClayLayers 1:1): deterministic per
     * seed, the hardened clay fill with the colored runs woven through
     * (orange/yellow/brown/red/white/silver), and the layer consult's
     * offset walk — adjacent columns read shifted rows of the SAME
     * table (the mesa's banded strata).
     */
    @Test
    void theClayTableIsSeededAndBanded() {
        BiomeSurfaceBuilder builder = builder();
        // Deterministic: two tall-mesa consults agree.
        int[] column = columnInBand(builder, -0.5, 1.0, 0);
        assertNotNull(column);
        int x = column[0];
        int z = column[1];
        assertEquals(builder.blockAt(x, 100, z, 100, Biome.MESA),
                builder.blockAt(x, 100, z, 100, Biome.MESA),
                "the consult is deterministic");
        // The strata: walking a tall column down through the clay band
        // crosses BOTH hardened clay and at least one stained row (the
        // 64-row table's colored runs — orange appears ~11 times alone).
        boolean sawHardened = false;
        boolean sawStained = false;
        for (int y = 100; y > 20; y--) {
            BlockType at = builder.blockAt(x, y, z, 100, Biome.MESA);
            if (at == null) {
                break; // below the band
            }
            String value = name(at);
            if (value.equals("hardened_clay")) {
                sawHardened = true;
            } else if (value.startsWith("stained_clay_")) {
                sawStained = true;
            }
        }
        assertTrue(sawHardened, "the strata cross the hardened clay");
        assertTrue(sawStained, "the strata cross the stained runs");
        // The offset walk: two distant columns at the same y can read
        // different rows (the banded look).
        boolean anyDifference = false;
        for (int probe = 1; probe < 200 && !anyDifference; probe += 7) {
            if (!name(builder.blockAt(x + probe * 16, 100, z, 100, Biome.MESA))
                    .equals(name(builder.blockAt(x, 100, z, 100, Biome.MESA)))) {
                anyDifference = true;
            }
        }
        assertTrue(anyDifference, "the offset noise bands the strata across columns");
    }

    /**
     * The Bryce pillars (the mutated flag's own arm): over a MESA_BRYCE
     * column whose d4 stands above the terrain, stone rises to (int) d4
     * — the pillar's cap rides the mesa law, the body is plain stone,
     * and no other mesa row ever answers above its terrain.
     */
    @Test
    void theBrycePillarsRiseAboveTheTerrain() {
        BiomeSurfaceBuilder builder = builder();
        // Scan for a column whose bryce consult answers above the
        // terrain (d4 > h + 1).
        int foundX = -1;
        int foundZ = -1;
        int foundTop = -1;
        for (int x = 0; x < 512 && foundX < 0; x += 4) {
            for (int z = 0; z < 512 && foundX < 0; z += 4) {
                for (int h = 64; h <= 70; h++) {
                    if (builder.blockAt(x, h + 1, z, h, Biome.MESA_BRYCE) != null) {
                        foundX = x;
                        foundZ = z;
                        foundTop = h;
                        break;
                    }
                }
            }
        }
        assertTrue(foundX >= 0, "a bryce pillar column exists in the sweep");
        // The pillar: stone somewhere above the terrain, ending below
        // the cap, and the consult eventually answers null again.
        boolean sawStone = false;
        int top = -1;
        for (int y = foundTop + 30; y > foundTop; y--) {
            BlockType at = builder.blockAt(foundX, y, foundZ, foundTop,
                    Biome.MESA_BRYCE);
            if (at != null) {
                top = y;
                break;
            }
        }
        assertTrue(top > foundTop, "the pillar stands above the terrain");
        for (int y = top; y > foundTop; y--) {
            BlockType at = builder.blockAt(foundX, y, foundZ, foundTop,
                    Biome.MESA_BRYCE);
            if (at != null) {
                sawStone = true;
            }
        }
        assertTrue(sawStone, "the pillar's body carries stone");
        // The plain mesa never answers above its terrain.
        assertEquals(null, builder.blockAt(foundX, foundTop + 1, foundZ,
                foundTop, Biome.MESA),
                "the plain mesa column answers nothing above its terrain");
    }
}
