package net.zaminmc.torch.server.world.light;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.concurrent.DomainWorkerPool;
import net.zaminmc.torch.server.world.EngineChunk;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import net.zaminmc.torch.server.world.WorldGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE LIGHTING BATCH LANE'S OWN GATE (§22's lighting batch shape — the
 * staleness protocol's evidence): the wave-batched pregen
 * ({@link LightBatchPregen}) against the production sequential walk, over
 * twin worlds built identically.
 *
 * <p>THE CONTRACTS PROVEN HERE:
 * <ol>
 * <li><b>Flat equivalence</b> — on the flat world (the boot default's own
 * shape: no shaded air, no emitters) the batch and the sequential walks
 * agree cell-for-cell, section materialization included, and their relight
 * queues agree as SETS (the first-marked ORDER differs by construction —
 * the wave order reorders the installs; the order only paces the wire's
 * column resends, deduplicated by the queue's own set semantics).</li>
 * <li><b>Worked divergence, measured and pinned</b> — on the worked scene
 * (the border-stress sculptures at the wave/column-major disagreement
 * pairs) the two walks CAN disagree wherever their install orders disagree
 * on which neighbor was published at a capture: the absent side answers the
 * phantom defaults (air, sky 15, block 0). The step-4 reconcile at each
 * later install recovers the real inflow (the re-derive darkens
 * phantom-fed cells; the strip seeds and the recorded neighbor writes
 * restore the missed light), so the measured residue is ONLY the sequential
 * walk's own phantom artifact — the probe's forensic found the mechanism:
 * a fresh chunk's step-4 fallback can read a LOADED neighbor's strip cell
 * whose outward face touches never-loaded space, and the absent side's
 * default (sky 15) seeds real light from nothing. The batch's wave order
 * never performed that read, so the batch is DARKER exactly there — the
 * physically correct answer. The pinned contract is the BRACKET (durable):
 * the phantom defaults' asymmetry (sky 15 is the channel's max, block 0 its
 * min) bounds every divergence — batched sky &le; sequential sky and
 * batched block &ge; sequential block, cell for cell — so the batch never
 * loses real light and never invents any; the flat shape (the boot
 * default's own terrain) diverges nowhere.</li>
 * <li><b>Determinism</b> — two batched runs over identically built worlds
 * agree cell-for-cell AND in relight first-marked order (the wave
 * decomposition, the within-wave column-major order and the per-wave
 * dispatch order are all fixed).</li>
 * <li><b>The lane's evidence</b> — every install in the batched run consumed
 * a pool-computed result (the engine's own counter); the sequential run
 * consumed none.</li>
 * </ol>
 *
 * <p>THE WORKED SCENE (the disagreement pairs of the radius-2 area, five
 * chunks a side): the pair orders that DISAGREE between wave order
 * (0,0)→(0,1)→(1,0)→(1,1) and the sequential column-major rank include the
 * cardinal pair (1,1)/(2,1) — batch publishes (2,1) first (wave (0,1)),
 * sequential publishes (1,1) first — and the diagonal pairs (2,0)/(1,1) and
 * (1,0)/(2,1). The sculptures aim light and shade exactly there: a torch on
 * (1,1)'s east border (its cone crosses into (2,1)'s west strip), a torch
 * at (2,0)'s corner cell (the diagonal consult into (1,1)), a cliff on
 * (1,0)'s east face (the shadow (2,0) must cast back into (1,0)'s border
 * strip), and a covered trench through (2,1) (shaded cells whose only
 * lateral feed would be the phantom sky of an absent neighbor).</p>
 */
class LightBatchEquivalenceTest {

    /** The pregen area's radius: a 5x5 chunk square, the same shape as a view-distance-2 boot. */
    private static final int RADIUS = 2;

    // ------------------------------------------------ the harness

