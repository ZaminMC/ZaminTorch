package net.zaminmc.torch.server.world.light;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.world.EngineChunk;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE UNLOADED-BORDER STORM REGRESSION — the unbounded-traversal bug the
 * lighting batch lane's measurements caught (and the fix's own guard): the
 * light BFS pushes into unloaded space, the write DROPS (the live setter's
 * own behavior — light cannot spread into unloaded space), but the READ
 * keeps answering the channel default forever (block 0), so the push
 * condition re-satisfies on EVERY incoming edge and a re-queued unloaded
 * cell re-floods everything downstream — the traversal count grows with
 * the number of monotone paths, EXPONENTIALLY in the light's reach. One
 * torch at the corner of a chunk with unloaded neighbors hung the initial
 * compute for THIRTY SECONDS (and the cliff shape for fifty-five); the
 * same shape through the LIVE incremental path (a torch placed at the
 * border of the loaded world) hung the tick thread identically — a
 * production engine bug, not a batch-lane artifact.
 *
 * <p>THE FIX (state-identical by construction): the BFS re-queues a cell
 * only when its write LANDED — the setters now answer the landing question
 * and the push consults it ({@code LightAccess.setBlockLight/setSkyLight}
 * return boolean). The dropped writes were no-ops all along, so the final
 * state is byte-identical; only the dead re-queues are gone (the measured
 * pathological compute: 30.8s -> 3.0ms).</p>
 *
 * <p>THE GUARD: the same shapes that stormed — the corner torch over the
 * initial compute (both routings: the live routing and the captured
 * region) and the live incremental placement at the loaded world's edge —
 * must complete in bounded time with the hand-checked values. The wall
 * clock asserts are deliberately generous (two seconds against the storm's
 * thirty) — they guard the class, not the nanosecond.</p>
 */
class UnloadedBorderStormTest {

    /** The storm's own bound: the pathological shapes completed in 30-55s pre-fix; 2s is the guard. */
    private static final long STORM_BOUND_NANOS = 2_000_000_000L;

    private EngineWorld newWorld() {
        var registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        return new EngineWorld("storm-guard", registry, new FlatWorldGenerator(registry, 4),
                Thread.currentThread());
    }

    /**
     * THE INITIAL-COMPUTE SHAPE: a fresh chunk with a torch at its CORNER —
     * every neighbor unloaded (the pregen's first wave's own shape) — over
     * both routings. Pre-fix this computed for thirty seconds.
     */
    @Test
    void theCornerTorchInitialComputeTerminatesOverBothRoutings() {
        // ---- the live routing: the compute over the unpublished chunk
        EngineWorld live = newWorld();
        LightEngine liveEngine = new LightEngine(live);
        live.addChangeListener(liveEngine);
        live.addChunkLoadListener(liveEngine);
        EngineChunk liveFresh = new EngineChunk(new ChunkPosition(0, 0), live.airType());
        live.generator().generate(liveFresh);
        liveFresh.setBlock(0, 6, 15, BuiltinBlocks.TORCH); // the corner cell: east and south faces unloaded

        long start = System.nanoTime();
        liveEngine.computeInitial(liveFresh);
        long liveNanos = System.nanoTime() - start;
        assertTrue(liveNanos < STORM_BOUND_NANOS,
                "the live corner-torch compute is bounded (" + liveNanos / 1_000 + "us; the storm was 30s)");

        // ---- the captured region: the same chunk over the offload's routing
        EngineWorld off = newWorld();
        LightEngine offEngine = new LightEngine(off);
        off.addChangeListener(offEngine);
        off.addChunkLoadListener(offEngine);
        EngineChunk offFresh = new EngineChunk(new ChunkPosition(0, 0), off.airType());
        off.generator().generate(offFresh);
        offFresh.setBlock(0, 6, 15, BuiltinBlocks.TORCH);

        long offStart = System.nanoTime();
        LightRegion region = LightRegion.capture(off, offFresh);
        InitialLightResult result = InitialLightCompute.compute(region);
        offEngine.applyInitial(result, offFresh);
        long offNanos = System.nanoTime() - offStart;
        assertTrue(offNanos < STORM_BOUND_NANOS,
                "the region corner-torch compute is bounded (" + offNanos / 1_000 + "us; the storm was 30s)");

        // ---- the two routings agree (the seam's own contract, on the storm's shape)
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = 0; y <= 15; y++) {
                    assertEquals(liveFresh.blockLight(lx, y, lz), offFresh.blockLight(lx, y, lz),
                            "block light at " + lx + "," + y + "," + lz);
                    assertEquals(liveFresh.skyLight(lx, y, lz), offFresh.skyLight(lx, y, lz),
                            "sky light at " + lx + "," + y + "," + lz);
                }
            }
        }
        // ---- the hand-checked values: the torch's own cell and its in-chunk cone
        assertEquals(14, liveFresh.blockLight(0, 6, 15), "the torch cell radiates 14");
        assertEquals(13, liveFresh.blockLight(1, 6, 15), "one step east decays to 13");
        assertEquals(13, liveFresh.blockLight(0, 6, 14), "one step north decays to 13");
        assertEquals(12, liveFresh.blockLight(2, 6, 15), "two steps east decay to 12");
    }

    /**
     * THE LIVE INCREMENTAL SHAPE: a torch PLACED at the border of the loaded
     * world (the change listener's own update path — the same BFS, the same
     * unloaded drop). Pre-fix this hung the tick thread identically.
     */
    @Test
    void theLoadedEdgeTorchPlacementTerminates() {
        EngineWorld world = newWorld();
        LightEngine engine = new LightEngine(world);
        world.addChangeListener(engine);
        world.addChunkLoadListener(engine);
        // Load ONE chunk (its own install computes over unloaded borders — the
        // first storm surface), then place the torch at the loaded world's
        // edge: every eastern and southern consult answers unloaded space.
        world.getOrGenerate(new ChunkPosition(0, 0));
        world.setBlock(new BlockPosition(15, 6, 15), BuiltinBlocks.TORCH); // the loaded world's corner

        long start = System.nanoTime();
        world.setBlock(new BlockPosition(15, 6, 15), BuiltinBlocks.TORCH); // the relight walk (idempotent placement)
        long nanos = System.nanoTime() - start;
        assertTrue(nanos < STORM_BOUND_NANOS,
                "the live edge-torch update is bounded (" + nanos / 1_000 + "us)");
        assertEquals(14, world.blockLightAt(new BlockPosition(15, 6, 15)),
                "the placed torch radiates 14");
        assertEquals(13, world.blockLightAt(new BlockPosition(14, 6, 15)),
                "the in-chunk neighbor holds 13");
        // The unloaded side answers nothing: the world edge holds no phantom block light
        assertEquals(0, world.blockLightAt(new BlockPosition(16, 6, 15)),
                "unloaded space holds no block light (the default answer)");
    }
}
