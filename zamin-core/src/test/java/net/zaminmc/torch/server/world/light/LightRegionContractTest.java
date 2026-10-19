package net.zaminmc.torch.server.world.light;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.world.EngineChunk;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE LIGHTING WORKLOAD'S §22 GATE (the same three-part bar the pathfinding
 * gate answered): the immutable-input contract, the behavioral equivalence,
 * and the before/after measurements — over the INITIAL per-chunk compute
 * (the recon's named offload candidate: the fresh chunk's clear + sky fill
 * + emitter radiate + border reconcile), the random-tick picks' own
 * compute-then-apply shape.
 *
 * <p>THE CONTRACT: {@link LightRegion} — the fresh chunk's material grid
 * plus every loaded 3x3 neighbor's materials and current light, captured
 * on the owning lane per SECTION (packed filter/emission bytes; wire-packed
 * light clones); the off-lane compute reads ONLY it. THE EQUIVALENCE: the
 * SAME static algorithms run over the live routing (inside the install
 * hook, the production path) and over the captured region (the detached
 * {@link InitialLightCompute} + the owner-side
 * {@code LightEngine.applyInitial}); the twin worlds must agree cell-for-
 * cell across the whole 3x3 — every cell's block light AND sky light, plus
 * every light section's materialization (the wire's own visibility) — and
 * the relight queues must agree in first-marked ORDER. THE MEASUREMENTS:
 * the live compute vs the capture vs the detached compute vs the apply,
 * logged for the ledger's verdict — the numbers decide whether the offload
 * lane is worth wiring, single-chunk and in the pregen's batch shape.</p>
 */
class LightRegionContractTest {

    // ------------------------------------------------ the twin-world harness

    /** The fresh chunk's position: the center of the 3x3. */
    private static final ChunkPosition FRESH = new ChunkPosition(0, 0);

    private EngineWorld newWorld() {
        var registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        return new EngineWorld("light-gate", registry, new FlatWorldGenerator(registry, 4),
                Thread.currentThread());
    }

    /** The fresh chunk, generated and sculpted identically in both worlds. */
    private EngineChunk newFreshChunk(EngineWorld world, boolean worked) {
        EngineChunk chunk = new EngineChunk(FRESH, world.airType());
        world.generator().generate(chunk);
        if (worked) {
            // The east-face cliff: a 16-tall solid wall along x 14..15 — the
            // lateral detour stress (the only paths around it run above y 20
            // or below y 5) and the sky-shade source for its own columns.
            for (int x = 14; x <= 15; x++) {
                for (int y = 5; y <= 20; y++) {
                    for (int z = 0; z < 16; z++) {
                        chunk.setBlock(x, y, z, BuiltinBlocks.STONE);
                    }
                }
            }
            // The west-edge emitter cluster: the outflow into the west
            // neighbor (the torch at x 2 reaches 11 cells past the border)
            // and the corner consult (the torch at (0,6,0) reads and lights
            // the diagonal (-1,-1)'s corner column).
            chunk.setBlock(2, 6, 2, BuiltinBlocks.TORCH);
            chunk.setBlock(0, 6, 0, BuiltinBlocks.TORCH);
            // The mid-air overhang: sky shade beneath it, lateral BFS around it.
            chunk.setBlock(8, 12, 8, BuiltinBlocks.STONE);
        }
        return chunk;
    }

