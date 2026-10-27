package net.zaminmc.torch.protocol.v1_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spectator camera's wire law (ML-018), proven over real sockets against
 * the real adapter: the C2S Spectate packet (the two-long uuid the reference's
 * PlayerSpectateC2SPacket carries) attaches the camera — the spectator's
 * client receives the Camera 0x43 naming the SAME wire id its own world holds
 * for the target, plus the target's position stream — while the spectating
 * body vanishes from every other view (the reference's broadcastTo law) and
 * the sneak exit restores the camera to self (0x43 own id + the authoritative
 * re-anchor, the re-spawn for the other observer).
 *
 * <p>The world under the adapter is alive (the natural-spawn mobs wander, so
 * the wire carries a steady stream of movement packets to every observer) —
 * every read below therefore targets its packet BY ID and skips the noise,
 * never assuming wire silence.</p>
 */
class SpectatorCameraWireAcceptanceTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        // The cadence rides well UNDER the listener's own 30s read-idle reap
        // (the reference's own proportion: its 15s cadence against the same
        // bound): the auto-answered exchange keeps a reading client alive no
        // matter how the scene's noise stream paces, and every scan below
        // skips the keep-alive frames by id — the packet-bounded windows are
        // never disturbed by the exchange.
        return 5_000;
    }

    @Test
    void spectatingAPlayerSendsTheCameraTheStreamAndTheInvisibilityLaw() throws Exception {
        PlayerSession aliceSession;
        PlayerSession bobSession;
        try (TestClient18 alice = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 bob = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 carol = new TestClient18("127.0.0.1", adapter.boundPort())) {
            join(alice, "Alice");
            aliceSession = session("Alice");
            JoinView atBob = join(bob, "Bob");
            bobSession = session("Bob");
            JoinView atCarol = join(carol, "Carol");
            // The join view's mutual tracking: Bob's world holds Alice, and
            // Carol's holds both (the wire ids the 0x43 and the Destroy must
            // name).
            captureSpawn(bob, atBob, aliceSession.uuid());
            captureSpawn(carol, atCarol, aliceSession.uuid(), bobSession.uuid());
            int aliceWireIdAtBob = atBob.spawns().get(aliceSession.uuid());
            int bobWireIdAtCarol = atCarol.spawns().get(bobSession.uuid());

            // THE ATTACH: the spectator mode then the hotbar's pick.
            server.setGamemode(bobSession, GameMode.SPECTATOR);
            awaitCondition(() -> bobSession.gamemode() == GameMode.SPECTATOR,
                    "Bob reached spectator");
            sendSpectate(bob, aliceSession.uuid());
            awaitCondition(() -> aliceSession.uuid().equals(bobSession.spectatorCamera()),
                    "the engine attached the camera");

            // The Camera packet names the target by the wire id the
            // client's own world holds (CameraS2CPacket's single varint).
            byte[] camera = bob.readPacketOfType(Protocol18.S2C_CAMERA, 10_000);
            ByteBuf cameraBody = Unpooled.wrappedBuffer(camera);
            ByteBufOps.readVarInt(cameraBody); // packet id
            assertEquals(aliceWireIdAtBob, ByteBufOps.readVarInt(cameraBody),
                    "the 0x43 names Alice by Bob's own wire id for her");

            // THE POSITION STREAM: Alice walks, her honest steps stream to
            // every in-range observer — the spectating Bob included (the
            // reference's own tracking: the target's entity stream IS the
            // camera's motion). The stream is netty-immediate but the body
            // follow is tick-aligned, so the engine's own state is awaited
            // before the exit drives the re-anchor.
            alice.walkTo(8.5, 5.0, 3.5, true);
            readUntilMovementOf(bob, aliceWireIdAtBob,
                    "the target's movement streams to the spectator");
            awaitCondition(() -> bobSession.position().x() == 8.5
                            && bobSession.position().z() == 3.5,
                    "the follow anchored the spectator's body at the walked position");

            // THE INVISIBILITY LAW: Carol's view of Bob dies the moment the
            // camera attaches (the reference's broadcastTo + the tracker's
            // removePlayer arm).
            readUntilDestroyOf(carol, bobWireIdAtCarol,
                    "the spectator vanished from the other observer's view");

            // THE LEAVE-SPECTATE WALK: the sneak action (the only exit signal
            // an attached client can send) restores the camera to self —
            // the 0x43 carrying the client's OWN join-game id, then the
            // authoritative Position-and-Look re-anchor where the follow
            // left the body.
            sendEntityAction(bob, 0);
            awaitCondition(() -> bobSession.spectatorCamera() == null,
                    "the engine restored the camera");
            byte[] restore = bob.readPacketOfType(Protocol18.S2C_CAMERA, 10_000);
            ByteBuf restoreBody = Unpooled.wrappedBuffer(restore);
            ByteBufOps.readVarInt(restoreBody); // packet id
            assertEquals(atBob.ownId(), ByteBufOps.readVarInt(restoreBody),
                    "the restore's 0x43 names Bob's own join id");
            byte[] anchor = bob.readPacketOfType(
                    Protocol18.S2C_PLAYER_POSITION_AND_LOOK, 10_000);
            ByteBuf anchorBody = Unpooled.wrappedBuffer(anchor);
            ByteBufOps.readVarInt(anchorBody); // packet id
            assertEquals(8.5, anchorBody.readDouble(), 0.0001,
                    "the re-anchor lands where the follow left the body");
            // The restored body re-enters the other observer's view (the
            // detach fan-out's re-track — the tracker's updatePlayer arm).
            readUntilNamedSpawnOf(carol, bobSession.uuid(),
                    "Bob re-spawns for Carol after the exit");
        }
        awaitEmptyServer();
    }

    @Test
    void aNonSpectatorsSpectatePacketNeverReachesTheWire() throws Exception {
        PlayerSession aliceSession;
        PlayerSession bobSession;
        try (TestClient18 alice = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 bob = new TestClient18("127.0.0.1", adapter.boundPort())) {
            join(alice, "Alice");
            aliceSession = session("Alice");
            join(bob, "Bob");
            bobSession = session("Bob");

            // The reference's isSpectator refusal (handlePlayerSpectate line
            // 518): a survival player's pick is discarded — no 0x43 ever
            // leaves the server for it. The refusal window is tick-scaled
            // (ten engine ticks past the walk's submit), the wire scan
            // packet-bounded.
            long startTick = server.world().totalTicks();
            sendSpectate(alice, bobSession.uuid());
            awaitCondition(() -> server.world().totalTicks() >= startTick + 10,
                    "the refusal window's ticks elapsed");
            awaitCondition(() -> aliceSession.gamemode() == GameMode.SURVIVAL,
                    "the survival mode stands");
            assertFalse(sawCameraPacket(alice), "no 0x43 answered the survival pick");
        }
        awaitEmptyServer();
    }

    @Test
    void aPlayerJoiningWhileSomeoneSpectatesNeverSeesTheSpectator() throws Exception {
        PlayerSession aliceSession;
        PlayerSession bobSession;
        try (TestClient18 alice = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 bob = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 dave = new TestClient18("127.0.0.1", adapter.boundPort())) {
            join(alice, "Alice");
            aliceSession = session("Alice");
            JoinView atBob = join(bob, "Bob");
            bobSession = session("Bob");
            captureSpawn(bob, atBob, aliceSession.uuid());

            server.setGamemode(bobSession, GameMode.SPECTATOR);
            awaitCondition(() -> bobSession.gamemode() == GameMode.SPECTATOR,
                    "Bob reached spectator");
            sendSpectate(bob, aliceSession.uuid());
            awaitCondition(() -> aliceSession.uuid().equals(bobSession.spectatorCamera()),
                    "the engine attached the camera");
            // Consume the 0x43; the scene's next observation is Dave's.
            bob.readPacketOfType(Protocol18.S2C_CAMERA, 10_000);

            JoinView atDave = join(dave, "Dave");
            captureSpawn(dave, atDave, aliceSession.uuid());
            assertTrue(atDave.spawns().containsKey(aliceSession.uuid()),
                    "Dave sees the plain player Alice");
            // The join view's gate: the spectating Bob never spawns for the
            // newcomer (trackPlayer's own refusal).
            assertFalse(atDave.spawns().containsKey(bobSession.uuid()),
                    "the spectating Bob never spawns for the newcomer");
            assertNoSpawnOf(dave, bobSession.uuid(),
                    "no Bob spawn reaches Dave even past the join's own window");
        }
        awaitEmptyServer();
    }

    // ------------------------------------------------------------------ helpers

    private PlayerSession session(String name) throws InterruptedException {
        awaitPlayers(name);
        return server.playerRegistry().byName(name).orElseThrow();
    }

    /** One scripted join: handshake, login, the burst captured until the anchor. */
    private JoinView join(TestClient18 client, String name) throws Exception {
        client.sendHandshake(47, 2);
        client.sendLoginStart(name);
        client.readLoginSuccess();
        int ownId = -1;
        Map<UUID, Integer> spawns = new HashMap<>();
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            byte[] payload = client.readPacket();
            ByteBuf buffer = Unpooled.wrappedBuffer(payload);
            int id = ByteBufOps.readVarInt(buffer);
            switch (id) {
                case Protocol18.S2C_JOIN_GAME -> ownId = buffer.readInt();
                case Protocol18.S2C_NAMED_SPAWN -> {
                    int wireId = ByteBufOps.readVarInt(buffer);
                    // ML-021's merged fix: the 16-byte writeUuid form
                    UUID uuid = new UUID(buffer.readLong(), buffer.readLong());
                    spawns.put(uuid, wireId);
                }
                case Protocol18.S2C_PLAYER_POSITION_AND_LOOK -> {
                    double x = buffer.readDouble();
                    double y = buffer.readDouble();
                    double z = buffer.readDouble();
                    // The anchor echo (what every real client sends first): it
                    // seeds the scripted walk tracker so later walkTo steps are
                    // honest relative moves. The bounded read timeout keeps a
                    // missing packet a fast failure, never a silent hang.
                    client.setReadTimeout(10_000);
                    client.sendPosition(x, y, z, true);
                    return new JoinView(ownId, spawns);
                }
                default -> { // the rest of the burst: consumed, frame-delimited
                }
            }
        }
        throw new IOException("join burst never reached the position anchor");
    }

    /**
     * Reads until every expected player has spawned for this client (the
     * mutual tracking's one-time Named Spawns), skipping the world's own
     * movement noise. The mobs wander forever under this adapter — silence
     * never comes, so the stop condition is the capture, never quiet.
     */
    private void captureSpawn(TestClient18 client, JoinView capture,
                              UUID... expected) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            byte[] payload = client.readPacket();
            ByteBuf buffer = Unpooled.wrappedBuffer(payload);
            int id = ByteBufOps.readVarInt(buffer);
            if (id == Protocol18.S2C_NAMED_SPAWN) {
                int wireId = ByteBufOps.readVarInt(buffer);
                // ML-021's merged fix: the 16-byte writeUuid form
                capture.spawns().put(new UUID(buffer.readLong(), buffer.readLong()), wireId);
            }
            boolean all = true;
            for (UUID wanted : expected) {
                all &= capture.spawns().containsKey(wanted);
            }
            if (all) {
                return;
            }
        }
        throw new AssertionError("expected spawns never arrived; captured: "
                + capture.spawns().keySet());
    }

    /** Reads until a movement-family packet for the entity arrives (skipping noise). */
    private void readUntilMovementOf(TestClient18 client, int wireId,
                                     String description) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            byte[] payload = client.readPacket();
            ByteBuf buffer = Unpooled.wrappedBuffer(payload);
            int id = ByteBufOps.readVarInt(buffer);
            if (id == Protocol18.S2C_REL_ENTITY_MOVE
                    || id == Protocol18.S2C_REL_ENTITY_MOVE_LOOK
                    || id == Protocol18.S2C_ENTITY_TELEPORT
                    || id == Protocol18.S2C_ENTITY_LOOK) {
                if (ByteBufOps.readVarInt(buffer) == wireId) {
                    return;
                }
            }
        }
        throw new AssertionError("Timed out waiting for the movement of entity "
                + wireId + ": " + description);
    }

    /** Reads until the entity's Destroy arrives (skipping the noise). */
    private void readUntilDestroyOf(TestClient18 client, int wireId,
                                    String description) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            byte[] payload = client.readPacket();
            ByteBuf buffer = Unpooled.wrappedBuffer(payload);
            int id = ByteBufOps.readVarInt(buffer);
            if (id == Protocol18.S2C_DESTROY_ENTITIES) {
                int count = ByteBufOps.readVarInt(buffer);
                for (int i = 0; i < count; i++) {
                    if (ByteBufOps.readVarInt(buffer) == wireId) {
                        return;
                    }
                }
            }
        }
        throw new AssertionError("Timed out waiting for the destroy of entity "
                + wireId + ": " + description);
    }

    /** Reads until the player's Named Spawn re-arrives (skipping the noise). */
    private void readUntilNamedSpawnOf(TestClient18 client, UUID wanted,
                                       String description) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            byte[] payload = client.readPacket();
            ByteBuf buffer = Unpooled.wrappedBuffer(payload);
            int id = ByteBufOps.readVarInt(buffer);
            if (id == Protocol18.S2C_NAMED_SPAWN) {
                ByteBufOps.readVarInt(buffer); // the fresh wire id
                // ML-021's merged fix: the 0x0C uuid rides the reference's
                // writeUuid (16 raw bytes) — this reader was written against
                // the string form the fix removed.
                UUID spawned = new UUID(buffer.readLong(), buffer.readLong());
                if (wanted.equals(spawned)) {
                    return;
                }
            }
        }
        throw new AssertionError("Timed out waiting for the spawn of " + wanted
                + ": " + description);
    }

    /**
     * Reads a bounded window asserting the player NEVER spawns in it (the
     * join view's gate): 300 packets of noise past the join, then quiet —
     * the mobs' stream counts the window, not the wall clock.
     */
    private void assertNoSpawnOf(TestClient18 client, UUID forbidden,
                                 String description) throws Exception {
        for (int i = 0; i < 300; i++) {
            byte[] payload = client.readPacket();
            ByteBuf buffer = Unpooled.wrappedBuffer(payload);
            int id = ByteBufOps.readVarInt(buffer);
            if (id == Protocol18.S2C_NAMED_SPAWN) {
                ByteBufOps.readVarInt(buffer); // wire id
                UUID uuid = new UUID(buffer.readLong(), buffer.readLong()); // ML-021's 16-byte form
                assertFalse(forbidden.toString().equals(uuid), description);
            }
        }
    }

    /**
     * @return whether a Camera 0x43 sits in the next stretch of the wire —
     * packet-bounded (the mobs' wandering stream never goes quiet, so the
     * count is the window: 400 frames of noise is well past the refusal's
     * own tick window).
     */
    private boolean sawCameraPacket(TestClient18 client) throws Exception {
        for (int i = 0; i < 400; i++) {
            byte[] payload = client.readPacket();
            if (client.readPacketId(payload) == Protocol18.S2C_CAMERA) {
                return true;
            }
        }
        return false;
    }

    /**
     * The spectate hotbar's pick (C2S 0x18 — the reference's
     * PlayerSpectateC2SPacket: two longs, msb then lsb).
     */
    private static void sendSpectate(TestClient18 client, UUID target) throws IOException {
        ByteBuf body = Unpooled.buffer(24);
        ByteBufOps.writeVarInt(body, Protocol18.C2S_SPECTATE);
        body.writeLong(target.getMostSignificantBits());
        body.writeLong(target.getLeastSignificantBits());
        send(client, body);
    }

    /**
     * The Entity Action stream (C2S 0x0B): own entity id varint, action
     * varint, jump-boost varint — action 0 is START_SNEAKING, the exit
     * signal an attached client can send (the reference's isSneaking
     * consult in the per-tick camera walk).
     */
    private static void sendEntityAction(TestClient18 client, int action) throws IOException {
        ByteBuf body = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(body, Protocol18.C2S_ENTITY_ACTION);
        ByteBufOps.writeVarInt(body, 0); // the player's own entity id (discarded)
        ByteBufOps.writeVarInt(body, action);
        ByteBufOps.writeVarInt(body, 0); // the jump-boost payload (unused here)
        send(client, body);
    }

    private static void send(TestClient18 client, ByteBuf body) throws IOException {
        byte[] bytes = new byte[body.readableBytes()];
        body.readBytes(bytes);
        body.release();
        client.sendPacket(bytes);
    }

    /** The captured join burst: the own join-game id and the spawned remotes. */
    private record JoinView(int ownId, Map<UUID, Integer> spawns) {
    }
}
