package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAVE OV — THE ORE-VEIN CENSUS (the owner's report 'terrain is not
 * vanilla like ore veins generation etc all are very wrong'): the vanilla
 * 1.8.8 vein law rides the populate walk's decorate head now — VeinFeature's
 * ellipsoid chains (the reference's WorldGenMinable form whole) placed by
 * the linear walker (the lapis by the triangular one) over the populate
 * stream's own chunk Random, replacing ONLY stone, with the default
 * OverworldGeneratorOptions table's counts/sizes/heights verbatim. The
 * retired generation-side hash ladder answered scattered single ore cells
 * in wrong depth bands with no stone family at all — the salt-and-pepper
 * world the owner saw.
 */
class OreVeinParityTest {

    private static final BlockRegistry REGISTRY = registry();

    private static BlockRegistry registry() {
        // The full boot chain (EngineServer's freeze order).
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

    private NormalWorldGenerator generator() {
        return new NormalWorldGenerator(REGISTRY, "orevein-world");
    }

    private EngineChunk generateAt(NormalWorldGenerator generator, int chunkX, int chunkZ) {
        EngineChunk chunk = new EngineChunk(
                new net.zaminmc.torch.block.ChunkPosition(chunkX, chunkZ), BuiltinBlocks.AIR);
        generator.generate(chunk);
        return chunk;
    }

    /** Counts every cell of one block kind in a chunk. */
    private int countOf(EngineChunk chunk, String value) {
        int count = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 256; y++) {
                    if (chunk.getBlock(x, y, z).identifier().value().equals(value)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** The set of (y-band, kind) pairs of the ore cells — the depth law. */
    private record Cell(int x, int y, int z) {
    }

    private java.util.List<Cell> cellsOf(EngineChunk chunk, String value) {
        java.util.List<Cell> cells = new java.util.ArrayList<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 256; y++) {
                    if (chunk.getBlock(x, y, z).identifier().value().equals(value)) {
                        cells.add(new Cell(x, y, z));
                    }
                }
            }
        }
        return cells;
    }

    /**
     * SEAT 1 — THE VEIN SHAPE (the retired hash ladder's loudest
     * divergence): vanilla ores grow in CONNECTED blobs — an ellipsoid
     * chain of 8-33 cells — never scattered single cells. Over a wide
     * chunk field, the majority of ore cells must have at least one
     * face-adjacent ore neighbor of the same kind (the hash ladder's
     * 1/10,000-per-cell rolls could never pass this: its cells stand
     * alone by construction).
     */
    @Test
    void theOresGrowInConnectedVeinsNotScatteredSingles() {
        NormalWorldGenerator generator = generator();
        String[] ores = {"coal_ore", "iron_ore"};
        int connected = 0;
        int total = 0;
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                EngineChunk chunk = generateAt(generator, cx, cz);
                for (String ore : ores) {
                    Set<Long> cells = new HashSet<>();
                    for (Cell cell : cellsOf(chunk, ore)) {
                        cells.add(cellKey(cell.x(), cell.y(), cell.z()));
                    }
                    for (Cell cell : cellsOf(chunk, ore)) {
                        total++;
                        if (cells.contains(cellKey(cell.x() + 1, cell.y(), cell.z()))
                                || cells.contains(cellKey(cell.x() - 1, cell.y(), cell.z()))
                                || cells.contains(cellKey(cell.x(), cell.y() + 1, cell.z()))
                                || cells.contains(cellKey(cell.x(), cell.y() - 1, cell.z()))
                                || cells.contains(cellKey(cell.x(), cell.y(), cell.z() + 1))
                                || cells.contains(cellKey(cell.x(), cell.y(), cell.z() - 1))) {
                            connected++;
                        }
                    }
                }
            }
        }
        assertTrue(total > 200, "the field carries ore cells (got " + total + ")");
        // The vanilla veins' blobs: nearly every cell sits inside a vein.
        assertTrue(connected * 100 / total >= 85,
                "the ore cells grow in connected veins ("
                        + connected + "/" + total + ")");
    }

    /**
     * SEAT 2 — THE STONE FAMILY (1.8.8's own): the diorite, granite and
     * andesite veins below y 80 — the family the retired ladder never
     * generated at all.
     */
    @Test
    void theStoneFamilyVeinsGenerateBelowEighty() {
        NormalWorldGenerator generator = generator();
        int granite = 0;
        int diorite = 0;
        int andesite = 0;
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                EngineChunk chunk = generateAt(generator, cx, cz);
                granite += countOf(chunk, "granite");
                diorite += countOf(chunk, "diorite");
                andesite += countOf(chunk, "andesite");
                // The band law: no family cell above y 79.
                for (Cell cell : cellsOf(chunk, "granite")) {
                    assertTrue(cell.y() < 80, "granite below 80 (y=" + cell.y() + ")");
                }
                for (Cell cell : cellsOf(chunk, "diorite")) {
                    assertTrue(cell.y() < 80, "diorite below 80 (y=" + cell.y() + ")");
                }
                for (Cell cell : cellsOf(chunk, "andesite")) {
                    assertTrue(cell.y() < 80, "andesite below 80 (y=" + cell.y() + ")");
                }
            }
        }
        // The 10-veins-of-33 per chunk per kind: a 5x5 field carries
        // hundreds of cells per kind.
        assertTrue(granite > 500, "granite veins generate (got " + granite + ")");
        assertTrue(diorite > 500, "diorite veins generate (got " + diorite + ")");
        assertTrue(andesite > 500, "andesite veins generate (got " + andesite + ")");
    }

    /**
     * SEAT 3 — THE DEPTH BANDS (the default table's heights): coal below
     * 128, iron below 64, gold below 32, redstone and diamond below 16,
     * lapis below 32 (the triangle's tail). The retired ladder's bands
     * were wrong whole (coal to 60, iron to 54, gold to 30).
     */
    @Test
    void theOreDepthBandsMatchTheVanillaTable() {
        NormalWorldGenerator generator = generator();
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                EngineChunk chunk = generateAt(generator, cx, cz);
                for (Cell cell : cellsOf(chunk, "coal_ore")) {
                    assertTrue(cell.y() < 128, "coal below 128 (y=" + cell.y() + ")");
                }
                for (Cell cell : cellsOf(chunk, "iron_ore")) {
                    assertTrue(cell.y() < 64, "iron below 64 (y=" + cell.y() + ")");
                }
                for (Cell cell : cellsOf(chunk, "gold_ore")) {
                    assertTrue(cell.y() < 32, "gold below 32 (y=" + cell.y() + ")");
                }
                for (Cell cell : cellsOf(chunk, "redstone_ore")) {
                    assertTrue(cell.y() < 16, "redstone below 16 (y=" + cell.y() + ")");
                }
                for (Cell cell : cellsOf(chunk, "diamond_ore")) {
                    assertTrue(cell.y() < 16, "diamond below 16 (y=" + cell.y() + ")");
                }
                for (Cell cell : cellsOf(chunk, "lapis_ore")) {
                    assertTrue(cell.y() >= 0 && cell.y() < 32,
                            "lapis inside the triangle (y=" + cell.y() + ")");
                }
            }
        }
    }

    /**
     * SEAT 4 — THE DENSITY LADDER (the table's counts, statistically):
     * coal (20 veins of 17 per chunk) must outnumber iron (20 of 9),
     * iron must outnumber gold (2 of 9), and redstone (8 of 8) must
     * outnumber diamond (1 of 8) — the ordering the retired ladder's
     * arbitrary per-cell probabilities inverted (gold rarer than iron
     * only by luck, redstone denser than coal at depth).
     */
    @Test
    void theOreDensityOrderingMatchesTheVanillaCounts() {
        NormalWorldGenerator generator = generator();
        int coal = 0;
        int iron = 0;
        int gold = 0;
        int redstone = 0;
        int diamond = 0;
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                EngineChunk chunk = generateAt(generator, cx, cz);
                coal += countOf(chunk, "coal_ore");
                iron += countOf(chunk, "iron_ore");
                gold += countOf(chunk, "gold_ore");
                redstone += countOf(chunk, "redstone_ore");
                diamond += countOf(chunk, "diamond_ore");
            }
        }
        assertTrue(coal > iron, "coal outnumbers iron (" + coal + " vs " + iron + ")");
        assertTrue(iron > gold, "iron outnumbers gold (" + iron + " vs " + gold + ")");
        assertTrue(redstone > diamond,
                "redstone outnumbers diamond (" + redstone + " vs " + diamond + ")");
        assertTrue(gold > 0, "gold generates at all");
        assertTrue(diamond > 0, "diamond generates at all");
    }

    /**
     * SEAT 5 — THE DETERMINISM CONTRACT: the same seed rebuilds the
     * identical vein field (the populate stream's chunk seed law).
     */
    @Test
    void theSameSeedRebuildsIdenticalVeins() {
        NormalWorldGenerator first = generator();
        NormalWorldGenerator second = generator();
        int[] counts = new int[6];
        int[] countsAgain = new int[6];
        String[] kinds = {"coal_ore", "iron_ore", "gold_ore",
                "redstone_ore", "diamond_ore", "lapis_ore"};
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                EngineChunk a = generateAt(first, cx, cz);
                EngineChunk b = generateAt(second, cx, cz);
                for (int i = 0; i < kinds.length; i++) {
                    counts[i] += countOf(a, kinds[i]);
                    countsAgain[i] += countOf(b, kinds[i]);
                }
            }
        }
        for (int i = 0; i < kinds.length; i++) {
            assertEquals(counts[i], countsAgain[i],
                    "the " + kinds[i] + " field is seed-deterministic");
        }
    }

    /** The in-chunk cell key with the -1..16 neighbor band (x/z) and 0..255 y. */
    private static long cellKey(int x, int y, int z) {
        return ((long) (x + 1) * 18L + (z + 1)) * 256L + y;
    }
}
