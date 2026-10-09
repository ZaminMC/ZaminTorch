package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The riding contract over the real wire: the boat item spawns the vehicle
 * on water (Spawn Entity object type 1), the interact takes the seat
 * (Attach Entity riding=1), the steer drives the body (Entity Teleport)
 * and the unmount flag opens it (Attach Entity riding=0).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VehicleIntegrationTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 300_000;
    }

    @Test
    void boatPlaceRideSteerAndDismountOverTheWire() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("BoatRider");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();

            // A 5x5 water pool centered on the join anchor's foot block: the
            // boat spawns in the clicked cell and paddles any heading inside
            // the pool (a 1x1 hole would wall it in — the vanilla refusal).
            var anchor = server.playerRegistry().byName("BoatRider").orElseThrow().position();
            int bx = (int) Math.floor(anchor.x()) + 2;
            int bz = (int) Math.floor(anchor.z());
            int by = (int) Math.floor(anchor.y()) - 1;
            var water = net.zaminmc.torch.server.block.FluidBlocks.sourceOf(
                    net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER);
            server.ticker().submit(() -> {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        server.world().setBlock(
                                new net.zaminmc.torch.block.BlockPosition(bx + dx, by, bz + dz),
                                water);
                    }
                }
            });
            awaitCondition(() -> server.world().getBlock(
                    new net.zaminmc.torch.block.BlockPosition(bx, by, bz)).equals(water)
                    && server.world().getBlock(
                    new net.zaminmc.torch.block.BlockPosition(bx + 2, by, bz + 2)).equals(water),
                    "pool seeded");

            // The boat item into the held slot (the default hotbar slot 0).
            client.sendChat("/give minecraft:boat 1");
            Thread.sleep(200);

            // The placement: use the boat on the water block's top face.
            client.sendBlockPlacement(bx, by, bz, 1, 333);
            int[] spawn = client.readSpawnObjectOfType(1, 20_000);
            int boatId = spawn[0];
            assertTrue(boatId >= 5_000_000, "the vehicle id lives in its own band");

            // The seat: interact takes it (Attach Entity riding=1).
            client.sendUseEntity(boatId, Protocol18.USE_ENTITY_INTERACT);
            int[] mount = client.readAttachEntity(20_000);
            assertEquals(1, mount[2], "the attach names the riding state");
            assertNotEquals(-1, mount[1], "the attach names the vehicle");

            // The steer: forward drives the body (Entity Teleport per tick).
            // A floating boat emits no teleport until it actually moves, so
            // the anchor is the spawn packet's own fixed-point position.
            for (int i = 0; i < 10; i++) {
                client.sendSteerVehicle(0.0f, 1.0f, 0);
                Thread.sleep(60);
            }
            int[] after = client.readEntityTeleportOf(boatId, 20_000);
            boolean moved = after[0] != spawn[2] || after[2] != spawn[4];
            assertTrue(moved, "the steered boat left its spawn spot");

            // The unmount: the jump bit's 0x02 opens the seat.
            client.sendSteerVehicle(0.0f, 0.0f, 2);
            int[] dismount = client.readAttachEntity(20_000);
            assertEquals(0, dismount[2], "the seat cleared over the wire");
        }
    }

    @Test
    void creativeMinecartSpawnsOnRailsAndRides() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("RailRider");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();

            // Creative mode (engine-authoritative), then the creative-menu
            // pick: Set Creative Slot 0 = minecart item 328. The item claim
            // is not a block — the use dispatch must run off the real held
            // stack the creative write populated (the require() crash class).
            var session = server.playerRegistry().byName("RailRider").orElseThrow();
            server.ticker().submit(() -> session.setGamemode(
                    net.zaminmc.torch.GameMode.CREATIVE));
            Thread.sleep(200);
            client.sendCreativeSlot(36, 328); // wire slot 36 = hotbar 0 (held)
            Thread.sleep(200);

            // A straight east-west rail line in the feet cells beside spawn.
            var anchor = server.playerRegistry().byName("RailRider").orElseThrow().position();
            int bx = (int) Math.floor(anchor.x()) + 2;
            int bz = (int) Math.floor(anchor.z());
            int ry = (int) Math.floor(anchor.y()); // the air cell the feet occupy
            var rail = net.zaminmc.torch.server.block.BuiltinBlocks.RAIL_EW;
            server.ticker().submit(() -> {
                for (int dx = -2; dx <= 2; dx++) {
                    server.world().setBlock(
                            new net.zaminmc.torch.block.BlockPosition(bx + dx, ry, bz), rail);
                }
            });
            awaitCondition(() -> server.world().getBlock(
                    new net.zaminmc.torch.block.BlockPosition(bx, ry, bz)).equals(rail),
                    "rail line seeded");

            // The placement: the minecart item on the rail's top face.
            client.sendBlockPlacement(bx, ry, bz, 1, 328);
            int[] spawn = client.readSpawnObjectOfType(10, 20_000);
            int cartId = spawn[0];
            assertTrue(cartId >= 5_000_000, "the vehicle id lives in its own band");

            // The seat: the mount attach, then the steer drives the cart.
            client.sendUseEntity(cartId, Protocol18.USE_ENTITY_INTERACT);
            int[] mount = client.readAttachEntity(20_000);
            assertEquals(1, mount[2], "the creative mount took the seat");
            for (int i = 0; i < 12; i++) {
                client.sendSteerVehicle(0.0f, 1.0f, 0);
                Thread.sleep(60);
            }
            // The first per-tick teleport can floor to the spawn's own
            // fixed-point cell (0.02 blocks is 0.64 units), so drain the
            // stream until a teleport actually differs from the spawn.
            int[] after = client.readEntityTeleportOf(cartId, 20_000);
            long movedDeadline = System.currentTimeMillis() + 5_000;
            while (after[0] == spawn[2] && after[2] == spawn[4]
                    && System.currentTimeMillis() < movedDeadline) {
                after = client.readEntityTeleportOf(cartId,
                        movedDeadline - System.currentTimeMillis());
            }
            assertTrue(after[0] != spawn[2] || after[2] != spawn[4],
                    "the steered cart left its spawn spot");

            // The unmount clears the seat like the boat's.
            client.sendSteerVehicle(0.0f, 0.0f, 2);
            int[] dismount = client.readAttachEntity(20_000);
            assertEquals(0, dismount[2], "the cart seat cleared");
        }
    }
}
