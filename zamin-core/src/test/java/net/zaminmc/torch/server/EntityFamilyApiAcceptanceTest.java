package net.zaminmc.torch.server;

import net.zaminmc.torch.entity.DroppedItem;
import net.zaminmc.torch.entity.EntitySnapshot;
import net.zaminmc.torch.entity.ExperienceOrb;
import net.zaminmc.torch.entity.FallingBlock;
import net.zaminmc.torch.entity.Projectile;
import net.zaminmc.torch.entity.ProjectileKind;
import net.zaminmc.torch.entity.Vehicle;
import net.zaminmc.torch.entity.VehicleKind;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The public entity-family surfaces (§27.3 — the entity/projectile/
 * falling-block/vehicle/XP-orb families) over the live engine: a
 * plugin-style consumer reaches every engine population through the
 * {@code World} interface alone — no engine internals in the assertion
 * paths — and consumes {@link EntitySnapshot}s off-owner (§27.6).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EntityFamilyApiAcceptanceTest {

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
    void theDroppedItemFamilyIsReachableThroughTheWorld() throws Exception {
        server = boot();
        join("Dropper");
        Position at = new Position(8.5, 8.0, 8.5);
        // Engine-side setup: a block drop appears (the survival break's own path).
        server.itemEntities().spawnDropAtBlock(at,
                new ItemStack(BuiltinItems.STICK, 1), ItemEntityPickup.DROP);

        AtomicReference<DroppedItem> handle = new AtomicReference<>();
        await(() -> {
            server.world().execute(() -> {
                for (DroppedItem item : server.world().itemEntities()) {
                    if (item.stack().type().identifier().value().equals("stick")) {
                        handle.set(item);
                    }
                }
            });
            return handle.get() != null;
        }, "the drop appeared in the public item view");

        DroppedItem drop = handle.get();
        assertEquals(Identifier.parse("minecraft:item"), drop.typeIdentifier());
        assertEquals(1, drop.stack().count());
        // The await loop crosses engine ticks — the delay counts down from
        // the spawn's 10; the assertion pins the band, not the exact tick.
        assertTrue(drop.pickupDelayTicks() >= 0 && drop.pickupDelayTicks() <= 10,
                "the pickup delay counts down from the drop's 10 (was "
                        + drop.pickupDelayTicks() + ")");
        assertEquals(at.x(), drop.position().x(), 1.0);
        // The snapshot is freely consumable off-owner (§27.6).
        EntitySnapshot snapshot = drop.snapshot();
        assertEquals(Identifier.parse("minecraft:item"), snapshot.type());
        server.shutdown(null);
    }

    private static final class ItemEntityPickup {
        /** The engine's own block-drop pickup delay (0.5s). */
        static final int DROP = 10;
    }

    @Test
    void theProjectileFamilyIsReachableThroughTheWorld() throws Exception {
        server = boot();
        PlayerSession shooter = join("Shooter");
        server.projectiles().launch(ProjectileEntity.Kind.ARROW, shooter.engineEntityId(),
                new Position(8.5, 10.0, 8.5), 0.0, -20.0, 3.0, 0);

        AtomicReference<Projectile> handle = new AtomicReference<>();
        await(() -> {
            server.world().execute(() -> {
                for (Projectile projectile : server.world().projectiles()) {
                    if (projectile.projectileKind() == ProjectileKind.ARROW) {
                        handle.set(projectile);
                    }
                }
            });
            return handle.get() != null;
        }, "the arrow appeared in the public projectile view");

        Projectile arrow = handle.get();
        assertEquals(Identifier.parse("minecraft:arrow"), arrow.typeIdentifier());
        assertEquals(shooter.engineEntityId(), arrow.throwerEntityId());
        assertTrue(arrow.age() >= 0 && arrow.age() <= 5,
                "the fresh arrow's age is the first flight ticks (was " + arrow.age() + ")");
        server.shutdown(null);
    }

    @Test
    void theFallingBlockFamilyIsReachableThroughTheWorld() throws Exception {
        server = boot();
        join("Digger");
        // Engine-side setup: a sand column's top block starts falling.
        server.world().execute(() -> server.world().setBlock(
                new net.zaminmc.torch.block.BlockPosition(8, 20, 8),
                net.zaminmc.torch.server.block.BuiltinBlocks.SAND));
        server.fallingEntities().startFall(
                new net.zaminmc.torch.block.BlockPosition(8, 20, 8),
                net.zaminmc.torch.server.block.BuiltinBlocks.SAND);

        AtomicReference<FallingBlock> handle = new AtomicReference<>();
        await(() -> {
            server.world().execute(() -> {
                for (FallingBlock block : server.world().fallingBlocks()) {
                    handle.set(block);
                }
            });
            return handle.get() != null;
        }, "the falling body appeared in the public falling-block view");

        FallingBlock body = handle.get();
        assertEquals("sand", body.block().identifier().value());
        assertEquals(Identifier.parse("minecraft:falling_block"), body.typeIdentifier());
        server.shutdown(null);
    }

    @Test
    void theVehicleAndOrbFamiliesAreReachableThroughTheWorld() throws Exception {
        server = boot();
        join("Sailor");
        server.vehicles().spawnBoat(new Position(8.5, 8.0, 8.5));
        server.experienceOrbs().spawnOrb(new Position(12.5, 8.0, 12.5), 3);

        AtomicReference<Vehicle> boat = new AtomicReference<>();
        AtomicReference<ExperienceOrb> orb = new AtomicReference<>();
        await(() -> {
            server.world().execute(() -> {
                for (Vehicle vehicle : server.world().vehicles()) {
                    boat.set(vehicle);
                }
                for (ExperienceOrb candidate : server.world().experienceOrbs()) {
                    orb.set(candidate);
                }
            });
            return boat.get() != null && orb.get() != null;
        }, "the boat and the orb appeared in the public views");

        assertEquals(VehicleKind.BOAT, boat.get().vehicleKind());
        assertEquals(Identifier.parse("minecraft:boat"), boat.get().typeIdentifier());
        assertEquals(-1, boat.get().riderEntityId(), "the fresh boat's seat is empty");
        assertEquals(4.0f, boat.get().health(), 0.0f);
        assertEquals(3, orb.get().amount());
        assertEquals(Identifier.parse("minecraft:xp_orb"), orb.get().typeIdentifier());

        // The off-owner consumption (§27.6): the snapshot crosses a foreign
        // thread freely after the owner-context capture.
        EntitySnapshot[] captured = new EntitySnapshot[1];
        server.world().execute(() -> captured[0] = boat.get().snapshot());
        await(() -> captured[0] != null, "the owner-context capture landed");
        CountDownLatch consumed = new CountDownLatch(1);
        Thread pluginThread = new Thread(() -> {
            if (captured[0] != null
                    && Identifier.parse("minecraft:boat").equals(captured[0].type())) {
                consumed.countDown();
            }
        }, "plugin-thread");
        pluginThread.start();
        assertTrue(consumed.await(3, TimeUnit.SECONDS),
                "the snapshot consumed off-owner carried the boat's identity");
        pluginThread.join();
        server.shutdown(null);
    }
}
