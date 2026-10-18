package net.zaminmc.torch.server.concurrent;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.RandomTickSystem;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Phase-6 lane's proof obligation (the random-tick compute-then-apply
 * region lane — §22 Phase 6's evidence gate): the per-region derived
 * streams make the sequential walk and the pool dispatch produce
 * IDENTICAL picks, the applies stay on the caller's thread in the stable
 * region order (§6.2's one authoritative writer), and the lane actually
 * ENGAGES when armed over a multi-region partition (the dispatch
 * counter — never a silent sequential fallback).
 *
 * <p>THE LOCKSTEP DESIGN (why not two boots): the stream derivation's
 * time axis is the tick, and two separate server boots can never align
 * their absolute tick numbers — the equivalence cannot be proven across
 * boots. Instead TWO identically-built worlds (same generator, same
 * light engine, same partition, same chunk generation order, same
 * seeded apply-side randomness) are driven tick-by-tick IN LOCKSTEP,
 * one walk sequential, one on the pool: every divergence — a dropped
 * apply, a reordered region, a different pick — would show as a state
 * difference. STRICT equality, not a convergence fixed point.</p>
 */
class RandomTickLaneTest {

    /** The pure-computation contract: the streams are per-(tick, region). */
    @Test
    void theStreamsArePerRegionAndTick() {
        // The same (tick, region) answers the same sequence; different
        // ticks and regions answer different sequences.
        java.util.Random a = RandomTickPicks.streamFor(1234L, 100L, 7L);
        java.util.Random b = RandomTickPicks.streamFor(1234L, 100L, 7L);
        java.util.Random otherTick = RandomTickPicks.streamFor(1234L, 101L, 7L);
        java.util.Random otherRegion = RandomTickPicks.streamFor(1234L, 100L, 8L);
        for (int i = 0; i < 8; i++) {
            assertEquals(a.nextInt(16), b.nextInt(16), "the same (tick, region) replays");
        }
        boolean differs = false;
        for (int i = 0; i < 16 && !differs; i++) {
            differs = a.nextInt(16) != otherTick.nextInt(16)
                    || a.nextInt(16) != otherRegion.nextInt(16);
        }
        assertTrue(differs, "different ticks and regions answer different streams");
    }

    /** The pick computation's shape: the chunk/section/pick order. */
    @Test
    void thePicksFollowTheChunkSectionPickOrder() {
        List<long[]> twoChunks = List.of(new long[]{10, -3}, new long[]{0, 0});
        List<long[]> picks = RandomTickPicks.picksForRegion(
                42L, 5L, 9L, twoChunks, 3);
        // 2 chunks * 16 sections * 3 picks.
        assertEquals(2 * 16 * 3, picks.size());
        // Every pick lies inside its chunk's cell band.
        for (long[] cell : picks) {
            assertTrue(cell[0] >= 10 * 16 && cell[0] < 11 * 16
                    || cell[0] >= 0 && cell[0] < 16);
            assertTrue(cell[1] >= 0 && cell[1] < 256);
            assertTrue(cell[2] >= -3 * 16 && cell[2] < -2 * 16
                    || cell[2] >= 0 && cell[2] < 16);
        }
        // The zero band answers nothing (the rule's own gate).
        assertTrue(RandomTickPicks.picksForRegion(42L, 5L, 9L, twoChunks, 0).isEmpty());
    }

    /**
     * THE LANE'S LOCKSTEP EQUIVALENCE: two identically-built worlds —
     * same flat generator (same seed), same light engine, same 2-chunk
     * region partition, same seeded apply-side randomness, the same
     * dirt patch laid above the surface — driven tick by tick, one
     * walk sequential and one on the worker pool. The derived streams
     * (per tick, per region) make the picks identical; the applies run
     * in the stable region order on the caller's thread; the two world
     * states must match EXACTLY while both differ from their initial
     * snapshot (the random-tick writes actually flowed — the covered
     * grass decays, the patch's edge dirt grows grass).
     */
    @Test
    void theSequentialAndParallelWalksAgreeInLockstep() {
        WorldPair pair = buildIdenticalWorlds();
        // The patch above the surface: covered grass decays below
        // it, the patch's edge dirt grows grass from the open
        // grass beside it — both lanes must write the same cells.
        for (int x = -3; x <= 2; x++) {
            for (int z = -3; z <= 2; z++) {
                BlockPosition cell = new BlockPosition(x, 5, z);
                pair.sequentialWorld.setBlock(cell, BuiltinBlocks.DIRT);
                pair.parallelWorld.setBlock(cell, BuiltinBlocks.DIRT);
            }
        }
        String initial = snapshot(pair.sequentialWorld);
        assertEquals(initial, snapshot(pair.parallelWorld),
                "the two worlds start identical");

        RandomTickSystem sequential =
                new RandomTickSystem(pair.sequentialWorld, new Random(99));
        RandomTickSystem parallel =
                new RandomTickSystem(pair.parallelWorld, new Random(99));
        DomainWorkerPool pool = new DomainWorkerPool(2);
        try {
            parallel.setLanePool(pool);
            for (int i = 0; i < 500; i++) {
                sequential.tick(List.of());
                parallel.tick(List.of());
            }
        } finally {
            pool.shutdown();
        }

        // THE EQUIVALENCE: exact state equality, never a fixed point.
        String sequentialState = snapshot(pair.sequentialWorld);
        String parallelState = snapshot(pair.parallelWorld);
        assertEquals(sequentialState, parallelState,
                "the two lane modes produce identical world states");
        // NON-TRIVIAL: the drives wrote (a vacuous equality of two
        // untouched worlds would prove nothing about the lane).
        assertNotEquals(initial, sequentialState,
                "the drives produced random-tick writes");
        assertNotEquals(initial, parallelState,
                "the parallel lane's writes flowed too");
        // THE ENGAGEMENT: the parallel walk rode the pool (9 regions
        // over the 5x5 chunk area at 2-chunk granularity), the
        // sequential walk never did.
        assertTrue(parallel.parallelDispatchTicks() > 0,
                "the parallel walk engaged the pool dispatch");
        assertEquals(0, sequential.parallelDispatchTicks(),
                "the unarmed walk stayed sequential");
    }

    /**
     * The lane's LIVE wiring: the boot-time property gate
     * ({@code torch.parallel-region-ticks}) arms the lane over a real
     * region split, the live tick loop rides the dispatch, and the
     * shutdown drains clean. The engagement is asserted through the
     * dispatch counter — the observability the gate's own evidence
     * demands.
     */
    @Test
    void theBootGateArmsTheLiveLane(@TempDir Path dir) throws Exception {
        System.setProperty("torch.parallel-region-ticks", "true");
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "lanelive", "it", 20, 4, 20,
                dir.resolve("live").toString(),
                net.zaminmc.torch.GameMode.SURVIVAL, true, "flat", false,
                4, false);
        EngineServer server = new EngineServer(config);
        try {
            server.start();
        } finally {
            System.clearProperty("torch.parallel-region-ticks");
        }
        try {
            ClientLink link = new ClientLink() {
                @Override public boolean isActive() { return true; }
                @Override public void kick(String reason) { }
            };
            var result = server.joinRequest(link, "LaneRider",
                    UUID.nameUUIDFromBytes("LaneRider".getBytes()));
            PlayerSession session = ((EngineBridge.Accepted) result).session();
            server.joinCompleted(session);
            session.applyMovement(new Position(0.5, 5.0, 0.5), Rotation.ZERO, true);
            await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING);
            // Drive real ticks: the anchored player's range holds chunks
            // across several 4-chunk regions — the dispatch engages.
            for (int i = 0; i < 40; i++) {
                server.ticker().submit(() -> { });
                Thread.sleep(10);
            }
            assertTrue(server.randomTicks() != null
                            && server.randomTicks().parallelDispatchTicks() > 0,
                    "the live loop rode the parallel dispatch");
        } finally {
            server.shutdown(null);
        }
    }

    /** The pool dispatch itself: the jobs run, the join holds. */
    @Test
    void thePoolDispatchRunsTheRegionJobs() {
        DomainWorkerPool pool = new DomainWorkerPool(2);
        try {
            List<Integer> results = java.util.Collections.synchronizedList(
                    new ArrayList<>());
            List<Runnable> jobs = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                final int value = i;
                jobs.add(() -> results.add(value));
            }
            pool.dispatchAndJoin(jobs);
            assertEquals(8, results.size());
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Two identically-built worlds: the same registry, the same flat
     * generator (ground level 4 — the surface grass the picks write),
     * the same light engine (the decay/spread gates read real light),
     * the same 2-chunk region partition (a 5x5 chunk area spans 9
     * regions), the chunks generated in the SAME nested-loop order (the
     * chunk iteration order feeds the pick stream's chunk list — the
     * lockstep's own precondition).
     */
    private static WorldPair buildIdenticalWorlds() {
        FrozenBlockRegistry registry =
                BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        EngineWorld sequential = buildWorld("lanelock-a", registry);
        EngineWorld parallel = buildWorld("lanelock-b", registry);
        return new WorldPair(sequential, parallel);
    }

    private static EngineWorld buildWorld(String name, FrozenBlockRegistry registry) {
        EngineWorld world = new EngineWorld(name, registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        net.zaminmc.torch.server.world.light.LightEngine light =
                new net.zaminmc.torch.server.world.light.LightEngine(world);
        world.addChangeListener(light);
        world.addChunkLoadListener(light);
        world.attachRegionPartition(new RegionPartition(name, 2));
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getOrGenerate(new ChunkPosition(x, z));
            }
        }
        return world;
    }

    /**
     * The state snapshot the equivalence compares: every non-air cell
     * in the band the random ticks write (the surface grass line at 4,
     * the patch layer at 5, the air above it) over the patch's
     * neighbourhood — a stable textual form.
     */
    private static String snapshot(EngineWorld world) {
        StringBuilder state = new StringBuilder();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                for (int y = 4; y <= 6; y++) {
                    var type = world.getBlock(new BlockPosition(x, y, z));
                    if (!type.equals(BuiltinBlocks.AIR)) {
                        state.append(x).append(',').append(y).append(',').append(z)
                                .append(':').append(type.identifier()).append(';');
                    }
                }
            }
        }
        return state.toString();
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("condition not met");
    }

    /** The lockstep pair's own holder: two separately-owned worlds. */
    private record WorldPair(EngineWorld sequentialWorld, EngineWorld parallelWorld) {
    }
}
