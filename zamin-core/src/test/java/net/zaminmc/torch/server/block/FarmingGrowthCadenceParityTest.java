package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The farming growth-cadence parity seats (the verifier's fourteenth pass):
 * the reference's own laws pinned against the engine's growth arms —
 *
 * <p>THE CROP FORMULA (WheatBlock.tick lines 34-45 over getGrowthSpeed
 * lines 56-98): {@code random.nextInt((int)(25.0F / f) + 1) == 0} with the
 * f speed summed from the 3x3 soil ring (wet farmland +3.0 at the center,
 * +0.75 wet/+0.25 dry per ring cell, the same-crop row halves). The
 * pre-audit engine's flat {@code nextInt(3)}/{@code nextInt(6)} pair grew
 * 2.33x/2.17x the reference's pace and carried none of the ring's row
 * acceleration.</p>
 *
 * <p>THE AGE-15 ACCUMULATORS (SugarCaneBlock/CactusBlock.tick lines 28-49):
 * a column's new block lands on the sixteenth band — the pre-audit flat
 * {@code nextInt(3)} was 5.33x too fast. THE CANE'S SUPPORT CONSULT
 * (SugarCaneBlock line 30): the stacked short-circuit and the base's own
 * water-loss pop — the water cell is DIAGONAL to the cane, so the pop rides
 * the random tick itself. THE CACTUS'S NEW-CELL CONSULT (CactusBlock line
 * 43): the consult reads the NEW cell's horizontals, not the current
 * top's, and a refused cell places then pops WITH its drop.</p>
 *
 * <p>Every statistical seat is SEEDED (a fixed {@link Random} seed): the
 * observed sums are deterministic constants, and the bands are cut to
 * separate the restored cadences from the pre-audit rates with margins no
 * seed variance can cross (the pre-fix tree's expected sums sit far
 * outside every band — the stash verification's own proof).</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FarmingGrowthCadenceParityTest {

    private static final int TRIALS = 240;

    private EngineWorld world;
    private RandomTickSystem ticks;
    private net.zaminmc.torch.server.entity.ItemEntityManager popItems;

    @BeforeAll
    void setUp() {
        FrozenBlockRegistry registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        world = new EngineWorld("parity", registry, new FlatWorldGenerator(registry, 4),
                Thread.currentThread());
        net.zaminmc.torch.server.world.light.LightEngine light =
                new net.zaminmc.torch.server.world.light.LightEngine(world);
        world.addChangeListener(light);
        world.addChunkLoadListener(light);
        world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(0, 0));
        // The plant-pop seam (the same manager shape the acceptance lanes
        // use): the pop seats assert the dropped item bodies.
        popItems = new net.zaminmc.torch.server.entity.ItemEntityManager(
                (x, y, z) -> false, new Random(0x0D0DL), 9000);
        ticks = new RandomTickSystem(world, new Random(0xC0FFEE1L));
        ticks.setPlantPopItems(popItems);
    }

    // ---------------------------------------------------------------
    // The f speed: the reference's getGrowthSpeed, pinned per shape
    // ---------------------------------------------------------------

    /** Wet farmland under the crop alone: f = 1.0 + 3.0 = 4.0 (line 66's
     *  MOISTURE > 0 bonus), the band (int)(25/4)+1 = 7. */
    @Test
    void theGrowthSpeedPinsTheWetCenterAtFour() {
        BlockPosition crop = cropOver(BuiltinBlocks.FARMLAND_WET, 6, 6);
        assertEquals(4.0f, RandomTickSystem.cropGrowthSpeed(world, crop,
                BuiltinBlocks.WHEAT_STAGE0.identifier()), 0.0001f);
        assertEquals(7, (int) (25.0f / 4.0f) + 1, "the wet-center band");
        clearCrop(6, 6);
    }

    /** Dry farmland alone: f = 1.0 + 1.0 = 2.0, the band 13. */
    @Test
    void theGrowthSpeedPinsTheDryCenterAtTwo() {
        BlockPosition crop = cropOver(BuiltinBlocks.FARMLAND, 12, 6);
        assertEquals(2.0f, RandomTickSystem.cropGrowthSpeed(world, crop,
                BuiltinBlocks.WHEAT_STAGE0.identifier()), 0.0001f);
        assertEquals(13, (int) (25.0f / 2.0f) + 1, "the dry-center band");
        clearCrop(12, 6);
    }

    /** No farmland at all (the reference's f=1 shape): the band 26. */
    @Test
    void theGrowthSpeedPinsTheBareGroundAtOne() {
        BlockPosition crop = cropOver(BuiltinBlocks.DIRT, 18, 6);
        assertEquals(1.0f, RandomTickSystem.cropGrowthSpeed(world, crop,
                BuiltinBlocks.WHEAT_STAGE0.identifier()), 0.0001f);
        assertEquals(26, (int) (25.0f / 1.0f) + 1, "the bare band");
        clearCrop(18, 6);
    }

    /**
     * The eight wet ring: f = 1.0 + 3.0 + 8*0.75 = 10.0 — the famous row
     * acceleration to a 1-in-3 band (lines 60-77), the pre-audit engine's
     * missing continuum.
     */
    @Test
    void theGrowthSpeedPinsTheEightWetRingAtTen() {
        BlockPosition crop = cropOver(BuiltinBlocks.FARMLAND_WET, 24, 6);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                world.setBlock(crop.offset(0, -1, 0).offset(dx, 0, dz), BuiltinBlocks.FARMLAND_WET);
            }
        }
        assertEquals(10.0f, RandomTickSystem.cropGrowthSpeed(world, crop,
                BuiltinBlocks.WHEAT_STAGE0.identifier()), 0.0001f);
        assertEquals(3, (int) (25.0f / 10.0f) + 1, "the eight-ring band");
    }

    /**
     * The same-crop row half (lines 79-95): wheat at north/south/east/west
     * (the crop level — the ring's eight farmlands all stay) →
     * f = 1 + 3 + 8*0.75 = 10.0, halved (both axes carry the same crop)
     * = 5.0 — and the family match is the crop's OWN kind: a melon stem
     * beside pumpkin stems never halves.
     */
    @Test
    void theRowHalfAppliesToTheSameCropOnBothAxes() {
        BlockPosition crop = cropOver(BuiltinBlocks.FARMLAND_WET, 30, 6);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                world.setBlock(crop.offset(0, -1, 0).offset(dx, 0, dz), BuiltinBlocks.FARMLAND_WET);
            }
        }
        world.setBlock(crop.offset(0, 0, -1), BuiltinBlocks.WHEAT_STAGE0);
        world.setBlock(crop.offset(0, 0, 1), BuiltinBlocks.WHEAT_STAGE0);
        world.setBlock(crop.offset(-1, 0, 0), BuiltinBlocks.WHEAT_STAGE0);
        world.setBlock(crop.offset(1, 0, 0), BuiltinBlocks.WHEAT_STAGE0);
        assertEquals(5.0f, RandomTickSystem.cropGrowthSpeed(world, crop,
                BuiltinBlocks.WHEAT_STAGE0.identifier()), 0.0001f,
                "the full wet ring (10.0) halves under the both-axes cross");
        assertEquals(6, (int) (25.0f / 5.0f) + 1, "the row-half band");

        // The family distinctness: a melon stem's cross never halves over a
        // pumpkin stem (the reference's block == this consult) — the lone
        // wet centre (4.0) stays unhalved.
        BlockPosition melonStem = cropOver(BuiltinBlocks.FARMLAND_WET, 36, 6);
        world.setBlock(melonStem.offset(0, 0, -1),
                MelonBlocks.stemStage("pumpkin_stem", 0));
        world.setBlock(melonStem.offset(0, 0, 1),
                MelonBlocks.stemStage("pumpkin_stem", 0));
        world.setBlock(melonStem.offset(-1, 0, 0),
                MelonBlocks.stemStage("pumpkin_stem", 0));
        world.setBlock(melonStem.offset(1, 0, 0),
                MelonBlocks.stemStage("pumpkin_stem", 0));
        assertEquals(4.0f, RandomTickSystem.cropGrowthSpeed(world, melonStem,
                MelonBlocks.stemStage("melon_stem", 0).identifier()), 0.0001f,
                "a pumpkin cross never halves a melon stem");
    }

    /** The corner branch of the row half: one same crop at the NW corner
     *  (crop level, the ring intact) → f = 10.0 halved = 5.0 through the
     *  CORNER consult (the both-axes gate is false: the orthogonals are
     *  farmland, not wheat). */
    @Test
    void theRowHalfAppliesFromTheCornerBranch() {
        BlockPosition crop = cropOver(BuiltinBlocks.FARMLAND_WET, 42, 6);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                world.setBlock(crop.offset(0, -1, 0).offset(dx, 0, dz), BuiltinBlocks.FARMLAND_WET);
            }
        }
        world.setBlock(crop.offset(-1, 0, -1), BuiltinBlocks.WHEAT_STAGE0);
        assertEquals(5.0f, RandomTickSystem.cropGrowthSpeed(world, crop,
                BuiltinBlocks.WHEAT_STAGE0.identifier()), 0.0001f,
                "the corner consult halves where the both-axes gate is false");
        assertEquals(6, (int) (25.0f / 5.0f) + 1, "the corner-half band");
    }

    // ---------------------------------------------------------------
    // The cadences: the statistical bands, seeded and deterministic
    // ---------------------------------------------------------------

    /**
     * The wet-center crop grows at the 1-in-7 band (the reference's own
     * pace): the seeded mean over TRIALS drives of a stage-0 wheat. The
     * pre-audit engine's 1-in-3 sits far below the band's floor.
     */
    @Test
    void theWetCropGrowsAtTheReferenceBand() {
        BlockPosition crop = cropOver(BuiltinBlocks.FARMLAND_WET, 48, 6);
        long probes = 0;
        for (int trial = 0; trial < TRIALS; trial++) {
            world.setBlock(crop, BuiltinBlocks.WHEAT_STAGE0);
            int spent = 0;
            while (world.getBlock(crop).equals(BuiltinBlocks.WHEAT_STAGE0) && spent < 200) {
                ticks.randomTick(crop);
                spent++;
            }
            assertTrue(spent < 200, "the wet band terminates the trial");
            probes += spent;
        }
        assertTrue(probes >= TRIALS * 5 && probes <= TRIALS * 10,
                "the wet band's mean 7 (sum " + probes + " over " + TRIALS
                        + " trials; the pre-audit 1-in-3 sums ~" + TRIALS * 3 + ")");
        clearCrop(48, 6);
    }

    /** The dry-center crop grows at the 1-in-13 band. */
    @Test
    void theDryCropGrowsAtTheReferenceBand() {
        BlockPosition crop = cropOver(BuiltinBlocks.FARMLAND, 54, 6);
        long probes = 0;
        for (int trial = 0; trial < TRIALS; trial++) {
            world.setBlock(crop, BuiltinBlocks.WHEAT_STAGE0);
            int spent = 0;
            while (world.getBlock(crop).equals(BuiltinBlocks.WHEAT_STAGE0) && spent < 400) {
                ticks.randomTick(crop);
                spent++;
            }
            assertTrue(spent < 400, "the dry band terminates the trial");
            probes += spent;
        }
        assertTrue(probes >= TRIALS * 10 && probes <= TRIALS * 16,
                "the dry band's mean 13 (sum " + probes + " over " + TRIALS
                        + " trials; the pre-audit 1-in-6 sums ~" + TRIALS * 6 + ")");
        clearCrop(54, 6);
    }

    /**
     * THE STEM ROLL GATE (StemBlock.tick lines 57-82): a mature stem
     * attempts its fruit walk ONLY on a passing growth roll — the expected
     * first-fruit index is precomputed from the same seeded stream (one
     * nextInt(7) per drive while no fruit sits), and the observed index
     * must match it. The seat drives its OWN tick system (a fresh stream
     * — the shared instance's consumption order is seat-order-dependent).
     * The pre-audit engine attempted the walk on EVERY tick (index 1
     * always).
     */
    @Test
    void theMatureStemFruitsOnlyOnAPassingRoll() {
        BlockPosition stem = cropOver(BuiltinBlocks.FARMLAND_WET, 60, 6);
        world.setBlock(stem, MelonBlocks.stemStage("melon_stem", 7));
        // The four neighbour floors take the fruit (StemBlock lines 74-77:
        // air over farmland/dirt/grass).
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            world.setBlock(stem.offset(dir[0], -1, dir[1]), BuiltinBlocks.GRASS_BLOCK);
        }
        // The expected index: the first passing roll of the seeded band
        // (the same seed the seat's own tick system rolls with).
        Random expected = new Random(0x57EAL);
        int firstPass = 0;
        do {
            firstPass++;
        } while (expected.nextInt(7) != 0);
        assertTrue(firstPass > 1, "the chosen seed carries a failing first roll");
        RandomTickSystem stemTicks = new RandomTickSystem(world, new Random(0x57EAL));

        int observed = 0;
        while (observed < 400) {
            stemTicks.randomTick(stem);
            observed++;
            if (!world.getBlock(stem.offset(1, 0, 0)).equals(world.airType())
                    || !world.getBlock(stem.offset(-1, 0, 0)).equals(world.airType())
                    || !world.getBlock(stem.offset(0, 0, 1)).equals(world.airType())
                    || !world.getBlock(stem.offset(0, 0, -1)).equals(world.airType())) {
                break;
            }
        }
        assertEquals(firstPass, observed, "the fruit lands exactly on the first passing roll"
                + " (the pre-audit engine fruited on tick 1)");
        world.setBlock(stem, world.airType());
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (!world.getBlock(stem.offset(dir[0], 0, dir[1])).equals(world.airType())) {
                world.setBlock(stem.offset(dir[0], 0, dir[1]), world.airType());
            }
        }
    }

    /** The cane grows at the AGE-15 accumulator's 1-in-16 band. */
    @Test
    void theCaneGrowsAtTheAccumulatorBand() {
        BlockPosition cane = caneOverWetDirt(66, 6);
        long probes = 0;
        for (int trial = 0; trial < TRIALS; trial++) {
            world.setBlock(cane.offset(0, 1, 0), world.airType());
            int spent = 0;
            while (world.getBlock(cane.offset(0, 1, 0)).equals(world.airType())
                    && spent < 500) {
                ticks.randomTick(cane);
                spent++;
            }
            assertTrue(spent < 500, "the accumulator band terminates the trial");
            probes += spent;
        }
        assertTrue(probes >= TRIALS * 11 && probes <= TRIALS * 21,
                "the cane's mean 16 (sum " + probes + " over " + TRIALS
                        + " trials; the pre-audit 1-in-3 sums ~" + TRIALS * 3 + ")");
    }

    /**
     * THE SUPPORT SHORT-CIRCUIT (SugarCaneBlock.tick line 30): a stacked
     * cane over a base whose water left still GROWS (its below is a cane;
     * the water consult never runs for it), while the BASE's own tick pops
     * the column from under it — the water cell being diagonal to the cane,
     * the pop is the random tick's own law.
     */
    @Test
    void theStackedCaneGrowsWhileTheBasePops() {
        BlockPosition soil = new BlockPosition(72, 4, 6);
        BlockPosition base = soil.offset(0, 1, 0);
        BlockPosition top = base.offset(0, 1, 0);
        world.setBlock(soil, BuiltinBlocks.DIRT);
        world.setBlock(base, BuiltinBlocks.SUGAR_CANE);
        world.setBlock(top, BuiltinBlocks.SUGAR_CANE);
        // No water anywhere near the soil: the base's own consult fails.

        // The top grows (the short-circuit): drive until the band passes.
        int guard = 0;
        while (world.getBlock(top.offset(0, 1, 0)).equals(world.airType()) && guard < 600) {
            ticks.randomTick(top);
            guard++;
        }
        assertEquals(BuiltinBlocks.SUGAR_CANE, world.getBlock(top.offset(0, 1, 0)),
                "the stacked cane grows without the base's water (line 30's short-circuit)");

        // The base pops on its own tick: the block goes air (the item drop
        // rides the seam) — the pre-audit engine left the column standing.
        ticks.randomTick(base);
        assertEquals(BuiltinBlocks.AIR, world.getBlock(base),
                "the base's own consult pops it when the water left (canSurviveOrBreak)");
    }

    /** The popped cane's item lands (the seam's own law: the drop at the
     *  pop cell, the standard pickup delay). */
    @Test
    void theWaterLossPopDropsTheCaneItem() {
        BlockPosition soil = new BlockPosition(78, 4, 6);
        BlockPosition base = soil.offset(0, 1, 0);
        world.setBlock(soil, BuiltinBlocks.DIRT);
        world.setBlock(base, BuiltinBlocks.SUGAR_CANE);
        long before = popItems.size();
        ticks.randomTick(base);
        assertEquals(BuiltinBlocks.AIR, world.getBlock(base), "the pop");
        assertEquals(before + 1, popItems.size(),
                "the cane's item drop landed (the pop seam's own body)");
    }

    /** The cactus grows at the AGE-15 accumulator's 1-in-16 band. */
    @Test
    void theCactusGrowsAtTheAccumulatorBand() {
        BlockPosition cactus = cactusOverSand(84, 6);
        long probes = 0;
        for (int trial = 0; trial < TRIALS; trial++) {
            world.setBlock(cactus.offset(0, 1, 0), world.airType());
            int spent = 0;
            while (world.getBlock(cactus.offset(0, 1, 0)).equals(world.airType())
                    && spent < 500) {
                ticks.randomTick(cactus);
                spent++;
            }
            assertTrue(spent < 500, "the accumulator band terminates the trial");
            probes += spent;
        }
        assertTrue(probes >= TRIALS * 11 && probes <= TRIALS * 21,
                "the cactus's mean 16 (sum " + probes + " over " + TRIALS
                        + " trials; the pre-audit 1-in-3 sums ~" + TRIALS * 3 + ")");
    }

    /**
     * THE NEW-CELL CONSULT (CactusBlock.tick line 43 + canSurvive lines
     * 85-94): a solid beside the CURRENT top does not block the growth
     * (the reference consults the NEW cell's horizontals, not the top's —
     * the pre-audit engine read the top's and never grew this scene), and
     * a solid beside the NEW cell places the block then pops it with its
     * item, same tick.
     */
    @Test
    void theCactusConsultsTheNewCellNotTheTop() {
        // Scene one: a stone beside the current top, the new cell clear.
        BlockPosition cactus = cactusOverSand(90, 6);
        world.setBlock(cactus.offset(1, 0, 0), BuiltinBlocks.STONE);
        int guard = 0;
        while (world.getBlock(cactus.offset(0, 1, 0)).equals(world.airType()) && guard < 600) {
            ticks.randomTick(cactus);
            guard++;
        }
        assertEquals(BuiltinBlocks.CACTUS, world.getBlock(cactus.offset(0, 1, 0)),
                "the growth consults the NEW cell — the top's own solid neighbour"
                        + " never blocked it in the reference");

        // Scene two: a stone beside the NEW cell (one higher): the
        // placement pops with its item, same tick (line 43's walk).
        BlockPosition twoCactus = cactusOverSand(96, 6);
        world.setBlock(twoCactus.offset(1, 0, 0), BuiltinBlocks.CACTUS);
        world.setBlock(twoCactus.offset(1, 1, 0), BuiltinBlocks.STONE);
        long before = popItems.size();
        int guard2 = 0;
        while (world.getBlock(twoCactus.offset(0, 1, 0)).equals(world.airType())
                && popItems.size() == before
                && guard2 < 600) {
            ticks.randomTick(twoCactus);
            guard2++;
        }
        assertEquals(BuiltinBlocks.AIR, world.getBlock(twoCactus.offset(0, 1, 0)),
                "the new cell pops beside its own solid (the same-tick pop)");
        assertEquals(before + 1, popItems.size(),
                "the popped new cell drops its item (breakBlock's own drop)");
    }

    // ---------------------------------------------------------------
    // The scene helpers
    // ---------------------------------------------------------------

    /** A stage-0 wheat over the given soil at (x, 5, z); the crop sits at
     *  (x, 6, z) under the open sky (the light gate's own scene). */
    private BlockPosition cropOver(BlockType soil, int x, int z) {
        BlockPosition soilAt = new BlockPosition(x, 5, z);
        BlockPosition cropAt = soilAt.offset(0, 1, 0);
        world.setBlock(soilAt, soil);
        world.setBlock(cropAt, BuiltinBlocks.WHEAT_STAGE0);
        return cropAt;
    }

    /** Clears the two-cell crop scene (the shared world's hygiene). */
    private void clearCrop(int x, int z) {
        world.setBlock(new BlockPosition(x, 6, z), world.airType());
    }

    /** A base cane on dirt with water beside the soil (the wet scene). */
    private BlockPosition caneOverWetDirt(int x, int z) {
        BlockPosition soil = new BlockPosition(x, 4, z);
        BlockPosition cane = soil.offset(0, 1, 0);
        world.setBlock(soil, BuiltinBlocks.DIRT);
        world.setBlock(soil.offset(1, 0, 0),
                FluidBlocks.sourceOf(FluidBlocks.Kind.WATER));
        world.setBlock(cane, BuiltinBlocks.SUGAR_CANE);
        return cane;
    }

    /** A base cactus on sand (the desert scene). */
    private BlockPosition cactusOverSand(int x, int z) {
        BlockPosition sand = new BlockPosition(x, 4, z);
        BlockPosition cactus = sand.offset(0, 1, 0);
        world.setBlock(sand, BuiltinBlocks.SAND);
        world.setBlock(cactus, BuiltinBlocks.CACTUS);
        return cactus;
    }
}
