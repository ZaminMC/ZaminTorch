package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAVE TR-3 — THE PLAYER BAND'S OWN BOX (the reference's widest row,
 * EntityMap.onEntityAdded lines 61-62: {@code startTracking(entity, 512,
 * 2)} — the last tracked family the roll-out left untouched): the
 * remote-player relay consults the same clamped ±range square every
 * family rides (the 512 answers 48 on the view-4 rig) — at the join's
 * mutual exchange, at the mover's own broadcast, and at the observer's
 * walk. The retired consult was a CHUNK-distance circle at the mover's
 * broadcast alone, and the join exchange spawned every same-dimension
 * player however far.
 */
class TrackingRangePlayerWireParityTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 120_000;
    }

    /** Drains Named Spawn (0x0C) packets until the wanted uuid's own
     * spawn arrives; @return the observer-local wire id. */
    private int readNamedSpawnOf(UUID wanted, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        client().setReadTimeout(200);
        while (System.currentTimeMillis() < deadline) {
            byte[] payload;
            try {
                payload = client().readPacketForDiagnostics();
            } catch (Exception quiet) {
                continue;
            }
            if (client().readPacketId(payload) != Protocol18.S2C_NAMED_SPAWN) {
                continue;
            }
            var buffer = io.netty.buffer.Unpooled.wrappedBuffer(payload);
            net.zaminmc.torch.protocol.v1_8.ByteBufOps.readVarInt(buffer); // packet id
            int wireId = net.zaminmc.torch.protocol.v1_8.ByteBufOps.readVarInt(buffer);
            UUID carried = net.zaminmc.torch.protocol.v1_8.ByteBufOps.readUuid(buffer);
            if (wanted.equals(carried)) {
                return wireId;
            }
        }
        throw new AssertionError("no named spawn packet for " + wanted
                + " within " + timeoutMs + "ms");
    }

    /** True when a Named Spawn for the uuid arrives inside the window. */
    private boolean namedSpawnArrivesWithin(UUID wanted, long windowMs) throws Exception {
        long deadline = System.currentTimeMillis() + windowMs;
        client().setReadTimeout(200);
        while (System.currentTimeMillis() < deadline) {
            byte[] payload;
            try {
                payload = client().readPacketForDiagnostics();
            } catch (Exception quiet) {
                continue;
            }
            if (client().readPacketId(payload) == Protocol18.S2C_NAMED_SPAWN) {
                var buffer = io.netty.buffer.Unpooled.wrappedBuffer(payload);
                net.zaminmc.torch.protocol.v1_8.ByteBufOps.readVarInt(buffer); // packet id
                net.zaminmc.torch.protocol.v1_8.ByteBufOps.readVarInt(buffer); // wire id
                UUID carried = net.zaminmc.torch.protocol.v1_8.ByteBufOps.readUuid(buffer);
                if (wanted.equals(carried)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Drains destroy packets until the wanted wire id's destroy arrives. */
    private void readDestroyOfWireId(int wireId, long timeoutMs) throws Exception {
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
                if (net.zaminmc.torch.protocol.v1_8.ByteBufOps.readVarInt(buffer) == wireId) {
                    return;
                }
            }
        }
        throw new AssertionError("no destroy packet for wire id " + wireId
                + " within " + timeoutMs + "ms");
    }

    private TestClient18 client;

    private TestClient18 client() {
        return client;
    }

    private TestClient18 joinAt(String name) throws Exception {
        TestClient18 joined = new TestClient18("127.0.0.1", adapter.boundPort());
        joined.sendHandshake(47, 2);
        joined.sendLoginStart(name);
        joined.readLoginSuccess();
        joined.readUntilPositionAndLook();
        return joined;
    }

    /**
     * SEAT 1 — THE MOVER CROSSING (the mover's own broadcast over the
     * clamped 512-row box): two players see each other at the spawn; one
     * walks 60 blocks away — the OTHER observer's client destroys the
     * walker's body; the walker returns — the SAME uuid re-spawns through
     * the full chain. RED on the pre-fix engine: the chunk-distance
     * circle tracked a 60-block walker (4 chunks, view 4) — the box's 48
     * refuses it.
     */
    @Test
    void theMoverCrossingOutIsDestroyedAndBackInRespawns() throws Exception {
        try (TestClient18 walker = joinAt("PlayerBandWalk");
             TestClient18 watcher = joinAt("PlayerBandWatch")) {
            var walkerSession = server.playerRegistry().byName("PlayerBandWalk").orElseThrow();
            var watcherSession = server.playerRegistry().byName("PlayerBandWatch").orElseThrow();
            double sx = watcherSession.position().x();
            double sy = watcherSession.position().y();
            double sz = watcherSession.position().z();

            // The mutual spawn at the join: the watcher sees the walker.
            client = watcher;
            int walkerWireId = readNamedSpawnOf(walkerSession.uuid(), 10_000);
            assertTrue(walkerWireId >= 0);

            // The walker crosses OUT of the watcher's box: the mover-side
            // broadcast un-tracks (the destroy on the watcher's client).
            walker.walkTo(sx + 60, sy, sz, true);
            readDestroyOfWireId(walkerWireId, 20_000);
            assertTrue(server.playerRegistry().byName("PlayerBandWalk").isPresent(),
                    "the walker keeps living server-side");

            // The walker crosses back IN: the re-spawn — the SAME uuid.
            walker.walkTo(sx, sy, sz, true);
            int respawned = readNamedSpawnOf(walkerSession.uuid(), 20_000);
            assertTrue(respawned >= 0, "the SAME uuid re-spawned through the full chain");
        }
        awaitEmptyServer();
    }

    /**
     * SEAT 2 — THE OBSERVER'S WALK (EntityMap.tick's loop over the player
     * entries — the observer-side arm the retired relay never carried):
     * the walker stands 60 blocks away (un-tracked); the OBSERVER walks
     * toward them — the walk's player loop spawns the walker's body
     * through the full chain.
     */
    @Test
    void theObserversWalkSpawnsTheFarPlayer() throws Exception {
        try (TestClient18 walker = joinAt("PlayerBandFar");
             TestClient18 observer = joinAt("PlayerBandNear")) {
            var walkerSession = server.playerRegistry().byName("PlayerBandFar").orElseThrow();
            var observerSession = server.playerRegistry().byName("PlayerBandNear").orElseThrow();
            double sx = observerSession.position().x();
            double sy = observerSession.position().y();
            double sz = observerSession.position().z();

            // The join's mutual spawn consumed first (the drain's own law:
            // the buffered join spawn must never read as a crossing).
            client = observer;
            int walkerWireId = readNamedSpawnOf(walkerSession.uuid(), 10_000);

            // The walker goes far (out of the observer's box): the
            // mover-side broadcast destroys the body here.
            walker.walkTo(sx + 60, sy, sz, true);
            readDestroyOfWireId(walkerWireId, 20_000);
            assertFalse(namedSpawnArrivesWithin(walkerSession.uuid(), 1_500),
                    "the far walker never spawns for the near observer");

            // The OBSERVER walks toward the walker: the walk's player loop
            // spawns the now-in-box body.
            observer.walkTo(sx + 55, sy, sz, true);
            int spawned = readNamedSpawnOf(walkerSession.uuid(), 20_000);
            assertTrue(spawned >= 0,
                    "the observer's walk spawned the newly in-box player");
        }
        awaitEmptyServer();
    }

    /**
     * SEAT 3 — THE JOIN'S MUTUAL EXCHANGE CONSULTS THE BOXES (the
     * reference's EntityMap.updatePlayers at the join): a third client
     * joining beside the spawn NEVER reaches the player standing 60
     * blocks away — while the in-box player beside them spawns whole
     * (the exchange ran; the box alone refused the far body). RED on the
     * pre-fix engine: the join exchange spawned every same-dimension
     * player however far.
     */
    @Test
    void theJoinExchangeNeverSpawnsFarPlayers() throws Exception {
        try (TestClient18 far = joinAt("BandFarJoin");
             TestClient18 anchor = joinAt("BandAnchor");
             TestClient18 late = new TestClient18("127.0.0.1", adapter.boundPort())) {
            var farSession = server.playerRegistry().byName("BandFarJoin").orElseThrow();
            var anchorSession = server.playerRegistry().byName("BandAnchor").orElseThrow();
            double sx = anchorSession.position().x();
            double sy = anchorSession.position().y();
            double sz = anchorSession.position().z();

            // The far player walks away BEFORE the late joiner even
            // connects (the socket stays closed through the walk).
            far.walkTo(sx + 60, sy, sz, true);
            Thread.sleep(300);

            // The late joiner at the spawn: the far player (60 out) never
            // spawns for them, the anchor beside them does (the control).
            late.sendHandshake(47, 2);
            late.sendLoginStart("BandLate");
            late.readLoginSuccess();
            late.readUntilPositionAndLook();
            client = late;
            readNamedSpawnOf(anchorSession.uuid(), 10_000);
            assertFalse(namedSpawnArrivesWithin(farSession.uuid(), 2_500),
                    "the join exchange never spawns the out-of-box player");
        }
        awaitEmptyServer();
    }
}
