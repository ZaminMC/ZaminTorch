package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAVE TR — THE TRACKING-RANGE WIRE (the reference's EntityMap/TrackedEntity
 * law over the real adapter, and Paper 0216's per-entity override on top —
 * the KigPaper census's row): a body beyond its own ±range box NEVER
 * reaches the observer, a body that crosses OUT is destroyed on the
 * observer's client while it keeps living server-side, and a body that
 * crosses back IN re-spawns through the full chain. The retired wire fanned
 * every mob event to EVERY same-dimension observer with no range consult
 * at all — the seats pin the closed gap over the shared view-4 rig (the
 * registration's clamp: the mob row's 80 answers 48).
 */
class TrackingRangeWireParityTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 120_000;
    }

    /** The NoAI ghast — a body that never moves on its own, never burns
     * (the fire-immune family), never drowns: the range walk's own
     * observable, nothing else's. */
    private net.zaminmc.torch.server.entity.MobEntity spawnQuietGhast(
            double x, double y, double z) {
        var ghast = server.mobs().spawnAt(
                net.zaminmc.torch.server.entity.MobType.GHAST,
                new net.zaminmc.torch.util.Position(x, y, z));
        assertNotEquals(null, ghast, "the ghast spawned");
        ghast.setNoAi(true);
        return ghast;
    }

    /** Drains spawn-mob packets until the wanted entity id's own spawn
     * arrives (the join snapshot may deliver other bodies' spawns first —
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
            if (client().readPacketId(payload) != Protocol18.S2C_SPAWN_MOB) {
                continue;
            }
            var buffer = io.netty.buffer.Unpooled.wrappedBuffer(payload);
            net.zaminmc.torch.protocol.v1_8.ByteBufOps.readVarInt(buffer); // packet id
            if (net.zaminmc.torch.protocol.v1_8.ByteBufOps.readVarInt(buffer) == entityId) {
                return new int[] {entityId};
            }
        }
        throw new AssertionError("no spawn mob packet for entity " + entityId
                + " within " + timeoutMs + "ms");
    }

    /** True when a spawn-mob packet for the entity arrives inside the
     * window (drains everything; the caller asserts the negative). */
    private boolean spawnArrivesWithin(int entityId, long windowMs) throws Exception {
        long deadline = System.currentTimeMillis() + windowMs;
        client().setReadTimeout(200);
        while (System.currentTimeMillis() < deadline) {
            byte[] payload;
            try {
                payload = client().readPacketForDiagnostics();
            } catch (Exception quiet) {
                continue; // the read timeout's own drain cadence
            }
            if (client().readPacketId(payload) == Protocol18.S2C_SPAWN_MOB) {
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
     * SEAT 1 — THE SPAWN GATE + THE WALK'S ADD ARM: a ghast 61 blocks east
     * (inside the view-4 chunk window, OUTSIDE the clamped 48 box) NEVER
     * spawns — not at its own spawn, not at the observer's join. Teleported
     * inside the box, the player-side visibility walk (the player moving
     * more than 4 blocks) spawns it through the full chain. RED on the
     * pre-fix engine: the retired wire spawned it the moment it appeared.
     */
    @Test
    void theFarBodyNeverSpawnsUntilItCrossesIntoTheBox() throws Exception {
        try (TestClient18 joined = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client = joined;
            joined.sendHandshake(47, 2);
            joined.sendLoginStart("RangeGateWatcher");
            joined.readLoginSuccess();
            joined.readUntilPositionAndLook();
            var session = server.playerRegistry().byName("RangeGateWatcher").orElseThrow();
            double sx = session.position().x();
            double sy = session.position().y();
            double sz = session.position().z();

            // 61 blocks east: chunk 3-4 away (inside the loaded window),
            // 13 blocks past the 48 box.
            var far = spawnQuietGhast(sx + 61, sy, sz);
            assertFalse(spawnArrivesWithin(far.entityId(), 2_500),
                    "a body 13 blocks past its box NEVER spawns (the retired wire did)");

            // The same body teleported inside the box: still nothing until
            // the PLAYER moves more than 4 blocks (the walk's own cadence).
            far.teleport(new net.zaminmc.torch.util.Position(sx + 12, sy, sz));
            assertFalse(spawnArrivesWithin(far.entityId(), 1_500),
                    "a stationary player's walk never fires (the >4-block cadence)");
            joined.walkTo(sx - 6, sy, sz, true);
            int[] spawn = readSpawnOfEntity(far.entityId(), 10_000);
            assertEquals(far.entityId(), spawn[0],
                    "the walk's add arm spawned the newly in-box body through the full chain");
            server.ticker().submit(far::despawn);
        }
        awaitEmptyServer();
    }

    /**
     * SEAT 2 — THE OUT-OF-BOX DESTROY (the player-side arm): a spawned
     * body the player walks away from is destroyed on the client while it
     * keeps living server-side — TrackedEntity.updatePlayer's remove arm.
     * RED on the pre-fix engine: nothing ever destroyed a still-living
     * body for a moving-away observer.
     */
    @Test
    void theWalkedAwayBodyIsDestroyedButLivesServerSide() throws Exception {
        try (TestClient18 joined = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client = joined;
            joined.sendHandshake(47, 2);
            joined.sendLoginStart("RangeLeaver");
            joined.readLoginSuccess();
            joined.readUntilPositionAndLook();
            var session = server.playerRegistry().byName("RangeLeaver").orElseThrow();
            double sx = session.position().x();
            double sy = session.position().y();
            double sz = session.position().z();

            var near = spawnQuietGhast(sx + 10, sy, sz);
            readSpawnOfEntity(near.entityId(), 10_000);

            // The body crosses out of the box server-side; the PLAYER then
            // moves more than 4 blocks — the walk consults the box again
            // and destroys the body on this client.
            near.teleport(new net.zaminmc.torch.util.Position(sx + 70, sy, sz));
            joined.walkTo(sx - 6, sy, sz, true);
            readDestroyOfEntity(near.entityId(), 10_000);
            assertFalse(near.dead(), "the body keeps living server-side (the destroy is the observer's alone)");
            server.ticker().submit(near::despawn);
        }
        awaitEmptyServer();
    }

    /**
     * SEAT 3 — THE BACK-IN-BOX RE-SPAWN: the destroyed body crossing back
     * inside the box re-spawns through the full chain — the SAME entity
     * id, the reference's updatePlayer add arm.
     */
    @Test
    void theBackInBoxBodyRespawnsThroughTheFullChain() throws Exception {
        try (TestClient18 joined = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client = joined;
            joined.sendHandshake(47, 2);
            joined.sendLoginStart("RangeReturner");
            joined.readLoginSuccess();
            joined.readUntilPositionAndLook();
            var session = server.playerRegistry().byName("RangeReturner").orElseThrow();
            double sx = session.position().x();
            double sy = session.position().y();
            double sz = session.position().z();

            var wanderer = spawnQuietGhast(sx + 12, sy, sz);
            readSpawnOfEntity(wanderer.entityId(), 10_000);

            // Out, then back: the destroy lands, then the re-spawn — the
            // same id both sides of the crossing.
            wanderer.teleport(new net.zaminmc.torch.util.Position(sx + 70, sy, sz));
            joined.walkTo(sx - 6, sy, sz, true);
            readDestroyOfEntity(wanderer.entityId(), 10_000);
            wanderer.teleport(new net.zaminmc.torch.util.Position(sx + 14, sy, sz));
            joined.walkTo(sx, sy, sz, true);
            int[] respawn = readSpawnOfEntity(wanderer.entityId(), 10_000);
            assertEquals(wanderer.entityId(), respawn[0],
                    "the SAME id re-spawned through the full chain");
            server.ticker().submit(wanderer::despawn);
        }
        awaitEmptyServer();
    }

    /**
     * SEAT 4 — PAPER 0216'S PER-ENTITY OVERRIDE (the KigPaper census's
     * row): a custom tracking range set on the LIVE body is consulted
     * first at the very next visibility walk — a custom 16 destroys a
     * 30-block body the table's clamped 48 still held, and clearing the
     * override (0) re-spawns it. RED on the pre-fix engine: no override
     * consult existed.
     */
    @Test
    void theCustomOverrideShrinksTheBoxLive() throws Exception {
        try (TestClient18 joined = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client = joined;
            joined.sendHandshake(47, 2);
            joined.sendLoginStart("RangeOverrider");
            joined.readLoginSuccess();
            joined.readUntilPositionAndLook();
            var session = server.playerRegistry().byName("RangeOverrider").orElseThrow();
            double sx = session.position().x();
            double sy = session.position().y();
            double sz = session.position().z();

            var custom = spawnQuietGhast(sx + 30, sy, sz);
            readSpawnOfEntity(custom.entityId(), 10_000); // 30 < 48: inside the table's box

            // The override shrinks the box BELOW the body's distance: the
            // next walk destroys it.
            custom.setCustomTrackingRange(16);
            joined.walkTo(sx - 6, sy, sz, true);
            readDestroyOfEntity(custom.entityId(), 10_000);
            assertFalse(custom.dead(), "the body still lives server-side");

            // Clearing the override (0): the table's row answers again and
            // the same body re-spawns.
            custom.setCustomTrackingRange(0);
            joined.walkTo(sx, sy, sz, true);
            readSpawnOfEntity(custom.entityId(), 10_000);
            server.ticker().submit(custom::despawn);
        }
        awaitEmptyServer();
    }
}