    /**
     * Installs the 8 neighbors around the center in a fixed row-major order —
     * the production path: the install hook computes each chunk's initial
     * light inside {@code installGenerated} (before publication).
     */
    private void setupNeighborsLive(EngineWorld world, boolean worked) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                world.getOrGenerate(new ChunkPosition(dx, dz));
            }
        }
        sculptNeighbors(world, worked);
    }

    /**
     * The same setup, the manual sequence: create, generate, the hook's own
     * body (computeInitial on the still-detached chunk), then install —
     * state-identical to the hook firing inside installGenerated (the chunk
     * is out of the map during the compute either way). Used by the OFF
     * world, whose fresh chunk must install WITHOUT a light hook.
     */
    private void setupNeighborsOff(EngineWorld world, LightEngine engine, boolean worked) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                ChunkPosition at = new ChunkPosition(dx, dz);
                EngineChunk chunk = new EngineChunk(at, world.airType());
                world.generator().generate(chunk);
                engine.computeInitial(chunk);
                world.installGenerated(at, chunk);
            }
        }
        sculptNeighbors(world, worked);
    }

    /**
     * Sculpts the neighbor side (the incremental path — identical in both
     * worlds): the east neighbor's torch two deep (the westward inflow),
     * the near-corner torch on the strip adjacent to the diagonal (the
     * diagonal consult from the neighbor side), and the south neighbor's
     * mid-terrain torch (the northward inflow).
     */
    private void sculptNeighbors(EngineWorld world, boolean worked) {
        if (worked) {
            world.setBlock(new BlockPosition(18, 6, 8), BuiltinBlocks.TORCH);   // east, 2-deep
            world.setBlock(new BlockPosition(16, 6, 15), BuiltinBlocks.TORCH);  // the strip corner
            world.setBlock(new BlockPosition(8, 6, -2), BuiltinBlocks.TORCH);   // south, 2-deep
        }
    }

    /** Every cell's block light and sky light across the whole 3x3, plus each light section's materialization. */
    private void assertStatesAgree(EngineWorld live, EngineWorld off) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPosition at = new ChunkPosition(dx, dz);
                EngineChunk liveChunk = live.peek(at);
                EngineChunk offChunk = off.peek(at);
                assertTrue(liveChunk != null && offChunk != null, "chunk " + at + " installed in both worlds");
                int baseX = at.minBlockX();
                int baseZ = at.minBlockZ();
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = 0; y <= 255; y++) {
                            final int wx = baseX + lx;
                            final int wy = y;
                            final int wz = baseZ + lz;
                            assertEquals(
                                    live.blockLightAt(new BlockPosition(wx, wy, wz)),
                                    off.blockLightAt(new BlockPosition(wx, wy, wz)),
                                    () -> "block light at " + wx + "," + wy + "," + wz);
                            assertEquals(
                                    live.skyLightAt(new BlockPosition(wx, wy, wz)),
                                    off.skyLightAt(new BlockPosition(wx, wy, wz)),
                                    () -> "sky light at " + wx + "," + wy + "," + wz);
                        }
                    }
                }
                for (int s = 0; s < 16; s++) {
                    final int sectionY = s;
                    assertEquals(liveChunk.lightSection(sectionY) != null,
                            offChunk.lightSection(sectionY) != null,
                            () -> "light section " + sectionY + " materialization of chunk " + at);
                }
            }
        }
    }

    /** Drains both engines' relight queues and compares them in first-marked order. */
    private void assertRelightsAgree(LightEngine liveEngine, LightEngine offEngine) {
        List<ChunkPosition> liveMarks = new ArrayList<>();
        List<ChunkPosition> offMarks = new ArrayList<>();
        liveEngine.flushRelight(liveMarks::add);
        offEngine.flushRelight(offMarks::add);
        assertEquals(liveMarks, offMarks, "the relight columns agree in first-marked order");
    }

    // ------------------------------------------------ the equivalence (the gate's second part)

    @Test
    void theDetachedComputeMatchesTheLiveComputeCellForCell() {
        for (boolean worked : new boolean[] {false, true}) {
            // ---- the LIVE world: the production wiring (the install hook computes)
            EngineWorld live = newWorld();
            LightEngine liveEngine = new LightEngine(live);
            live.addChangeListener(liveEngine);
            live.addChunkLoadListener(liveEngine);
            setupNeighborsLive(live, worked);
            liveEngine.flushRelight(m -> { }); // drain the setup's marks
            EngineChunk liveFresh = newFreshChunk(live, worked);
            live.installGenerated(FRESH, liveFresh); // the hook computes inside

            // ---- the OFF world: the compute-then-apply split (no install hook)
            EngineWorld off = newWorld();
            LightEngine offEngine = new LightEngine(off);
            off.addChangeListener(offEngine);
            setupNeighborsOff(off, offEngine, worked);
            offEngine.flushRelight(m -> { }); // drain the setup's marks
            EngineChunk offFresh = newFreshChunk(off, worked);
            LightRegion region = LightRegion.capture(off, offFresh);
            InitialLightResult result = InitialLightCompute.compute(region);
            offEngine.applyInitial(result, offFresh);
            off.installGenerated(FRESH, offFresh);

            // ---- the equivalence: whole-3x3 state, section materialization, relight order
            assertStatesAgree(live, off);
            assertRelightsAgree(liveEngine, offEngine);

            // ---- non-trivial: the scene actually exercised the paths
            if (worked) {
                // the west-edge emitter's outflow reaches deep into the west neighbor
                assertTrue(live.blockLightAt(new BlockPosition(-1, 6, 2)) > 0,
                        "the fresh torch lights the west neighbor's strip");
                assertTrue(live.blockLightAt(new BlockPosition(-8, 6, 2)) > 0,
                        "the fresh torch reaches 8 cells deep into the west neighbor");
                // the corner consult: the diagonal (-1,-1)'s corner column lit by (0,6,0)
                assertTrue(live.blockLightAt(new BlockPosition(-1, 6, -1)) > 0,
                        "the corner torch lights the diagonal's corner column");
                // the east-neighbor inflow reaches the fresh chunk's west half,
                // blocked by the cliff at x 14..15 (the detour must go around)
                assertTrue(live.blockLightAt(new BlockPosition(13, 6, 8)) > 0,
                        "the fresh chunk's west half holds light");
                // the overhang's own column is shaded but laterally re-lit
                assertTrue(live.skyLightAt(new BlockPosition(8, 11, 8)) == 14,
                        "the overhang's column is shaded to 14 (fed laterally)");
                // the cliff's own columns: dark underground-style beneath y 5
                assertEquals(0, live.skyLightAt(new BlockPosition(14, 6, 8)),
                        "the cliff column below y 5 start holds no direct sky");
            }
            assertEquals(8, countCapturedNeighbors(off),
                    "the captured region holds all eight neighbors (worked=" + worked + ")");
        }
    }

    private int countCapturedNeighbors(EngineWorld off) {
        EngineChunk fresh = new EngineChunk(FRESH, off.airType());
        LightRegion region = LightRegion.capture(off, fresh);
        return region.capturedNeighborCount();
    }

    // ------------------------------------------------ the region's own contract

    @Test
    void theRegionServesMaterialsInsideAndDefaultsOutside() {
        EngineWorld world = newWorld();
        LightEngine engine = new LightEngine(world);
        world.addChangeListener(engine);
        world.addChunkLoadListener(engine);
        setupNeighborsLive(world, true);
        EngineChunk fresh = newFreshChunk(world, true);
        LightRegion region = LightRegion.capture(world, fresh);

        // The fresh chunk's sculpted cells read back through the packed grid
        assertEquals(15, region.materialAt(15, 6, 8) & 0xF,
                "the cliff's stone filters 15");
        assertEquals(14, (region.materialAt(2, 6, 2) >> 4) & 0xF,
                "the torch emits 14");
        // The neighbor's sculpted cells (captured through the world)
        assertEquals(14, (region.materialAt(18, 6, 8) >> 4) & 0xF,
                "the east neighbor's torch emits 14");
        assertEquals(15, region.materialAt(18, 4, 8) & 0xF,
                "the east neighbor's grass top filters 15");
        // Outside the 3x3: the unloaded answer (air)
        assertEquals(0, region.materialAt(100, 6, 8) & 0xF,
                "outside the 3x3 answers air (filter 0)");
        assertEquals(0, (region.materialAt(100, 6, 8) >> 4) & 0xF,
                "outside the 3x3 answers air (emission 0)");
        // The working span: the worked chunk's highest relevant section
        // (the cliff tops at y 20 -> section 1 -> maxY (1<<4)+15+1 = 32)
        assertEquals(32, region.maxY(), "the fresh chunk's light-relevant span");
        assertEquals(8, region.capturedNeighborCount(), "all eight neighbors captured");
    }

    // ------------------------------------------------ the measurement gate

    /**
     * THE MEASUREMENTS (the §22 gate's before/after bar, logged for the
     * ledger): the live compute vs the capture vs the detached compute vs
     * the apply, over the flat shape (the common case) and the worked shape
     * (the cliff/emitter/overhang stress). The numbers decide the offload
     * verdict — the lane is worth wiring only where the capture + the
     * parallel compute + the apply beat the on-lane compute, per chunk and
     * in the pregen's batch shape.
     */
    @Test
    void theMeasurementHarnessRecordsTheCosts() {
        for (boolean worked : new boolean[] {false, true}) {
            EngineWorld world = newWorld();
            LightEngine engine = new LightEngine(world);
            world.addChangeListener(engine);
            world.addChunkLoadListener(engine);
            setupNeighborsLive(world, worked);

            // warmup both paths (the JIT's own band)
            for (int i = 0; i < 15; i++) {
                EngineChunk warm = newFreshChunk(world, worked);
                engine.computeInitial(warm);
                engine.flushRelight(m -> { });
                LightRegion warmRegion = LightRegion.capture(world, warm);
                InitialLightResult warmResult = InitialLightCompute.compute(warmRegion);
                engine.applyInitial(warmResult, new EngineChunk(FRESH, world.airType()));
            }

            final int runs = 40;
            long liveNanos = timedMedian(runs, () -> {
                EngineChunk chunk = newFreshChunk(world, worked);
                engine.computeInitial(chunk);
                engine.flushRelight(m -> { });
            });
            EngineChunk measured = newFreshChunk(world, worked);
            long captureNanos = timedMedian(runs, () -> LightRegion.capture(world, measured));
            LightRegion region = LightRegion.capture(world, measured);
            long computeNanos = timedMedian(runs, () -> InitialLightCompute.compute(region));
            InitialLightResult result = InitialLightCompute.compute(region);
            long applyNanos = timedMedian(runs, () ->
                    engine.applyInitial(result, new EngineChunk(FRESH, world.airType())));

            long offloadTotal = captureNanos + computeNanos + applyNanos;
            System.out.printf(
                    "[light-region] worked=%b: live %,d ns | capture %,d ns | compute %,d ns"
                            + " | apply %,d ns | offload total %,d ns (x%.2f)%n",
                    worked, liveNanos, captureNanos, computeNanos, applyNanos,
                    offloadTotal, offloadTotal / (double) liveNanos);
            System.out.printf(
                    "[light-region] worked=%b result: %s%n", worked, result.summary());
            // The pregen's batch shape (81 chunks, 4 workers): the computes
            // parallelize; the capture and the apply stay on the caller lane.
            System.out.printf(
                    "[light-region] worked=%b batch(81, P=4): live-serial %,d us"
                            + " | offload wall %,d us (capture 81x + compute 81/4 + apply 81x)%n",
                    worked,
                    81 * liveNanos / 1000,
                    (81 * captureNanos + (81 * computeNanos) / 4 + 81 * applyNanos) / 1000);

            // The sanity bar: the equivalence still holds after the timing runs
            EngineChunk liveFresh = newFreshChunk(world, worked);
            engine.computeInitial(liveFresh);
            engine.flushRelight(m -> { });
            EngineChunk offFresh = newFreshChunk(world, worked);
            LightRegion freshRegion = LightRegion.capture(world, offFresh);
            engine.applyInitial(InitialLightCompute.compute(freshRegion), offFresh);
            assertEquals(liveFresh.blockLight(2, 6, 2), offFresh.blockLight(2, 6, 2),
                    "the torch cell agrees after the timing runs");
            assertEquals(liveFresh.skyLight(8, 11, 8), offFresh.skyLight(8, 11, 8),
                    "the overhang column agrees after the timing runs");
        }
    }

    /** The median of N timed runs (the outlier-resistant central cost). */
    private static long timedMedian(int runs, Runnable action) {
        long[] samples = new long[runs];
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            action.run();
            samples[i] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(samples);
        return samples[runs / 2];
    }
}
