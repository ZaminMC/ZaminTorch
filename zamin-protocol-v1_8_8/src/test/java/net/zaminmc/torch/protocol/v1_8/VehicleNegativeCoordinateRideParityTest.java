package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAVE VR — THE NEGATIVE-COORDINATE RIDE WIRE (the owner's report read
 * whole: 'placing rails and minecart on top of rails do not render,
 * unable to see minecarts on rails they do not render, sitting in one of
 * them is glitchy too, in second person im entirely invisible sitting on
 * minecart'). The root cause the stream diagnostic caught: the vehicle
 * move broadcast's fixed-point change key packed its lanes WITHOUT
 * masks, so any position with a NEGATIVE z collapsed every same-z-cell
 * position onto one key and the move broadcast NEVER fired south/west
 * of origin — the cart ran its rail physics server-side while the rider
 * and every observer saw a body frozen at its spawn (the reported
 * invisible cart: it never appeared to be ON the rails it rolled along,
 * and the mounted camera never followed it). The riding wire itself
 * carried three more non-1:1 arms the same diagnostic exposed, each
 * pinned here against the reference's own law.
 */
class VehicleNegativeCoordinateRideParityTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 120_000;
    }

    /**
     * SEAT 1 — THE NEGATIVE-COORDINATE MOVE BROADCAST (the lane-mask law
     * itself): a steered cart at negative x AND z must broadcast its
     * move family over the wire and actually leave its spawn cell
     * server-side. RED on the pre-fix engine: the un-masked z lane
     * drowned the x/y lanes, onVehicleMoved never fired, and the
     * readEntityMoveFamilyOf poll timed out with the cart frozen.
     */
    @Test
    void theSteeredCartBroadcastsItsMovesAtNegativeCoordinates() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("NegCoordRider");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            var session = server.playerRegistry().byName("NegCoordRider").orElseThrow();
            server.ticker().submit(() -> session.setGamemode(
                    net.zaminmc.torch.GameMode.CREATIVE));
            awaitCondition(() -> session.gamemode()
                    == net.zaminmc.torch.GameMode.CREATIVE, "creative");
            client.sendCreativeSlot(36, 328);
            Thread.sleep(200);

            // The honest walk into the negative quadrant — both lanes
            // negative, the exact shape the un-masked packing drowned.
            double targetX = -14.5;
            double targetZ = -12.5;
            client.walkTo(targetX, 5.0, targetZ, true);
            awaitCondition(() -> Math.abs(session.position().x() - targetX) < 0.01
                    && Math.abs(session.position().z() - targetZ) < 0.01,
                    "the walk reached the negative quadrant");

            var anchor = new net.zaminmc.torch.util.Position(targetX, 5.0, targetZ);
            int bx = (int) Math.floor(anchor.x());
            int bz = (int) Math.floor(anchor.z());
            int ry = (int) Math.floor(anchor.y());
            var rail = net.zaminmc.torch.server.block.BuiltinBlocks.RAIL_EW;
            server.ticker().submit(() -> {
                for (int dx = -2; dx <= 6; dx++) {
                    server.world().setBlock(
                            new net.zaminmc.torch.block.BlockPosition(bx + dx, ry - 1, bz),
                            net.zaminmc.torch.server.block.BuiltinBlocks.GRASS_BLOCK);
                    server.world().setBlock(
                            new net.zaminmc.torch.block.BlockPosition(bx + dx, ry, bz), rail);
                }
            });
            awaitCondition(() -> server.world().getBlock(
                    new net.zaminmc.torch.block.BlockPosition(bx, ry, bz)).equals(rail),
                    "rail line seeded");

            client.sendBlockPlacement(bx, ry, bz, 1, 328);
            // The join snapshot re-spawns the earlier seats' leftover carts
            // (the class shares one world); the poll drains until THIS
            // test's own cart — the one on the negative-quadrant cell —
            // the shared-world convention the integration rig uses.
            int cartId = -1;
            long spawnDeadline = System.currentTimeMillis() + 20_000;
            while (cartId == -1 && System.currentTimeMillis() < spawnDeadline) {
                int[] spawn = client.readSpawnOfMinecartType(0,
                        Math.max(1, spawnDeadline - System.currentTimeMillis()));
                if (spawn[2] < 0 && spawn[4] < 0) {
                    cartId = spawn[0];
                }
            }
            assertTrue(cartId >= 0,
                    "the spawn itself sits in the negative quadrant");

            client.sendUseEntity(cartId, Protocol18.USE_ENTITY_INTERACT);
            int[] mount = client.readAttachEntity(20_000);
            assertEquals(1, mount[2], "the mount took the seat");

            for (int i = 0; i < 16; i++) {
                client.sendSteerVehicle(0.0f, 1.0f, 0);
                Thread.sleep(60);
            }
            // THE MOVE FAMILY: the cart's own packets must arrive — the
            // rider's camera and every observer's seated-body anchor ride
            // them (the pre-fix engine sent NONE of them here).
            int[] move = client.readEntityMoveFamilyOf(cartId, 20_000);
            assertTrue(move[0] == Protocol18.S2C_ENTITY_TELEPORT
                    || move[0] == Protocol18.S2C_REL_ENTITY_MOVE
                    || move[0] == Protocol18.S2C_REL_ENTITY_MOVE_LOOK,
                    "the move family packet arrived");
            // And the body genuinely left its spawn server-side (the
            // frozen-stream symptom the owner saw).
            var cart = server.vehicles().byId(cartId);
            assertNotEquals(null, cart, "the cart lives");
            assertTrue(Math.abs(cart.position().x() - bx - 0.5) > 0.5,
                    "the cart rolled away from its spawn cell");
        }
        awaitEmptyServer();
    }

    /**
     * SEAT 2 — THE RIDE'S POSITION-AND-LOOK LAW (the reference's riding
     * arm, handlePlayerMove lines 215-252 + startRiding lines 516-522):
     * the rider receives EXACTLY ONE Position-And-Look — the mount
     * anchor — and NOTHING per move afterwards (the retired engine
     * re-synced the rider every move, fighting the client's own vehicle
     * interpolation twenty times a second: the camera-snap 'sitting in
     * one of them is glitchy'). Run at the spawn's positive coordinates
     * where the retired arm fired every move, so the seat is RED on the
     * pre-fix engine and GREEN on the landed law.
     */
    @Test
    void theRideCarriesTheMountAnchorAloneNeverPerMoveResyncs() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("AnchorRider");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            var session = server.playerRegistry().byName("AnchorRider").orElseThrow();
            server.ticker().submit(() -> session.setGamemode(
                    net.zaminmc.torch.GameMode.CREATIVE));
            awaitCondition(() -> session.gamemode()
                    == net.zaminmc.torch.GameMode.CREATIVE, "creative");
            client.sendCreativeSlot(36, 328);
            Thread.sleep(200);

            var anchor = session.position();
            int bx = (int) Math.floor(anchor.x()) + 2;
            int bz = (int) Math.floor(anchor.z());
            int ry = (int) Math.floor(anchor.y());
            var rail = net.zaminmc.torch.server.block.BuiltinBlocks.RAIL_EW;
            server.ticker().submit(() -> {
                for (int dx = -2; dx <= 6; dx++) {
                    server.world().setBlock(
                            new net.zaminmc.torch.block.BlockPosition(bx + dx, ry - 1, bz),
                            net.zaminmc.torch.server.block.BuiltinBlocks.GRASS_BLOCK);
                    server.world().setBlock(
                            new net.zaminmc.torch.block.BlockPosition(bx + dx, ry, bz), rail);
                }
            });
            awaitCondition(() -> server.world().getBlock(
                    new net.zaminmc.torch.block.BlockPosition(bx, ry, bz)).equals(rail),
                    "rail line seeded");

            client.sendBlockPlacement(bx, ry, bz, 1, 328);
            // The join snapshot may re-spawn leftover carts from the
            // earlier seat (the class shares one world); the poll drains
            // until THIS test's own cart on the freshly seeded cell.
            int cartId = -1;
            long spawnDeadline = System.currentTimeMillis() + 20_000;
            while (cartId == -1 && System.currentTimeMillis() < spawnDeadline) {
                int[] spawn = client.readSpawnOfMinecartType(0,
                        Math.max(1, spawnDeadline - System.currentTimeMillis()));
                if (spawn[2] / 32 == bx && spawn[4] / 32 == bz) {
                    cartId = spawn[0];
                }
            }
            assertTrue(cartId >= 0, "the fresh cart's spawn arrived");

            client.sendUseEntity(cartId, Protocol18.USE_ENTITY_INTERACT);
            int[] mount = client.readAttachEntity(20_000);
            assertEquals(1, mount[2], "the mount took the seat");
            // THE MOUNT ANCHOR: exactly one Position-And-Look beside the
            // attach (the reference's networkHandler.teleport at
            // startRiding) — it may sit in the queue ahead of or behind
            // the attach packet, so the poll starts after both.
            int anchors = 0;
            long anchorDeadline = System.currentTimeMillis() + 3_000;
            client.setReadTimeout(200);
            while (System.currentTimeMillis() < anchorDeadline) {
                byte[] payload;
                try {
                    payload = client.readPacketForDiagnostics();
                } catch (Exception quiet) {
                    continue;
                }
                if (client.readPacketId(payload)
                        == Protocol18.S2C_PLAYER_POSITION_AND_LOOK) {
                    anchors++;
                }
            }
            assertEquals(1, anchors,
                    "exactly ONE mount anchor Position-And-Look (startRiding's own teleport)");

            // THE RIDE ITSELF: steer hard, drain everything — the cart's
            // move family is welcome, the rider's Position-And-Look is
            // NOT (the retired per-move resync).
            int resyncs = 0;
            int cartMoves = 0;
            Thread steerer = new Thread(() -> {
                try {
                    long deadline = System.currentTimeMillis() + 3_000;
                    while (System.currentTimeMillis() < deadline) {
                        client.sendSteerVehicle(0.0f, 1.0f, 0);
                        Thread.sleep(60);
                    }
                } catch (Exception ignored) {
                }
            }, "anchor-seat-steerer");
            steerer.start();
            long rideDeadline = System.currentTimeMillis() + 3_000;
            while (System.currentTimeMillis() < rideDeadline) {
                byte[] payload;
                try {
                    payload = client.readPacketForDiagnostics();
                } catch (Exception quiet) {
                    continue;
                }
                int id = client.readPacketId(payload);
                if (id == Protocol18.S2C_PLAYER_POSITION_AND_LOOK) {
                    resyncs++;
                }
                if (id == Protocol18.S2C_ENTITY_TELEPORT
                        || id == Protocol18.S2C_REL_ENTITY_MOVE
                        || id == Protocol18.S2C_REL_ENTITY_MOVE_LOOK) {
                    cartMoves++;
                }
            }
            steerer.join();
            assertEquals(0, resyncs,
                    "the ride never re-syncs the rider per move (the reference's law)");
            assertTrue(cartMoves > 0,
                    "the cart's own move family carried the camera instead");
        }
        awaitEmptyServer();
    }

    /**
     * SEAT 3 — THE SEATED RIDER'S LOOK (the reference's riding arm,
     * handlePlayerMove lines 218-227): the riding client sends LOOK-ONLY
     * packets (EntityPlayerSP's C05) and the reference lands them at the
     * seat ({@code updatePositionAndAngles(d17, d18, d19, f4, f)}); the
     * retired whole-packet discard dropped the rider's LOOK server-side
     * too — the cart's own steer consult read the stale mount-time yaw
     * (the reported 'sitting in one is glitchy').
     */
    @Test
    void theSeatedRidersLookLandsAtTheSeat() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("LookingRider");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            var session = server.playerRegistry().byName("LookingRider").orElseThrow();
            server.ticker().submit(() -> session.setGamemode(
                    net.zaminmc.torch.GameMode.CREATIVE));
            awaitCondition(() -> session.gamemode()
                    == net.zaminmc.torch.GameMode.CREATIVE, "creative");
            client.sendCreativeSlot(36, 328);
            Thread.sleep(200);

            var anchor = session.position();
            int bx = (int) Math.floor(anchor.x()) + 2;
            int bz = (int) Math.floor(anchor.z());
            int ry = (int) Math.floor(anchor.y());
            var rail = net.zaminmc.torch.server.block.BuiltinBlocks.RAIL_EW;
            server.ticker().submit(() -> {
                for (int dx = -2; dx <= 2; dx++) {
                    server.world().setBlock(
                            new net.zaminmc.torch.block.BlockPosition(bx + dx, ry - 1, bz),
                            net.zaminmc.torch.server.block.BuiltinBlocks.GRASS_BLOCK);
                    server.world().setBlock(
                            new net.zaminmc.torch.block.BlockPosition(bx + dx, ry, bz), rail);
                }
            });
            awaitCondition(() -> server.world().getBlock(
                    new net.zaminmc.torch.block.BlockPosition(bx, ry, bz)).equals(rail),
                    "rail line seeded");

            client.sendBlockPlacement(bx, ry, bz, 1, 328);
            // The shared-world leftover drain (the earlier seats' carts
            // re-spawn in this client's join snapshot; only the fresh
            // cell's cart is this test's own).
            int cartId = -1;
            long spawnDeadline = System.currentTimeMillis() + 20_000;
            while (cartId == -1 && System.currentTimeMillis() < spawnDeadline) {
                int[] spawn = client.readSpawnOfMinecartType(0,
                        Math.max(1, spawnDeadline - System.currentTimeMillis()));
                if (spawn[2] / 32 == bx && spawn[4] / 32 == bz) {
                    cartId = spawn[0];
                }
            }
            assertTrue(cartId >= 0, "the fresh cart's spawn arrived");
            client.sendUseEntity(cartId, Protocol18.USE_ENTITY_INTERACT);
            int[] mount = client.readAttachEntity(20_000);
            assertEquals(1, mount[2], "the mount took the seat");
            var cart = server.vehicles().byId(cartId);
            assertNotEquals(null, cart, "the cart lives");
            // The per-tick seat anchor lands first (the reference's
            // updateRiderPositon): the body sits at the cart's own seat
            // before the look law is pinned against it.
            awaitCondition(() -> Math.abs(session.position().x()
                    - cart.position().x()) < 1e-9, "the seat anchored at the cart");
            var seatAtLook = session.position();

            // The riding client's own look-only packet: a 90-degree turn.
            client.sendLook(90.0f, 30.0f, true);
            awaitCondition(() -> Math.abs(session.rotation().yaw() - 90.0f) < 0.01f,
                    "the seated rider's look landed");
            assertEquals(30.0f, session.rotation().pitch(), 0.01f,
                    "the pitch landed with the yaw");
            // The seat owns the coordinates: the look never moved the body.
            assertEquals(seatAtLook.x(), session.position().x(), 1e-9,
                    "the seat kept its X");
            assertEquals(seatAtLook.z(), session.position().z(), 1e-9,
                    "the seat kept its Z");
        }
        awaitEmptyServer();
    }
}
