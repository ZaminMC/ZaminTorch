package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.Server;
import net.zaminmc.torch.entity.ProjectileKind;
import net.zaminmc.torch.event.entity.ItemSpawnEvent;
import net.zaminmc.torch.event.entity.ProjectileSpawnEvent;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The entity-spawn event family (§27.7 — the ledger's named omission
 * closing): the cancellable pre-commit item-spawn gate over the drop
 * chokepoint (the block-break loot, the mob deaths, the player throws,
 * the container spills, the dispenser's plain egress) and the
 * cancellable pre-commit projectile-spawn gate over the launch
 * chokepoint (the bow shot, the throws, the dispenser arms, the blaze
 * and ghast volleys) — every handler through the public bus, over the
 * live engine.
 */
class EntitySpawnEventAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "ese" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(), net.zaminmc.torch.GameMode.CREATIVE,
                true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
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

    @Test
    void theItemSpawnGateCancelsTheDropBeforeItEntersTheWorld() throws Exception {
        Server api = server;
        AtomicInteger fired = new AtomicInteger();
        AtomicReference<net.zaminmc.torch.item.ItemStack> seenStack = new AtomicReference<>();
        var sub = api.events().on(ItemSpawnEvent.class, event -> {
            fired.incrementAndGet();
            seenStack.set(event.stack());
            event.setCancelled(true);
        });

        // The engine's drop chokepoint (the same body the block-break
        // loot, the deaths and the spills ride).
        AtomicReference<Object> spawned = new AtomicReference<>("unset");
        server.ticker().submit(() -> spawned.set(server.itemEntities().spawnDropAtBlock(
                new Position(50, 40, 50),
                net.zaminmc.torch.item.ItemStack.of(BuiltinItems.DIAMOND, 3), 10)));
        await(() -> !"unset".equals(spawned.get()), "the drop walk ran");

        assertEquals(1, fired.get(), "the gate fired exactly once");
        assertEquals(BuiltinItems.DIAMOND, seenStack.get().type(), "the dropped stack rides the event");
        assertEquals(3, seenStack.get().count(), "the stack's count rides the event");
        assertNull(spawned.get(), "the cancelled drop returns null (no id consumed)");
        assertTrue(server.itemEntities().all().isEmpty(),
                "nothing lies on the ground after the cancelled drop");

        sub.unsubscribe();

        // The uncancelled twin: the same drop spawns.
        AtomicReference<Object> twin = new AtomicReference<>("unset");
        server.ticker().submit(() -> twin.set(server.itemEntities().spawnDropAtBlock(
                new Position(50, 40, 50),
                net.zaminmc.torch.item.ItemStack.of(BuiltinItems.DIAMOND, 3), 10)));
        await(() -> !"unset".equals(twin.get()), "the twin walk ran");
        assertTrue(twin.get() != null, "the uncancelled drop spawned");
        await(() -> !server.itemEntities().all().isEmpty(), "the diamond lies on the ground");
        assertEquals(BuiltinItems.DIAMOND, server.itemEntities().all().get(0).stack().type(),
                "the spawned entity carries the stack");
    }

    @Test
    void theProjectileSpawnGateCancelsTheShotBeforeItEntersTheWorld() throws Exception {
        Server api = server;
        AtomicInteger fired = new AtomicInteger();
        AtomicReference<ProjectileKind> seenKind = new AtomicReference<>();
        var sub = api.events().on(ProjectileSpawnEvent.class, event -> {
            fired.incrementAndGet();
            seenKind.set(event.kind());
            event.setCancelled(true);
        });

        // The engine's launch chokepoint (the same body the bow shot and
        // the dispenser's arrow arm ride).
        AtomicReference<Object> launched = new AtomicReference<>("unset");
        server.ticker().submit(() -> launched.set(server.projectiles().launch(
                net.zaminmc.torch.server.entity.projectile.ProjectileEntity.Kind.ARROW,
                -1, new Position(60, 40, 60), 0.0, 0.0, 1.5, 0)));
        await(() -> !"unset".equals(launched.get()), "the launch walk ran");

        assertEquals(1, fired.get(), "the gate fired exactly once");
        assertEquals(ProjectileKind.ARROW, seenKind.get(), "the arrow's public kind rides the event");
        assertNull(launched.get(), "the cancelled shot returns null (no id consumed)");
        assertTrue(server.projectiles().all().isEmpty(),
                "no arrow is in flight after the cancelled shot");

        sub.unsubscribe();

        // The uncancelled twin: the same shot flies.
        AtomicReference<Object> twin = new AtomicReference<>("unset");
        server.ticker().submit(() -> twin.set(server.projectiles().launch(
                net.zaminmc.torch.server.entity.projectile.ProjectileEntity.Kind.ARROW,
                -1, new Position(60, 40, 60), 0.0, 0.0, 1.5, 0)));
        await(() -> !"unset".equals(twin.get()), "the twin walk ran");
        assertTrue(twin.get() != null, "the uncancelled shot launched");
        await(() -> !server.projectiles().all().isEmpty(), "the arrow is in flight");
        assertEquals(1, server.projectiles().all().size(), "exactly one arrow");
        assertFalse(server.projectiles().all().isEmpty());
    }
}