    private EngineWorld newFlatWorld() {
        var registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        return new EngineWorld("light-batch-flat", registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
    }

    private EngineWorld newWorkedWorld(String name) {
        var registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        return new EngineWorld(name, registry, new WorkedGenerator(registry), Thread.currentThread());
    }

    /**
     * The sequential production walk (the un-gated boot path): column-major
     * getOrGenerate over the square — identical to
     * {@code EngineServer.pregenerateSpawnArea}'s default loop.
     */
    private void pregenerateSequential(EngineWorld world, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                world.getOrGenerate(new ChunkPosition(dx, dz));
            }
        }
    }

    /** The batch lane with its own pool (the gated boot path's own shape). */
    private void pregenerateBatched(EngineWorld world, LightEngine light, int radius) {
        DomainWorkerPool pool = new DomainWorkerPool();
        try {
            LightBatchPregen.pregenerate(world, light, new ChunkPosition(0, 0), radius, pool);
        } finally {
            pool.shutdown();
        }
    }

    /** One divergent cell record (world position, channel, sequential value, batched value). */
    private record Divergence(int x, int y, int z, String channel, int sequential, int batched) {
    }

    /**
     * Walks the whole area in both worlds and collects every divergent cell
     * (block light and sky light, both channels) plus every light-section
     * materialization difference.
     */
    private List<Divergence> collectDivergences(EngineWorld sequential, EngineWorld batched,
                                                List<String> materializationDiffs) {
        List<Divergence> divergences = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                ChunkPosition at = new ChunkPosition(dx, dz);
                EngineChunk seq = sequential.peek(at);
                EngineChunk bat = batched.peek(at);
                assertTrue(seq != null && bat != null, "chunk " + at + " installed in both worlds");
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = 0; y <= 255; y++) {
                            int x = at.minBlockX() + lx;
                            int z = at.minBlockZ() + lz;
                            int seqBlock = sequential.blockLightAt(new BlockPosition(x, y, z));
                            int batBlock = batched.blockLightAt(new BlockPosition(x, y, z));
                            if (seqBlock != batBlock) {
                                divergences.add(new Divergence(x, y, z, "block", seqBlock, batBlock));
                            }
                            int seqSky = sequential.skyLightAt(new BlockPosition(x, y, z));
                            int batSky = batched.skyLightAt(new BlockPosition(x, y, z));
                            if (seqSky != batSky) {
                                divergences.add(new Divergence(x, y, z, "sky", seqSky, batSky));
                            }
                        }
                    }
                }
                for (int s = 0; s < 16; s++) {
                    if ((seq.lightSection(s) != null) != (bat.lightSection(s) != null)) {
                        materializationDiffs.add("chunk " + at + " section " + s);
                    }
                }
            }
        }
        return divergences;
    }

    private Set<ChunkPosition> drainRelight(LightEngine engine) {
        Set<ChunkPosition> marks = new HashSet<>();
        engine.flushRelight(marks::add);
        return marks;
    }

    // ------------------------------------------------ the contracts

    @Test
    void theFlatBatchMatchesTheSequentialWalkExactly() {
        EngineWorld sequential = newFlatWorld();
        LightEngine sequentialLight = new LightEngine(sequential);
        sequential.addChangeListener(sequentialLight);
        sequential.addChunkLoadListener(sequentialLight); // the production wiring registers both
        pregenerateSequential(sequential, RADIUS);

        EngineWorld batched = newFlatWorld();
        LightEngine batchedLight = new LightEngine(batched);
        batched.addChangeListener(batchedLight);
        batched.addChunkLoadListener(batchedLight);
        pregenerateBatched(batched, batchedLight, RADIUS);

        List<String> materializationDiffs = new ArrayList<>();
        List<Divergence> divergences = collectDivergences(sequential, batched, materializationDiffs);
        assertEquals(List.of(), divergences, "the flat batch agrees with the sequential walk cell-for-cell");
        assertEquals(List.of(), materializationDiffs, "the flat batch agrees on every light section's materialization");
        assertEquals(drainRelight(sequentialLight), drainRelight(batchedLight),
                "the flat batch's relight columns agree as sets (the order is the wave's own, documented)");
        assertEquals(25, batchedLight.batchedInitialComputes(),
                "every batched install consumed a pool-computed result");
        assertEquals(0, sequentialLight.batchedInitialComputes(),
                "the sequential walk consumed none");
    }

    @Test
    void theWorkedDivergenceIsMeasuredAndPinned() {
        EngineWorld sequential = newWorkedWorld("light-batch-worked-seq");
        LightEngine sequentialLight = new LightEngine(sequential);
        sequential.addChangeListener(sequentialLight);
        sequential.addChunkLoadListener(sequentialLight);
        pregenerateSequential(sequential, RADIUS);

        EngineWorld batched = newWorkedWorld("light-batch-worked-bat");
        LightEngine batchedLight = new LightEngine(batched);
        batched.addChangeListener(batchedLight);
        batched.addChunkLoadListener(batchedLight);
        pregenerateBatched(batched, batchedLight, RADIUS);

        List<String> materializationDiffs = new ArrayList<>();
        List<Divergence> divergences = collectDivergences(sequential, batched, materializationDiffs);

        // THE MEASUREMENT (logged for the ledger, like the gate's own numbers)
        int brighter = 0;
        int darker = 0;
        int bracketViolations = 0;
        for (Divergence d : divergences) {
            if (d.batched() > d.sequential()) {
                brighter++;
            } else {
                darker++;
            }
            // THE BRACKET (the phantom defaults' own asymmetry — durable): the
            // absent side answers sky 15 (the channel's MAXIMUM) and block 0
            // (the channel's MINIMUM), so wherever the two walks' install
            // orders disagreed on an absent neighbor, the walk that saw the
            // phantom can only hold sky >= and block <= the walk that saw the
            // real (or recovered) state. The batch therefore never exceeds the
            // sequential on sky and never falls below it on block — the
            // divergence is exactly the sequential's own phantom residue,
            // never a real-light loss by the batch.
            if (("sky".equals(d.channel()) && d.batched() > d.sequential())
                    || ("block".equals(d.channel()) && d.batched() < d.sequential())) {
                bracketViolations++;
            }
        }
        Set<ChunkPosition> seqMarks = drainRelight(sequentialLight);
        Set<ChunkPosition> batMarks = drainRelight(batchedLight);
        Set<ChunkPosition> seqOnly = new HashSet<>(seqMarks);
        seqOnly.removeAll(batMarks);
        Set<ChunkPosition> batOnly = new HashSet<>(batMarks);
        batOnly.removeAll(seqMarks);
        System.out.printf("[light-batch] worked 5x5: %d divergent cells (%d batched-brighter, %d batched-darker)"
                        + ", %d materialization diffs, relight seq-only %s, batch-only %s%n",
                divergences.size(), brighter, darker, materializationDiffs.size(), seqOnly, batOnly);
        for (Divergence d : divergences) {
            System.out.printf("[light-batch]   %s at %d,%d,%d: sequential %d, batched %d%n",
                    d.channel(), d.x(), d.y(), d.z(), d.sequential(), d.batched());
        }

        // THE PINNED CONTRACT — the measured evidence: the flat shape diverges
        // NOWHERE; the worked shape's whole residue is the sequential walk's
        // OWN phantom class (the probe's forensic: the loaded-between pattern —
        // a fresh chunk's step-4 fallback reads a LOADED neighbor's strip cell
        // whose outward face touches never-loaded space, the absent side
        // answering sky 15 — and seeds real light from nothing; the batch's
        // wave order simply never performed that read). The bracket above is
        // the durable bound; the count is this scene's measured residue.
        assertEquals(0, bracketViolations,
                "the divergence is the sequential's phantom residue only: batched sky <= sequential sky"
                        + " and batched block >= sequential block, everywhere");
        assertEquals(List.of(), materializationDiffs,
                "the worked batch agrees on every light section's materialization");
        assertTrue(divergences.size() <= 256,
                "the worked residue stays inside the scene's measured phantom band (210 measured)");
        // The relight sets: both walks' marks sit inside the pregen area (writes
        // only land in loaded chunks), and the DIFFERENCE is the phantom-write
        // class — the sequential's phantom writes mark columns the batch never
        // touches, and the two walks' step-4 re-derives mark each other's
        // columns through different computes (the order difference, benign at
        // pregen: no view was ever sent, so every marked column's first packet
        // already carries its final light). Printed, not asserted — the flat
        // case's set equality above is the pinned contract.
        Set<ChunkPosition> area = new HashSet<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                area.add(new ChunkPosition(dx, dz));
            }
        }
        assertTrue(area.containsAll(seqMarks) && area.containsAll(batMarks),
                "both walks' relight marks sit inside the pregen area");
        // The scene's light actually flowed (non-trivial): the (1,1) torch's
        // cone crosses into (2,1)'s west strip in BOTH walks.
        assertTrue(sequential.blockLightAt(new BlockPosition(33, 6, 24)) > 0
                        && batched.blockLightAt(new BlockPosition(33, 6, 24)) > 0,
                "the border torch lights the neighbor's strip in both walks");
        // The cliff's shadow reached (1,0)'s own border strip in both walks.
        assertEquals(0, sequential.skyLightAt(new BlockPosition(30, 6, 8)));
        assertEquals(0, batched.skyLightAt(new BlockPosition(30, 6, 8)));
        // The lane's evidence: all twenty-five installs consumed pool results.
        assertEquals(25, batchedLight.batchedInitialComputes());
    }

    @Test
    void twoBatchedRunsAreIdenticalIncludingRelightOrder() {
        EngineWorld first = newWorkedWorld("light-batch-det-1");
        LightEngine firstLight = new LightEngine(first);
        first.addChangeListener(firstLight);
        first.addChunkLoadListener(firstLight);
        pregenerateBatched(first, firstLight, RADIUS);

        EngineWorld second = newWorkedWorld("light-batch-det-2");
        LightEngine secondLight = new LightEngine(second);
        second.addChangeListener(secondLight);
        second.addChunkLoadListener(secondLight);
        pregenerateBatched(second, secondLight, RADIUS);

        List<String> materializationDiffs = new ArrayList<>();
        List<Divergence> divergences = collectDivergences(first, second, materializationDiffs);
        assertEquals(List.of(), divergences, "two batched runs agree cell-for-cell");
        assertEquals(List.of(), materializationDiffs, "two batched runs agree on materialization");

        List<ChunkPosition> firstMarks = new ArrayList<>();
        List<ChunkPosition> secondMarks = new ArrayList<>();
        firstLight.flushRelight(firstMarks::add);
        secondLight.flushRelight(secondMarks::add);
        assertEquals(firstMarks, secondMarks, "two batched runs agree in relight first-marked order");
    }

    // ------------------------------------------------ the worked scene's generator

    /**
     * The flat generator plus the border-stress sculptures (deterministic per
     * chunk position — the twin worlds rebuild identically):
     * <ul>
     * <li>(1,1): a torch on the east border (local 14,6,8) — the cone that
     * crosses into (2,1)'s west strip; the pair whose publish orders
     * disagree cardinally.</li>
     * <li>(2,0): a torch at the corner cell (local 0,6,15) — the diagonal
     * consult into (1,1), the pair whose orders disagree diagonally.</li>
     * <li>(1,0): a 16-tall cliff on the east face (local x 14..15) — the
     * shadow the later-published neighbor casts back.</li>
     * <li>(2,1): a covered trench (local x 1..15, y 2..3, all z) — shaded
     * cells whose only lateral feed from an absent west neighbor would be
     * the phantom sky.</li>
     * </ul>
     */
    private static final class WorkedGenerator implements WorldGenerator {

        private final FlatWorldGenerator flat;

        WorkedGenerator(BlockRegistry registry) {
            this.flat = new FlatWorldGenerator(registry, 4);
        }

        @Override
        public void generate(EngineChunk chunk) {
            flat.generate(chunk);
            int cx = chunk.position().x();
            int cz = chunk.position().z();
            if (cx == 1 && cz == 1) {
                chunk.setBlock(14, 6, 8, BuiltinBlocks.TORCH);
            } else if (cx == 2 && cz == 0) {
                chunk.setBlock(0, 6, 15, BuiltinBlocks.TORCH);
            } else if (cx == 1 && cz == 0) {
                for (int x = 14; x <= 15; x++) {
                    for (int y = 5; y <= 20; y++) {
                        for (int z = 0; z < 16; z++) {
                            chunk.setBlock(x, y, z, BuiltinBlocks.STONE);
                        }
                    }
                }
            } else if (cx == 2 && cz == 1) {
                for (int x = 1; x <= 15; x++) {
                    for (int z = 0; z < 16; z++) {
                        chunk.setBlock(x, 2, z, BuiltinBlocks.AIR);
                        chunk.setBlock(x, 3, z, BuiltinBlocks.AIR);
                    }
                }
            }
        }

        @Override
        public int groundLevel() {
            return flat.groundLevel();
        }
    }
}
