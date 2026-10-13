package net.zaminmc.torch.server;

import net.zaminmc.torch.Server;
import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.PrimedTnt;
import net.zaminmc.torch.event.world.ExplosionEvent;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The explosion + TNT + redstone API wave (§27.3/§27.7): the
 * cancellable pre-commit {@link ExplosionEvent} over the detonation
 * walk (a cancelled blast never tears the world), the primed-TNT
 * family view over {@link World#primedTnt()} (the engine objects ARE
 * the handles, §27.5), and the redstone power reads over
 * {@link World#redstone()}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExplosionTntRedstoneApiAcceptanceTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString(), net.zaminmc.torch.GameMode.CREATIVE);
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    /** A small stone cluster around a center (the blast's targets). */
    private void placeCluster(EngineServer server, BlockPosition center) {
        server.ticker().submit(() -> {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        server.world().setBlock(center.offset(dx, dy, dz), BuiltinBlocks.STONE);
                    }
                }
            }
        });
    }

    private boolean clusterIntact(World world, BlockPosition center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    if (!world.getBlock(center.offset(dx, dy, dz))
                            .identifier().value().equals("stone")) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Test
    void theCancelledBlastNeverTearsTheWorld() throws Exception {
        EngineServer server = boot();
        try {
            World world = server.world();
            BlockPosition center = new BlockPosition(60, 60, 60);
            placeCluster(server, center);
            awaitSettled();

            // The cancelling handler: the gate fires BEFORE any destruction.
            AtomicInteger fired = new AtomicInteger();
            AtomicReference<Float> seenPower = new AtomicReference<>(0.0f);
            var sub = server.events().on(ExplosionEvent.class, event -> {
                fired.incrementAndGet();
                seenPower.set(event.power());
                event.setCancelled(true);
            });

            // A short-fuse prime at the center: the detonation ~2 ticks out.
            server.ticker().submit(() ->
                    server.tnt().ignite(center, 2));
            awaitSettled();

            assertEquals(1, fired.get(), "the gate fired exactly once");
            assertEquals(4.0f, seenPower.get(), 0.001f,
                    "the vanilla TNT power rides the event");
            assertTrue(clusterIntact(world, center),
                    "a cancelled blast destroys nothing");
            assertTrue(world.primedTnt().isEmpty(), "the body spent itself");

            sub.unsubscribe();

            // The uncancelled twin: the same cluster, no handler — the blast
            // tears the stone.
            placeCluster(server, center);
            awaitSettled();
            server.ticker().submit(() ->
                    server.tnt().ignite(center, 2));
            awaitSettled();
            assertFalse(clusterIntact(world, center),
                    "the uncancelled blast destroyed the cluster");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void thePrimedTntFamilyAndRedstoneReads() throws Exception {
        EngineServer server = boot();
        try {
            World world = server.world();
            assertTrue(world.primedTnt().isEmpty(), "no bodies at boot");

            // The family view: a primed body with its fuse + position + the
            // snapshot (§27.5 — the engine object IS the handle).
            BlockPosition at = new BlockPosition(70, 60, 70);
            server.ticker().submit(() ->
                    server.tnt().ignite(at, 80));
            awaitSettled();
            var bodies = world.primedTnt();
            assertEquals(1, bodies.size(), "the primed body answers the family view");
            PrimedTnt body = bodies.iterator().next();
            assertTrue(body.fuseTicks() > 0, "the fuse reads");
            assertTrue(body.fuseTicks() <= 80, "the vanilla prime's 80-tick band");
            assertEquals(net.zaminmc.torch.util.Identifier.parse("minecraft:primed_tnt"),
                    body.typeIdentifier(), "the canonical kind id");
            var snapshot = body.snapshot();
            assertTrue(snapshot.position().x() >= at.x() - 1 && snapshot.position().x() <= at.x() + 2,
                    "the snapshot carries the body's position (the small hop included)");
            assertTrue(snapshot.entityId() > 0, "the snapshot carries the engine id");

            // The redstone reads: a powered lever feeds every neighbor (the
            // engine's own power walk through the public view — a placed
            // wire would decay to its computed power, so the source itself
            // rides the read).
            BlockPosition lever = new BlockPosition(80, 60, 80);
            server.ticker().submit(() -> server.world().setBlock(lever,
                    net.zaminmc.torch.server.redstone.RedstoneBlocks.leverOf(0, true)));
            awaitSettled();
            assertEquals(15, world.redstone().powerAt(lever.offset(1, 0, 0)),
                    "the lever's neighbor reads the 15 feed");
            assertTrue(world.redstone().poweredAt(lever.offset(0, 0, 1)),
                    "the boolean form answers the same walk");
            assertEquals(0, world.redstone().powerAt(new BlockPosition(100, 60, 100)),
                    "a far cell reads nothing");
        } finally {
            server.shutdown(null);
        }
    }

    /** The submit drains at the head of the next tick (<= 100 ms). */
    private static void awaitSettled() {
        try {
            Thread.sleep(400);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
