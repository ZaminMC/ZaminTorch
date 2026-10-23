package net.zaminmc.torch.server;

import net.zaminmc.torch.entity.EntitySnapshot;
import net.zaminmc.torch.entity.LivingEntity;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The public entity API (§27.5/§27.6) over the live engine: a plugin-style
 * consumer holds {@link LivingEntity} handles, runs owner-context work
 * through {@code World.execute}, and consumes
 * {@link EntitySnapshot}s off-owner — no engine internals anywhere.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EntityApiAcceptanceTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString(), net.zaminmc.torch.GameMode.CREATIVE);
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        return ((EngineBridge.Accepted) result).session();
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    @Test
    void theWorldExposesTheSpawnedMobAsALivingEntity() throws Exception {
        server = boot();
        join("Observer");
        net.zaminmc.torch.util.Position at = new net.zaminmc.torch.util.Position(8.5, 8.0, 8.5);
        AtomicReference<LivingEntity> handle = new AtomicReference<>();

        // The owner path: spawn through the engine, find it through the API.
        server.world().execute(() -> {
            for (LivingEntity entity : server.world().entities()) {
                if (entity.typeIdentifier().value().equals("pig")
                        && entity.position().distanceSquared(at) < 16.0) {
                    handle.set(entity);
                }
            }
        });
        // The pig is not there yet — spawn one engine-side and find it again.
        server.mobs().spawnAt(net.zaminmc.torch.server.entity.MobType.PIG, at);
        await(() -> {
            server.world().execute(() -> {
                for (LivingEntity entity : server.world().entities()) {
                    if (entity.typeIdentifier().value().equals("pig")) {
                        handle.set(entity);
                    }
                }
            });
            return handle.get() != null;
        }, "the pig appeared in the public entity view");

        LivingEntity pig = handle.get();
        assertEquals(Identifier.parse("minecraft:pig"), pig.typeIdentifier());
        assertTrue(pig.maxHealth() > 0, "the pig has a positive maximum health");
        assertTrue(pig.health() > 0, "the spawned pig is alive");
        assertFalse(pig.dead());
        assertEquals(0.0f, pig.rotation().pitch(), 0.0f, "mobs carry no pitch");

        server.shutdown(null);
    }

    @Test
    void ownerContextWorkMutatesThroughTheHandle() throws Exception {
        server = boot();
        join("Handler");
        net.zaminmc.torch.util.Position at = new net.zaminmc.torch.util.Position(16.5, 8.0, 16.5);
        var mob = server.mobs().spawnAt(net.zaminmc.torch.server.entity.MobType.COW, at);
        assertNotNull(mob);

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<LivingEntity> handle = new AtomicReference<>();
        // A plugin-style foreign thread: it never touches the engine directly —
        // it submits owner-context work and consumes the result.
        Thread pluginThread = new Thread(() -> {
            server.world().execute(() -> {
                LivingEntity cow = null;
                for (LivingEntity entity : server.world().entities()) {
                    if (entity.typeIdentifier().value().equals("cow")
                            && entity.position().distanceSquared(at) < 4.0) {
                        cow = entity;
                    }
                }
                if (cow == null) {
                    return;
                }
                handle.set(cow);
                float before = cow.health();
                cow.hurt(3.0f);
                float hurt = cow.health();
                cow.heal(2.0f);
                float healed = cow.health();
                cow.addVelocity(0.05, 0.0, 0.05);
                // The observable ladder: hurt lands, heal restores, the
                // impulses ride the next movement tick.
                if (hurt < before && healed > hurt) {
                    done.countDown();
                }
            });
        }, "plugin-thread");
        pluginThread.start();
        assertTrue(done.await(3, TimeUnit.SECONDS),
                "the owner-context work ran and the handle mutations landed");
        pluginThread.join();

        LivingEntity cow = handle.get();
        assertEquals(mob.health(), cow.health(), 0.001,
                "the handle resolves to the same authoritative object");
        assertTrue(cow.velocityX() > 0.0, "the impulse was added");

        server.shutdown(null);
    }

    @Test
    void snapshotsAreConsumableOffOwner() throws Exception {
        server = boot();
        join("Snapper");
        net.zaminmc.torch.util.Position at = new net.zaminmc.torch.util.Position(24.5, 8.0, 24.5);
        server.mobs().spawnAt(net.zaminmc.torch.server.entity.MobType.SHEEP, at);
        await(() -> !server.world().entities().isEmpty(), "the sheep joined the view");

        AtomicReference<List<EntitySnapshot>> captured = new AtomicReference<>(new ArrayList<>());
        // Capture on the owner, consume on a foreign thread.
        server.world().execute(() -> {
            List<EntitySnapshot> snapshots = new ArrayList<>();
            for (LivingEntity entity : server.world().entities()) {
                snapshots.add(entity.snapshot());
            }
            captured.set(snapshots);
        });
        await(() -> !captured.get().isEmpty(), "the snapshots were captured");

        Thread foreign = new Thread(() -> {
            for (EntitySnapshot snapshot : captured.get()) {
                // Pure immutable reads, any thread — the §27.6 contract.
                assertTrue(snapshot.entityId() > 0);
                assertNotNull(snapshot.type());
                assertTrue(snapshot.position().isFinite());
                if (snapshot.type().value().equals("sheep")) {
                    assertEquals(8.0f, snapshot.health(), 0.001,
                            "the sheep's captured health");
                }
            }
        }, "foreign-consumer");
        foreign.start();
        foreign.join(2_000);
        assertFalse(foreign.isAlive(), "the off-owner consumption completed");

        server.shutdown(null);
    }

    @Test
    void despawnRemovesTheEntityThroughTheHandle() throws Exception {
        server = boot();
        join("Cleaner");
        net.zaminmc.torch.util.Position at = new net.zaminmc.torch.util.Position(32.5, 8.0, 32.5);
        int baselinePigs = countPigs();
        var mob = server.mobs().spawnAt(net.zaminmc.torch.server.entity.MobType.PIG, at);
        assertNotNull(mob);
        await(() -> countPigs() >= baselinePigs + 1, "the pig joined the view");

        CountDownLatch despawned = new CountDownLatch(1);
        server.world().execute(() -> {
            // Despawn exactly the new pig: the one standing at our position.
            for (LivingEntity entity : server.world().entities()) {
                if (entity.typeIdentifier().value().equals("pig")
                        && entity.position().distanceSquared(at) < 4.0) {
                    entity.despawn();
                }
            }
            despawned.countDown();
        });
        assertTrue(despawned.await(3, TimeUnit.SECONDS));
        int pigsAtTheEnd = countPigs();
        await(() -> countPigs() == baselinePigs,
                "the despawned pig left the view (was " + pigsAtTheEnd
                        + ", baseline " + baselinePigs + ")");

        server.shutdown(null);
    }

    /** Pigs in the public view, counted from a foreign thread via execute. */
    private int countPigs() {
        java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
        server.world().execute(() -> {
            for (LivingEntity entity : server.world().entities()) {
                if (entity.typeIdentifier().value().equals("pig")) {
                    count.incrementAndGet();
                }
            }
        });
        try {
            // execute is fire-and-forget on the owner lane; give the count a
            // moment to land before the caller reads it.
            EngineAwaits.awaitTicks(server.world(), 2, "engine progress (a 60 wall settle)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return count.get();
    }
}
