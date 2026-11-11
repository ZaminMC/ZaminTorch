package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAVE TR-2 — THE TRACKING-RANGE FAMILY ROLL-OUT over the real adapter
 * (the reference's EntityMap per-type table beyond the mobs — Paper
 * 0216's follow-up row, the architecture's named roll-out): every OTHER
 * tracked wire family consults its own ±range box at its spawn gate and
 * re-evaluates on every move — a body beyond its box NEVER reaches the
 * observer, a crossed-out body is destroyed on the observer's client
 * while it keeps living server-side, and a crossed-in body re-spawns
 * through the full chain (the SAME id). The retired wire fanned every
 * family's events to EVERY same-dimension observer with no range
 * consult at all. The seats pin the roll-out over the shared view-4 rig
 * (the registration's clamp: every row answers 48 there).
 */
class TrackingRangeFamilyWireParityTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 120_000;
    }

    /** Drains spawn-object packets until the wanted entity id's own
     * spawn arrives (the join snapshot may deliver other bodies first —
     * the shared-world convention). */
    private int[] readSpawnOfEntity(int entityId, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        client().setReadTimeout(200);
        while (System.currentTimeMillis() < deadline) {
            byte[] payload;
            try {
                payload = client().readPacketForDiagnostics();
            } catch (Exception quiet) {
                continue; // the read timeout's own drain cadence
            }
            if (client().readPacketId(payload) != Protocol18.S2C_SPAWN_ENTITY) {
                continue;
            }
            var buffer = io.netty.buffer.Unpooled.wrappedBuffer(payload);
            net.zaminmc.torch.protocol.v1_8.ByteBufOps.readVarInt(buffer); // packet id
            if (net.zaminmc.torch.protocol.v1_8.ByteBufOps.readVarInt(buffer) == entityId) {
                return new int[] {entityId};
            }
        }
        throw new AssertionError("no spawn object packet for entity " + entityId
                + " within " + timeoutMs + "ms");
    }

    /** True when a spawn-object packet for the entity arrives inside the
     * window (drains everything; the caller asserts the negative). */
    private boolean spawnArrivesWithin(int entityId, long windowMs) throws Exception {
        long deadline = System.currentTimeMillis() + windowMs;
        client().setReadTimeout(200);
        while (System.currentTimeMillis() < deadline) {
            byte[] payload;
            try {
                payload = client().readPacketForDiagnostics();
            } catch (Exception quiet) {
                continue;
            }
            if (client().readPacketId(payload) == Protocol18.S2C_SPAWN_ENTITY) {
                var buffer = io.netty.buffer.Unpooled.wrappedBuffer(payload);
                net.zaminmc.torch.protocol.v1_8.ByteBufOps.readVarInt(buffer); // packet id
                if (net.zaminmc.torch.protocol.v1_8.ByteBufOps.readVarInt(buffer) == entityId) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Drains destroy-entities packets until the wanted id's destroy
     * arrives. */
    private void readDestroyOfEntity(int entityId, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        client().setReadTimeout(200);
        while (System.currentTimeMillis() < deadline) {
            byte[] payload;
            try {
                payload = client().readPacketForDiagnostics();
            } catch (Exception quiet) {
                continue;
            }
            if (client().readPacketId(payload) != Protocol18.S2C_DESTROY_ENTITIES) {
                continue;
            }
            var buffer = io.netty.buffer.Unpooled.wrappedBuffer(payload);
            net.zaminmc.torch.protocol.v1_8.ByteBufOps.readVarInt(buffer); // packet id
            int count = net.zaminmc.torch.protocol.v1_8.ByteBufOps.readVarInt(buffer);
            for (int i = 0; i < count; i++) {
                if (net.zaminmc.torch.protocol.v1_8.ByteBufOps.readVarInt(buffer) == entityId) {
                    return;
                }
            }
        }
        throw new AssertionError("no destroy packet for entity " + entityId
                + " within " + timeoutMs + "ms");
    }

    private TestClient18 client;

    private TestClient18 client() {
        return client;
    }

    /**
     * SEAT 1 — THE ITEM'S SPAWN GATE + THE WALK'S ADD ARM: a dropped item
     * 61 blocks east (inside the view-4 chunk window, OUTSIDE the clamped
     * 48 box) NEVER spawns — not at its own spawn, not at any observer's
     * join. The player walking into its box spawns it through the full
     * chain (the walk's own >4-block cadence). RED on the pre-fix engine:
     * the retired wire spawned every same-dimension body however far.
     */
    @Test
    void theFarItemNeverSpawnsUntilThePlayerWalksIntoItsBox() throws Exception {
        try (TestClient18 joined = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client = joined;
            joined.sendHandshake(47, 2);
            joined.sendLoginStart("FamilyRangeItem");
            joined.readLoginSuccess();
            joined.readUntilPositionAndLook();
            var session = server.playerRegistry().byName("FamilyRangeItem").orElseThrow();
            double sx = session.position().x();
            double sy = session.position().y();
            double sz = session.position().z();

            // The far drop: 61 blocks east (chunk 3-4 away, inside the
            // loaded window, 13 blocks past the 48 box).
            final net.zaminmc.torch.server.entity.ItemEntity[] far = new net.zaminmc.torch.server.entity.ItemEntity[1];
            server.ticker().submit(() -> far[0] = server.itemEntities().spawnDropAtBlock(
                    new net.zaminmc.torch.util.Position(sx + 61, sy, sz),
                    net.zaminmc.torch.item.ItemStack.of(
                            net.zaminmc.torch.server.item.BuiltinItems.DIAMOND, 1),
                    40, 0));
            awaitCondition(() -> far[0] != null, "the item spawned server-side");
            assertFalse(spawnArrivesWithin(far[0].entityId(), 2_500),
                    "an item 13 blocks past its box NEVER spawns (the retired wire did)");

            // The player walks east into the box: the walk's add arm spawns
            // the item through the full chain.
            joined.walkTo(sx + 55, sy, sz, true);
            int[] spawn = readSpawnOfEntity(far[0].entityId(), 20_000);
            assertEquals(far[0].entityId(), spawn[0],
                    "the walk's add arm spawned the newly in-box item");
            server.ticker().submit(() -> server.itemEntities().remove(far[0], "test"));
        }
        awaitEmptyServer();
    }

    /**
     * SEAT 2 — THE WALKED-AWAY ITEM IS DESTROYED BUT LIVES SERVER-SIDE
     * (the player-side remove arm): a spawned item the player walks away
     * from is destroyed on the client while it keeps living
     * server-side — TrackedEntity.updatePlayer's remove arm over the
     * item family.
     */
    @Test
    void theWalkedAwayItemIsDestroyedButLivesServerSide() throws Exception {
        try (TestClient18 joined = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client = joined;
            joined.sendHandshake(47, 2);
            joined.sendLoginStart("FamilyRangeLeave");
            joined.readLoginSuccess();
            joined.readUntilPositionAndLook();
            var session = server.playerRegistry().byName("FamilyRangeLeave").orElseThrow();
            double sx = session.position().x();
            double sy = session.position().y();
            double sz = session.position().z();

            final net.zaminmc.torch.server.entity.ItemEntity[] near = new net.zaminmc.torch.server.entity.ItemEntity[1];
            server.ticker().submit(() -> near[0] = server.itemEntities().spawnDropAtBlock(
                    new net.zaminmc.torch.util.Position(sx + 10, sy, sz),
                    net.zaminmc.torch.item.ItemStack.of(
                            net.zaminmc.torch.server.item.BuiltinItems.DIAMOND, 1),
                    40, 0));
            awaitCondition(() -> near[0] != null, "the item spawned server-side");
            readSpawnOfEntity(near[0].entityId(), 10_000);

            // The player walks west, out of the item's box: the walk's
            // remove arm destroys it on this client.
            joined.walkTo(sx - 60, sy, sz, true);
            readDestroyOfEntity(near[0].entityId(), 20_000);
            assertTrue(!near[0].stack().isEmpty(),
                    "the item keeps living server-side (the destroy is the observer's alone)");
            server.ticker().submit(() -> server.itemEntities().remove(near[0], "test"));
        }
        awaitEmptyServer();
    }

    /**
     * SEAT 3 — THE VEHICLE ROW (the minecart/boat's own 80, EntityMap
     * lines 94-97, clamped to 48 on the view-4 rig): a boat 61 blocks
     * east never spawns; the player walking into its box spawns it.
     */
    @Test
    void theFarBoatNeverSpawnsUntilThePlayerWalksIntoItsBox() throws Exception {
        try (TestClient18 joined = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client = joined;
            joined.sendHandshake(47, 2);
            joined.sendLoginStart("FamilyRangeBoat");
            joined.readLoginSuccess();
            joined.readUntilPositionAndLook();
            var session = server.playerRegistry().byName("FamilyRangeBoat").orElseThrow();
            double sx = session.position().x();
            double sy = session.position().y();
            double sz = session.position().z();

            final net.zaminmc.torch.server.entity.vehicle.VehicleEntity[] boat =
                    new net.zaminmc.torch.server.entity.vehicle.VehicleEntity[1];
            server.ticker().submit(() -> boat[0] = server.vehicles().spawnBoat(
                    new net.zaminmc.torch.util.Position(sx + 61, sy, sz), 0));
            awaitCondition(() -> boat[0] != null, "the boat spawned server-side");
            assertFalse(spawnArrivesWithin(boat[0].entityId(), 2_500),
                    "a boat 13 blocks past its clamped box NEVER spawns");

            joined.walkTo(sx + 55, sy, sz, true);
            int[] spawn = readSpawnOfEntity(boat[0].entityId(), 20_000);
            assertEquals(boat[0].entityId(), spawn[0],
                    "the walk's add arm spawned the newly in-box boat");
            server.ticker().submit(() -> server.vehicles().hurt(boat[0], 1000.0f));
        }
        awaitEmptyServer();
    }

    /**
     * SEAT 4 — THE TNT'S MOVE-TIME ARM ON BOTH CROSSINGS (the primed
     * TNT's own 160 row, clamped to 48): a body crossing OUT of the
     * observer's box on its own movement is destroyed here, and a body
     * crossing back IN re-spawns through the full chain — the SAME id
     * both sides of the crossing (the pushTnt teleport drives the
     * onTntMoved fan deterministically).
     */
    @Test
    void theTntsMoveArmDestroysAndRespawnsOnBothCrossings() throws Exception {
        try (TestClient18 joined = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client = joined;
            joined.sendHandshake(47, 2);
            joined.sendLoginStart("FamilyRangeTnt");
            joined.readLoginSuccess();
            joined.readUntilPositionAndLook();
            var session = server.playerRegistry().byName("FamilyRangeTnt").orElseThrow();
            double sx = session.position().x();
            double sy = session.position().y();
            double sz = session.position().z();

            // Inside the box at first: the spawn flows.
            server.ticker().submit(() -> server.tnt().spawnPrimedAt(
                    server.world(), sx + 46, sy, sz));
            awaitCondition(() -> !server.tnt().allPrimed().isEmpty(), "the TNT primed");
            var body = server.tnt().allPrimed().get(0);
            readSpawnOfEntity(body.entityId(), 10_000);

            // The body crosses OUT on its own movement: the move arm
            // destroys it on this client (the fuse keeps burning
            // server-side).
            server.ticker().submit(() -> server.tnt().pushTnt(body, 10.0, 0.0, 0.0));
            readDestroyOfEntity(body.entityId(), 10_000);
            assertTrue(server.tnt().allPrimed().stream()
                            .anyMatch(live -> live.entityId() == body.entityId()),
                    "the TNT keeps living server-side (the destroy is the observer's alone)");

            // The body crosses back IN: the move arm re-spawns it through
            // the full chain — the SAME id.
            server.ticker().submit(() -> server.tnt().pushTnt(body, -20.0, 0.0, 0.0));
            int[] respawn = readSpawnOfEntity(body.entityId(), 10_000);
            assertEquals(body.entityId(), respawn[0],
                    "the SAME id re-spawned through the full chain");
        }
        awaitEmptyServer();
    }

    /**
     * SEAT 5 — THE JOIN SNAPSHOT INHERITS THE GATES (the reference's
     * EntityMap.updatePlayers at the join: the newly-added player is
     * tracked against every body's box, never the retired
     * every-same-dimension fan): a second client joining beside the
     * watcher sees the NEAR item but never the FAR one — both funnel
     * the same spawn-gate choke point.
     */
    @Test
    void theJoinSnapshotConsultsTheBoxes() throws Exception {
        try (TestClient18 first = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 second = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client = first;
            first.sendHandshake(47, 2);
            first.sendLoginStart("FamRangeJoinA");
            first.readLoginSuccess();
            first.readUntilPositionAndLook();
            var session = server.playerRegistry().byName("FamRangeJoinA").orElseThrow();
            double sx = session.position().x();
            double sy = session.position().y();
            double sz = session.position().z();

            final net.zaminmc.torch.server.entity.ItemEntity[] near = new net.zaminmc.torch.server.entity.ItemEntity[1];
            final net.zaminmc.torch.server.entity.ItemEntity[] far = new net.zaminmc.torch.server.entity.ItemEntity[1];
            server.ticker().submit(() -> {
                near[0] = server.itemEntities().spawnDropAtBlock(
                        new net.zaminmc.torch.util.Position(sx + 10, sy, sz),
                        net.zaminmc.torch.item.ItemStack.of(
                                net.zaminmc.torch.server.item.BuiltinItems.DIAMOND, 1),
                        40, 0);
                far[0] = server.itemEntities().spawnDropAtBlock(
                        new net.zaminmc.torch.util.Position(sx + 61, sy, sz),
                        net.zaminmc.torch.item.ItemStack.of(
                                net.zaminmc.torch.server.item.BuiltinItems.DIAMOND, 1),
                        40, 0);
            });
            awaitCondition(() -> near[0] != null && far[0] != null, "both items spawned");

            // The second client joins beside the first: the join snapshot
            // spawns the near item, never the far one.
            client = second;
            second.sendHandshake(47, 2);
            second.sendLoginStart("FamRangeJoinB");
            second.readLoginSuccess();
            second.readUntilPositionAndLook();
            readSpawnOfEntity(near[0].entityId(), 10_000);
            assertFalse(spawnArrivesWithin(far[0].entityId(), 2_500),
                    "the join snapshot never spawns the out-of-box body");
            server.ticker().submit(() -> {
                server.itemEntities().remove(near[0], "test");
                server.itemEntities().remove(far[0], "test");
            });
        }
        awaitEmptyServer();
    }
}
