package net.zaminmc.torch.server;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ScheduledUpdates;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The scheduled-tick API wave (§27.3's scheduled-block-tick family over
 * {@link World#scheduledUpdates()}): the pending neighbor updates and
 * gravity fuses — the queries copy the queue from any thread, the
 * cancel/request arms run on the world's authoritative context (the
 * §27.6 mutation contract: an off-lane call fails diagnosably).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduledUpdatesApiAcceptanceTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString(), net.zaminmc.torch.GameMode.CREATIVE);
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    @Test
    void thePendingUpdatesReadCancelAndRequestThroughThePublicView() throws Exception {
        EngineServer server = boot();
        try {
            BlockPosition at = new BlockPosition(30, 60, 30);
            BlockPosition above = at.offset(0, 1, 0);
            // A grass block with stone placed above: the commit schedules the
            // decay updates around it (the engine's own listener), the grass
            // under the opaque stone dies to dirt.
            server.ticker().submit(() ->
                    server.world().setBlock(at, BuiltinBlocks.GRASS_BLOCK));
            awaitSettled(server);
            server.ticker().submit(() ->
                    server.world().setBlock(above, BuiltinBlocks.STONE));
            awaitSettled(server);

            World world = server.world();
            ScheduledUpdates updates = world.scheduledUpdates();

            // The stone commit scheduled the cell + its six neighbors due the
            // commit tick; they run the same tick — after settling the queue
            // is empty again (the rules resolved: grass under stone dies).
            assertEquals(BuiltinBlocks.DIRT, world.getBlock(at), "the grass died to dirt");
            assertTrue(updates.pending().isEmpty(), "the due updates ran");

            // The request arm (owner lane): schedules the cell, due now —
            // the next tick runs it and the queue drains again.
            server.ticker().submit(() -> updates.requestAt(above));
            awaitSettled(server);
            assertTrue(updates.pending().isEmpty(), "the requested update ran");

            // The cancel arm (owner lane): a FUTURE-due pending fuse cancels.
            // Turn 1 (the submit): place the sand with support, break the
            // support, and schedule the cancellation one tick out — the fuse
            // arms at THIS tick's update walk (due +2), the runLater(1) body
            // runs at the head of the NEXT tick (inside the two-tick window,
            // before the fuse fires).
            BlockPosition sand = new BlockPosition(32, 60, 32);
            java.util.concurrent.atomic.AtomicBoolean cancelled =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            server.ticker().submit(() -> {
                server.world().setBlock(sand.offset(0, -1, 0), BuiltinBlocks.STONE);
                server.world().setBlock(sand, BuiltinBlocks.SAND);
                server.world().setBlock(sand.offset(0, -1, 0), BuiltinBlocks.AIR);
                server.scheduler().runLater(1, () ->
                        cancelled.set(updates.cancelFallCheckAt(sand)));
            });
            awaitSettled(server);
            assertTrue(cancelled.get(), "the armed fuse cancelled inside its window");
            assertEquals(BuiltinBlocks.SAND, world.getBlock(sand),
                    "the sand hovers forever — its fuse was cancelled");
            assertEquals(0, world.fallingBlocks().size(), "no falling body started");

            // The query arms off-lane: nothing pending (best-effort copies).
            assertTrue(updates.pending().isEmpty());
            assertTrue(updates.pendingAt(sand).isEmpty());
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theMutationArmsAssertTheOwnerContext() throws Exception {
        EngineServer server = boot();
        try {
            World world = server.world();
            ScheduledUpdates updates = world.scheduledUpdates();
            BlockPosition at = new BlockPosition(34, 60, 34);

            // Off-lane cancel: the diagnosable §27.6 violation.
            AtomicReference<Throwable> caught = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            Thread stranger = new Thread(() -> {
                try {
                    updates.cancelAt(at);
                } catch (Throwable t) {
                    caught.set(t);
                } finally {
                    done.countDown();
                }
            });
            stranger.start();
            assertTrue(done.await(5, TimeUnit.SECONDS), "the stranger finished");
            IllegalStateException violation =
                    assertInstanceOf(IllegalStateException.class, caught.get(),
                            "the off-lane cancel fails diagnosably");
            assertTrue(violation.getMessage().contains("cancelScheduledUpdate"),
                    "the violation names the operation: " + violation.getMessage());

            // Off-lane request: the same contract.
            AtomicReference<Throwable> caughtRequest = new AtomicReference<>();
            CountDownLatch doneRequest = new CountDownLatch(1);
            Thread strangerRequest = new Thread(() -> {
                try {
                    updates.requestAt(at);
                } catch (Throwable t) {
                    caughtRequest.set(t);
                } finally {
                    doneRequest.countDown();
                }
            });
            strangerRequest.start();
            assertTrue(doneRequest.await(5, TimeUnit.SECONDS), "the second stranger finished");
            assertNotNull(caughtRequest.get(), "the off-lane request fails too");

            // On-lane request through World.execute (the §27.6 owner path):
            // the verb runs.
            world.execute(() -> updates.requestAt(at));
            awaitSettled(server);
            Optional<ScheduledUpdates.PendingUpdate> pending =
                    updates.pendingAt(at);
            // The requested update was due the current tick — after settling
            // it ran (air at the position has no rules, the queue drains).
            assertTrue(pending.isEmpty(), "the on-lane request ran and drained");
        } finally {
            server.shutdown(null);
        }
    }

    /** The submit drains at the head of the next tick (<= 100 ms). */
    private void awaitSettled(EngineServer server) throws InterruptedException {
        EngineAwaits.awaitTicks(server.world(), 6, "the submit drains at the head of the next tick");
    }
}
