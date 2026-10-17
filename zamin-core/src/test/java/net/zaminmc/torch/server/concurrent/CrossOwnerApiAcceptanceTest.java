package net.zaminmc.torch.server.concurrent;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.concurrent.CrossOwner;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cross-owner request surface's acceptance (§28.2's default
 * nonblocking API — Slice 80): the {@code CrossOwnerRouter}'s public
 * face over the {@code Server.crossOwner()} accessor. The unit fixtures
 * prove the request semantics (the target-context apply, the completion
 * contract, the failure isolation, the cancel gate, the deadline gate,
 * the loud backpressure, the unpartitioned routing) on hand-driven
 * drains; the live-engine arms prove the real wiring (the foreign-thread
 * submission applying through the tick-head boundary walk, and the End
 * dimension's region drain — the Slice 80 fix).
 */
class CrossOwnerApiAcceptanceTest {

    private static FrozenBlockRegistry registry;

    @BeforeAll
    static void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
    }

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // The unit surface (hand-driven drains over the fixture worlds)
    // ------------------------------------------------------------------

    @Test
    void theRequestAppliesInTheTargetRegionContextWithItsCompletion() throws Exception {
        EngineWorld world = fixtureWorld("applies", true);
        CrossOwnerRouter router = new CrossOwnerRouter();
        CrossOwnerView view = new CrossOwnerView(router);
        OwnershipDomain right = world.regionDomainFor(new BlockPosition(16, 4, 8));

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> seen = new AtomicReference<>();
        AtomicReference<Throwable> failed = new AtomicReference<>();
        CrossOwner.Request<String> request = view.submit(world,
                new BlockPosition(16, 4, 8), "test:write",
                () -> {
                    // The action's own authority proof: the target
                    // region's mutation is legal HERE (the drain entered
                    // the region's context around this apply).
                    world.setBlock(new BlockPosition(16, 4, 8), BuiltinBlocks.STONE);
                    return "written";
                },
                (result, failure) -> {
                    seen.set(result);
                    failed.set(failure);
                    done.countDown();
                });

        drain(router, right);
        assertTrue(done.await(5, TimeUnit.SECONDS), "the completion ran");
        assertNull(failed.get(), "the completion carried no failure");
        assertEquals("written", seen.get(), "the completion carries the result");
        assertEquals(CrossOwner.Request.State.APPLIED, request.state());
        assertTrue(request.isDone());
        assertEquals(java.util.Optional.of("written"), request.resultNow());
        assertEquals("stone", world.getBlock(new BlockPosition(16, 4, 8))
                .identifier().value(), "the target's mutation landed");
        assertEquals(1, view.appliedCount(), "the view's applied counter");
        assertEquals(1, router.appliedCount(right), "the router's per-target counter");
    }

    @Test
    void theFailingActionReportsToTheCompletionAndNeverKillsTheDrain() throws Exception {
        EngineWorld world = fixtureWorld("failing", true);
        CrossOwnerRouter router = new CrossOwnerRouter();
        CrossOwnerView view = new CrossOwnerView(router);
        OwnershipDomain region = world.regionDomainFor(new BlockPosition(16, 4, 8));

        AtomicReference<Throwable> reported = new AtomicReference<>();
        CountDownLatch badDone = new CountDownLatch(1);
        view.submit(world, new BlockPosition(16, 4, 8), "test:boom",
                () -> {
                    throw new IllegalStateException("the action's own failure");
                },
                (result, failure) -> {
                    reported.set(failure);
                    badDone.countDown();
                });
        AtomicBoolean goodRan = new AtomicBoolean(false);
        view.submit(world, new BlockPosition(16, 4, 8), "test:survivor",
                () -> {
                    goodRan.set(true);
                    return null;
                }, null);

        int applied = drain(router, region);
        assertTrue(badDone.await(5, TimeUnit.SECONDS), "the failure reported");
        assertInstanceOf(IllegalStateException.class, reported.get());
        assertTrue(goodRan.get(), "the neighbor request still applied");
        assertEquals(2, applied,
                "the drain processed both — the failure isolated inside the guard");
        assertEquals(1, view.failedCount(), "the view's failed counter");
        assertEquals(1, view.appliedCount(), "the survivor's applied counter");
    }

    @Test
    void theCancelledRequestSkipsWithoutRunning() throws Exception {
        EngineWorld world = fixtureWorld("cancel", true);
        CrossOwnerRouter router = new CrossOwnerRouter();
        CrossOwnerView view = new CrossOwnerView(router);
        OwnershipDomain region = world.regionDomainFor(new BlockPosition(16, 4, 8));

        AtomicBoolean ran = new AtomicBoolean(false);
        CrossOwner.Request<Void> request = view.submit(world,
                new BlockPosition(16, 4, 8), "test:cancel",
                () -> ran.set(true));

        assertTrue(request.cancel(), "the cancel won while queued");
        assertEquals(CrossOwner.Request.State.CANCELLED, request.state());
        assertTrue(request.isDone());

        int applied = drain(router, region);
        // The ROUTER's count is "drained without throwing" — the guard
        // isolates the outcome inside itself, so the skip still drains
        // harmlessly; the VIEW's cancelled counter carries the semantics.
        assertEquals(1, applied, "the drain processed the skip harmlessly");
        assertFalse(ran.get(), "the action never ran");
        assertEquals(1, view.cancelledCount(), "the view's cancelled counter");
        assertEquals(0, router.pendingFor(region), "the window cleared");
    }

    @Test
    void theExpiredDeadlineSkipsWithoutRunning() throws Exception {
        EngineWorld world = fixtureWorld("expire", true);
        CrossOwnerRouter router = new CrossOwnerRouter();
        CrossOwnerView view = new CrossOwnerView(router);
        OwnershipDomain region = world.regionDomainFor(new BlockPosition(16, 4, 8));

        AtomicBoolean ran = new AtomicBoolean(false);
        CrossOwner.Request<Void> request = view.submit(world,
                new BlockPosition(16, 4, 8), "test:expire",
                () -> {
                    ran.set(true);
                    return null;
                }, null, world.totalTicks() - 1);

        int applied = drain(router, region);
        assertEquals(1, applied, "the drain processed the expired skip harmlessly");
        assertFalse(ran.get(), "the action never ran");
        assertEquals(CrossOwner.Request.State.EXPIRED, request.state(),
                "the deadline gate expired the request");
        assertEquals(1, view.expiredCount(), "the view's expired counter");
    }

    @Test
    void theDeadlineStillAppliesOnItsOwnTick() throws Exception {
        EngineWorld world = fixtureWorld("deadline", true);
        CrossOwnerRouter router = new CrossOwnerRouter();
        CrossOwnerView view = new CrossOwnerView(router);
        OwnershipDomain region = world.regionDomainFor(new BlockPosition(16, 4, 8));

        AtomicBoolean ran = new AtomicBoolean(false);
        CrossOwner.Request<Void> request = view.submit(world,
                new BlockPosition(16, 4, 8), "test:deadline-live",
                () -> {
                    ran.set(true);
                    return null;
                }, null, world.totalTicks());

        int applied = drain(router, region);
        assertEquals(1, applied, "the request applied on its own deadline tick");
        assertTrue(ran.get());
        assertEquals(CrossOwner.Request.State.APPLIED, request.state());
    }

    @Test
    void theFullQueueRefusesLoudlyAndTheHandleReportsIt() throws Exception {
        EngineWorld world = fixtureWorld("refuse", true);
        CrossOwnerRouter router = new CrossOwnerRouter(1);
        CrossOwnerView view = new CrossOwnerView(router);
        OwnershipDomain region = world.regionDomainFor(new BlockPosition(16, 4, 8));

        CrossOwner.Request<Void> first = view.submit(world,
                new BlockPosition(16, 4, 8), "test:first", () -> { });

        AtomicBoolean secondRan = new AtomicBoolean(false);
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> view.submit(world, new BlockPosition(16, 4, 8),
                        "test:second", () -> secondRan.set(true)));
        assertTrue(refused.getMessage().contains("pending bound"),
                "the loud refusal names the bound: " + refused.getMessage());

        drain(router, region);
        assertEquals(CrossOwner.Request.State.APPLIED, first.state(),
                "the first request still applied");
        assertFalse(secondRan.get(), "the refused request never ran");
        assertEquals(1, view.refusedCount(), "the refusal counted");
    }

    @Test
    void theUnpartitionedWorldRoutesToItsOwnDomain() throws Exception {
        EngineWorld world = fixtureWorld("ambient", false);
        CrossOwnerRouter router = new CrossOwnerRouter();
        CrossOwnerView view = new CrossOwnerView(router);

        AtomicBoolean ran = new AtomicBoolean(false);
        view.submit(world, new BlockPosition(8, 4, 8), "test:ambient",
                () -> {
                    ran.set(true);
                    world.setBlock(new BlockPosition(8, 4, 8), BuiltinBlocks.STONE);
                    return null;
                }, null);

        int applied = drain(router, world.domain());
        assertEquals(1, applied, "the unpartitioned request applied on the world's own domain");
        assertTrue(ran.get());
        assertEquals("stone", world.getBlock(new BlockPosition(8, 4, 8))
                .identifier().value());
    }

    // ------------------------------------------------------------------
    // The live engine (the real wiring through the tick-head walk)
    // ------------------------------------------------------------------

    @Test
    void theLiveEngineAppliesARequestFromAForeignThread() throws Exception {
        EngineServer server = boot();
        try {
            AtomicReference<String> result = new AtomicReference<>();
            CountDownLatch completion = new CountDownLatch(1);
            // The foreign thread: a plain caller holding no context at
            // all — the request routes to the target region's lane and
            // the tick-head boundary walk applies it there.
            Thread foreign = new Thread(() -> server.crossOwner().submit(
                    server.world(), new BlockPosition(16, 40, 10), "test:live",
                    () -> {
                        server.world().setBlock(
                                new BlockPosition(16, 40, 10), BuiltinBlocks.STONE);
                        return "landed";
                    },
                    (value, failure) -> {
                        if (failure == null) {
                            result.set(value);
                        }
                        completion.countDown();
                    }), "foreign-submitter");
            foreign.start();
            foreign.join(5_000);

            assertTrue(completion.await(30, TimeUnit.SECONDS),
                    "the completion ran through the live engine");
            assertEquals("landed", result.get(), "the completion carries the result");
            await(() -> "stone".equals(typeAt(server, 16, 40, 10)),
                    "the write landed at the target region");
            assertTrue(server.crossOwner().appliedCount() >= 1,
                    "the view's applied counter through the public surface");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theEndDimensionRegionIntentsNowDrain() throws Exception {
        EngineServer server = boot();
        try {
            assertNotNull(server.endWorld(), "the End booted");
            assertTrue(server.worlds().size() == 3,
                    "the public collection carries all three dimensions");
            // A request toward the End's own chunk space — pre-Slice-80
            // this intent sat queued forever (the End's region drain was
            // the missing tick-head line).
            AtomicReference<String> result = new AtomicReference<>();
            CountDownLatch completion = new CountDownLatch(1);
            server.crossOwner().submit(server.endWorld(),
                    new BlockPosition(16, 40, 10), "test:end",
                    () -> "end-applied",
                    (value, failure) -> {
                        if (failure == null) {
                            result.set(value);
                        }
                        completion.countDown();
                    });
            assertTrue(completion.await(30, TimeUnit.SECONDS),
                    "the End's region drain applied the request");
            assertEquals("end-applied", result.get());
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    /** The fixture world (the partition arm: every chunk its own region). */
    private EngineWorld fixtureWorld(String name, boolean partitioned) {
        EngineWorld world = new EngineWorld(name, registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        if (partitioned) {
            world.attachRegionPartition(new RegionPartition(name, 1));
        }
        world.attachDomain(OwnershipDomain.create("simulation:" + name));
        return world;
    }

    /** The target's own boundary drain: enter, drain, exit. */
    private int drain(CrossOwnerRouter router, OwnershipDomain target) {
        target.enter();
        try {
            return router.drainIntents(target);
        } finally {
            target.exit();
        }
    }

    /** The one-chunk-region engine: every chunk its own ownership domain. */
    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "coa" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, 1);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    /** Reads the block type through the tick thread (the submit queue's happens-before edge). */
    private static String typeAt(EngineServer server, int x, int y, int z) {
        AtomicReference<String> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(
                server.world().getBlock(new BlockPosition(x, y, z))
                        .identifier().value()));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return result.get();
            }
        }
        return result.get();
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
