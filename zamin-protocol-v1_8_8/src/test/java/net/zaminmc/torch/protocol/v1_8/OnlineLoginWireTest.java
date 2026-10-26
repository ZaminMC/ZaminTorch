package net.zaminmc.torch.protocol.v1_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.zaminmc.torch.ServerState;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.auth.AuthenticationUnavailableException;
import net.zaminmc.torch.server.auth.GameProfile;
import net.zaminmc.torch.server.auth.SessionService;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave ON-1b's wire pins — the whole online login arm end to end, against
 * the canonical operator shape (online-mode=true, threshold=256) with the
 * session service stubbed at the seam the reference stubs it nowhere: the
 * real hasJoined walk is exercised by MojangSessionServiceTest; THIS test
 * proves the wire — the Encryption Request's byte-exact HelloS2CPacket
 * form, the KeyC2S response's RSA round-trip, the AES/CFB8 stream riding
 * BOTH directions (every later frame the client reads parses only through
 * its own negotiated cipher — a wrong key, or no encryption, is garbage
 * frames and a dead login), the compression exchange arriving INSIDE the
 * encrypted stream, Login Success carrying the SESSION SERVICE's identity
 * (the Mojang uuid, never the offline v3), and the two failure arms'
 * reference-exact strings.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OnlineLoginWireTest {

    @org.junit.jupiter.api.io.TempDir
    static Path dataDir;

    /**
     * The stub's per-name online id — unique per test identity (the
     * engine's already-connected gate keys on the UUID, and a lingering
     * pre-reap session would collide on a shared one), deterministic and
     * never the offline v3.
     */
    private static UUID onlineIdOf(String username) {
        return UUID.nameUUIDFromBytes(("OnlineStub:" + username).getBytes(StandardCharsets.UTF_8));
    }

    /** The in-memory session-service stub (the seam's own test surface). */
    private static final class StubSessionService implements SessionService {
        // Each test owns its own name (the engine's "already connected"
        // gate lingers a session past the client's close until the reap).
        volatile boolean refuse;        // the null arm
        volatile boolean unavailable;   // the down arm
        volatile String lastName;
        volatile String lastServerIdHash;

        @Override
        public GameProfile hasJoined(String username, String serverIdHash)
                throws AuthenticationUnavailableException {
            lastName = username;
            lastServerIdHash = serverIdHash;
            if (unavailable) {
                throw new AuthenticationUnavailableException("stubbed outage");
            }
            if (refuse) {
                return null;
            }
            return new GameProfile(onlineIdOf(username), username,
                    List.of(new GameProfile.Property("textures", "aGVsbG8=", "c2lnbmF0dXJl")));
        }
    }

    private static EngineServer server;
    private static V18ProtocolServer adapter;
    private static StubSessionService sessions;

    @BeforeAll
    void bootStack() throws Exception {
        EngineConfig config = new EngineConfig(
                "127.0.0.1", 0, "world", "ZaminTorch test", 20, 4, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE, false, 16,
                false, "", 0, false, 0, "", "", 60_000L,
                true, 256);
        server = new EngineServer(config);
        server.start();
        sessions = new StubSessionService();
        adapter = new V18ProtocolServer(server, 60_000, 30, sessions);
        adapter.start(server);
    }

    @AfterAll
    void stopStack() {
        server.shutdown(adapter::shutdown);
    }

    /**
     * The whole arm: Request (byte-exact body: the empty server-id, the
     * DER key, the 4-byte nonce) -> the client's AES secret + RSA reply ->
     * the compression exchange arriving ENCRYPTED -> Login Success with
     * the service's identity -> the join burst riding the encrypted and
     * compressed stream whole. The client's own cipher streams carrying
     * every frame is the encryption proof: the server encrypted with the
     * negotiated key or nothing parses.
     */
    @Test
    void theOnlineLoginWalksTheWholeEncryptionArm() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("ArmWalker");

            // 1. The Encryption Request — the FIRST login-state answer.
            TestClient18.EncryptionRequest request = client.readEncryptionRequest();
            assertEquals("", request.serverId(),
                    "the reference's own empty server id (1.8.8's \"\" key)");
            assertEquals("RSA", request.publicKey().getAlgorithm());
            assertEquals(4, request.nonce().length, "the reference's 4-byte nonce");

            // 2. The client answers: a fresh AES secret + the RSA-encrypted
            //    pair, then wraps its own streams in the negotiated cipher.
            SecretKey secret = TestClient18.clientSecret();
            client.sendEncryptionResponse(secret, request.publicKey(), request.nonce());
            client.enableEncryption(secret);

            // 3. The compression exchange arrives INSIDE the encrypted
            //    stream (the parse only works through the cipher).
            byte[] exchange = client.readPacket();
            ByteBuf exchangeBody = Unpooled.wrappedBuffer(exchange);
            assertEquals(Protocol18.S2C_LOGIN_SET_COMPRESSION,
                    ByteBufOps.readVarInt(exchangeBody));
            assertEquals(256, ByteBufOps.readVarInt(exchangeBody));
            assertEquals(0, exchangeBody.readableBytes());

            // A real client arms both its own arms on the exchange.
            client.armCompression();
            client.armInboundCompression();

            // 4. Login Success carries the SESSION SERVICE'S identity —
            //    the Mojang uuid, never the offline v3.
            byte[] success = client.readPacket();
            ByteBuf successBody = Unpooled.wrappedBuffer(success);
            assertEquals(Protocol18.S2C_LOGIN_SUCCESS, ByteBufOps.readVarInt(successBody));
            String uuid = ByteBufOps.readString(successBody, 64);
            String name = ByteBufOps.readString(successBody, 64);
            assertEquals("ArmWalker", name);
            assertEquals(onlineIdOf("ArmWalker").toString(), uuid,
                    "the ONLINE identity — the service's answer, not the offline v3");
            assertNotEquals(java.util.UUID.nameUUIDFromBytes(
                            ("OfflinePlayer:ArmWalker").getBytes(StandardCharsets.UTF_8)).toString(),
                    uuid, "the offline convention never rides the online branch");
            assertEquals(0, successBody.readableBytes());

            // 5. The consult itself: the name and the hash the service saw.
            assertEquals("ArmWalker", sessions.lastName);
            assertTrue(sessions.lastServerIdHash.matches("-?[0-9a-f]{1,40}"),
                    "the server-id hash is the BigInteger hex form: "
                            + sessions.lastServerIdHash);

            // 6. The join burst rides the encrypted+compressed stream
            //    whole (the fixture's inbound walk decrypts + inflates;
            //    the ids parse like any plaintext login).
            boolean joinGameSeen = false;
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline) {
                byte[] frame = client.readPacket();
                int id = client.readPacketId(frame);
                if (id == Protocol18.S2C_JOIN_GAME && !joinGameSeen) {
                    ByteBuf joinBody = Unpooled.wrappedBuffer(frame);
                    ByteBufOps.readVarInt(joinBody); // the id
                    joinBody.readInt();              // entity id
                    joinBody.readByte();             // gamemode
                    joinBody.readByte();             // dimension
                    joinBody.readByte();             // difficulty
                    joinBody.readByte();             // max players (legacy)
                    ByteBufOps.readString(joinBody, 32); // level type
                    joinBody.readBoolean();          // reduced debug info
                    assertEquals(0, joinBody.readableBytes(),
                            "Join Game parses whole through the negotiated cipher");
                    joinGameSeen = true;
                }
                if (joinGameSeen && id == Protocol18.S2C_PLAYER_POSITION_AND_LOOK) {
                    break;
                }
            }
            assertTrue(joinGameSeen, "the join burst rode the encrypted stream");
            assertEquals(ServerState.RUNNING, server.state());
        }
    }

    /**
     * The refusal arm: a null hasJoined answer closes the login with the
     * reference's own string (line 180) — through the encrypted pipe.
     */
    @Test
    void aRefusedSessionDisconnectsWithTheReferenceString() throws Exception {
        sessions.refuse = true;
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Refused");
            TestClient18.EncryptionRequest request = client.readEncryptionRequest();
            SecretKey secret = TestClient18.clientSecret();
            client.sendEncryptionResponse(secret, request.publicKey(), request.nonce());
            client.enableEncryption(secret);

            byte[] disconnect = client.readPacket();
            ByteBuf body = Unpooled.wrappedBuffer(disconnect);
            assertEquals(Protocol18.S2C_LOGIN_DISCONNECT, ByteBufOps.readVarInt(body));
            String reason = ByteBufOps.readString(body, 256);
            assertTrue(reason.contains("Failed to verify username!"),
                    "the reference's own refusal string: " + reason);
        } finally {
            sessions.refuse = false;
        }
    }

    /**
     * The outage arm: an unresponsive session service closes the login
     * with the reference's own string (line 190).
     */
    @Test
    void anUnavailableServiceDisconnectsWithTheReferenceString() throws Exception {
        sessions.unavailable = true;
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Refused");
            TestClient18.EncryptionRequest request = client.readEncryptionRequest();
            SecretKey secret = TestClient18.clientSecret();
            client.sendEncryptionResponse(secret, request.publicKey(), request.nonce());
            client.enableEncryption(secret);

            byte[] disconnect = client.readPacket();
            ByteBuf body = Unpooled.wrappedBuffer(disconnect);
            assertEquals(Protocol18.S2C_LOGIN_DISCONNECT, ByteBufOps.readVarInt(body));
            String reason = ByteBufOps.readString(body, 256);
            assertTrue(reason.contains("Authentication servers are down. Please try again later, sorry!"),
                    "the reference's own outage string: " + reason);
        } finally {
            sessions.unavailable = false;
        }
    }

    /**
     * The nonce arm: a corrupted verify token fails the echo check and
     * the connection dies without a login answer (the reference's
     * IllegalStateException reaches the connection's exception walk — a
     * close, never a LoginFail).
     */
    @Test
    void aCorruptedNonceKillsTheConnection() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("BadNonce");
            TestClient18.EncryptionRequest request = client.readEncryptionRequest();
            SecretKey secret = TestClient18.clientSecret();
            client.sendEncryptionResponse(secret, request.publicKey(),
                    new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF});
            client.enableEncryption(secret);
            client.setReadTimeout(10_000);
            // The connection closes: the read surfaces EOF (or the socket
            // dies trying) — never a Login Success, never a LoginFail.
            boolean closed = false;
            try {
                while (true) {
                    byte[] frame = client.readPacket();
                    int id = client.readPacketId(frame);
                    assertNotEquals(Protocol18.S2C_LOGIN_SUCCESS, id,
                            "a corrupted nonce never reaches a login");
                }
            } catch (java.io.IOException | RuntimeException e) {
                closed = true; // the stream died: the close law
            }
            assertTrue(closed, "the connection died on the corrupted nonce");
        }
    }

    /**
     * The stage gate: an Encryption Response without a Request (the
     * offline-stage violation) closes the connection — the reference's
     * Validate.validState throw, the same close law.
     */
    @Test
    void anUnsolicitedEncryptionResponseIsClosed() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("StageGate");
            TestClient18.EncryptionRequest request = client.readEncryptionRequest();
            // The reference's second-response violation: answer, then
            // answer AGAIN — the stage is AUTHENTICATING, not KEY.
            SecretKey secret = TestClient18.clientSecret();
            client.sendEncryptionResponse(secret, request.publicKey(), request.nonce());
            client.enableEncryption(secret);
            client.sendEncryptionResponse(secret, request.publicKey(), request.nonce());
            client.setReadTimeout(10_000);
            boolean closed = false;
            try {
                while (true) {
                    client.readPacket();
                }
            } catch (java.io.IOException | RuntimeException e) {
                closed = true;
            }
            assertTrue(closed, "the stage violation closed the connection");
        }
    }
}
