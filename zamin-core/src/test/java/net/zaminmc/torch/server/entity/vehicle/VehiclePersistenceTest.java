package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vehicle persistence round trip (Slice 25g — the ZVD store): the
 * whole family's pre-25g gap (the carts carried no store at all) closes —
 * a chest minecart's position, health, and 27-slot cargo, a plain
 * minecart, and a boat all survive the shutdown-restart cycle exactly
 * like the reference's own entity NBT.
 */
class VehiclePersistenceTest {

    @TempDir
    Path dataDir;

    @Test
    void theVehiclesAndTheirCargoSurviveTheRestart() throws Exception {
        String world = "vdpr" + Math.abs(UUID.randomUUID().hashCode() % 100000);

        // --- first life: spawn the vehicles, load the cargo, save ---------
        EngineConfig first = new EngineConfig("127.0.0.1", 0, world, "it", 20, 2, 20,
                dataDir.toString());
        EngineServer one = new EngineServer(first);
        one.start();
        // The surface the flat generator builds (the carts settle on the
        // ground instead of falling — the store carries the settled body).
        int ground = groundAt(one, 8, 8);
        assertTrue(ground > 0, "the flat ground exists");
        one.ticker().submit(() -> {
            VehicleEntity chestCart = one.vehicles().spawnChestMinecart(
                    new Position(8.5, ground + 1.0, 8.5));
            if (chestCart instanceof ChestMinecartEntity cart) {
                cart.chest().setSlot(0, ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.DIAMOND, 7));
                cart.chest().setSlot(26, ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.IRON_INGOT, 3));
            }
            // A wounded cart (the health carry: 4.0 - one hit = 3.0).
            one.vehicles().spawnMinecart(new Position(12.5, ground + 1.0, 12.5));
            one.vehicles().spawnBoat(new Position(16.5, ground + 1.0, 16.5));
        });
        await(() -> one.vehicles().all().size() == 3, "the three vehicles spawned");
        // Let the bodies settle (the carts ride their physics onto the
        // ground; the settled position is what the store carries).
        Thread.sleep(600);
        // Wound the cart inside its i-frame window (one hit = one damage).
        VehicleEntity wounded = one.vehicles().all().stream()
                .filter(v -> v.kind() == VehicleEntity.Kind.MINECART).findFirst()
                .orElseThrow();
        one.ticker().submit(() -> wounded.hurt(1.0f));
        await(() -> wounded.health() < VehicleEntity.MAX_HEALTH, "the minecart took the hit");
        float woundedHealth = wounded.health();
        // The settled positions (read ON THE TICK LANE: the settled y the
        // store carries, captured atomically with the save decision).
        double[] settled = new double[3];
        one.ticker().submit(() -> {
            for (VehicleEntity vehicle : one.vehicles().all()) {
                if (vehicle.kind() == VehicleEntity.Kind.CHEST_MINECART) {
                    settled[0] = vehicle.position().y();
                } else if (vehicle.kind() == VehicleEntity.Kind.BOAT) {
                    settled[1] = vehicle.position().x();
                }
            }
        });
        await(() -> settled[0] != 0.0, "the settled positions captured");
        double chestCartY = settled[0];
        double boatX = settled[1];
        one.saveAllNow();
        one.shutdown(null);
        awaitState(one);

        // --- second life: fresh process state, same data -------------------
        EngineConfig second = new EngineConfig("127.0.0.1", 0, world, "it", 20, 2, 20,
                dataDir.toString());
        EngineServer two = new EngineServer(second);
        two.start();
        try {
            await(() -> two.vehicles().all().size() == 3,
                    "the whole family restored");
            assertEquals(3, two.vehicles().all().size());

            // The chest minecart: position, kind, and cargo.
            ChestMinecartEntity cart = (ChestMinecartEntity) two.vehicles().all().stream()
                    .filter(v -> v.kind() == VehicleEntity.Kind.CHEST_MINECART)
                    .findFirst().orElseThrow();
            assertEquals(8.5, cart.position().x(), 1e-9, "the cart's x survived");
            // The restored body sits at the saved y or just below it (the
            // restored body continues its own physics from the load — a
            // settled cart stays settled, so the band is tight).
            assertTrue(cart.position().y() <= chestCartY + 1e-9
                            && cart.position().y() > chestCartY - 0.5,
                    "the cart's settled y survived: " + cart.position().y()
                            + " vs " + chestCartY);
            assertEquals(8.5, cart.position().z(), 1e-9, "the cart's z survived");
            assertEquals(7, cart.chest().snapshotSlots()[0].count(),
                    "the diamond stack survived");
            assertEquals(net.zaminmc.torch.server.item.BuiltinItems.DIAMOND,
                    cart.chest().snapshotSlots()[0].type());
            assertEquals(3, cart.chest().snapshotSlots()[26].count(),
                    "the tail slot's iron survived");
            assertTrue(cart.chest().snapshotSlots()[13].isEmpty(),
                    "the middle slots stay empty");

            // The plain minecart: the health carry.
            VehicleEntity minecart = two.vehicles().all().stream()
                    .filter(v -> v.kind() == VehicleEntity.Kind.MINECART)
                    .findFirst().orElseThrow();
            assertEquals(woundedHealth, minecart.health(), 1e-6f,
                    "the wounded health survived");

            // The boat: position.
            VehicleEntity boat = two.vehicles().all().stream()
                    .filter(v -> v.kind() == VehicleEntity.Kind.BOAT)
                    .findFirst().orElseThrow();
            assertEquals(boatX, boat.position().x(), 0.5, "the boat's settled x survived");
        } finally {
            two.shutdown(null);
        }
    }

    /** The topmost non-air block's y at the column (the flat ground). */
    private static int groundAt(EngineServer server, int x, int z) {
        for (int y = 255; y >= 0; y--) {
            if (!server.world().getBlock(
                    new net.zaminmc.torch.block.BlockPosition(x, y, z))
                    .equals(server.world().airType())) {
                return y;
            }
        }
        return -1;
    }

    private static void awaitState(EngineServer server) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 8_000;
        while (System.currentTimeMillis() < deadline) {
            if (server.state() == net.zaminmc.torch.ServerState.STOPPED) {
                return;
            }
            Thread.sleep(25);
        }
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
