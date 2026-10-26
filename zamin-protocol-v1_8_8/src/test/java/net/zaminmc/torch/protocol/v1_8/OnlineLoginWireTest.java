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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Waves ON-1b/ON-1d's wire pins — the whole online login arm end to end
 * (ON-1b) plus the profile's properties riding the join burst's tab-list
 * ADD (ON-1d), against
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
            return new GameProfile(onlineIdOf(username), username, propertiesFor(username));
        }

        /**
         * The stub's per-name property arms (Wave ON-1d's test surface):
         * the signed textures default, the UNSIGNED variant (the wire's
         * no-signature arm — a null signature never rides a string), and
         * the multi-property row (the count varint's >1 form).
         */
        private static List<GameProfile.Property> propertiesFor(String username) {
            if (username.startsWith("Unsigned")) {
                return List.of(new GameProfile.Property("textures", "dW5zaWduZWQ=", null));
            }
            if (username.startsWith("Multi")) {
                return List.of(
                        new GameProfile.Property("textures", "bXVsdGk=", "c2lnbmF0dXJl"),
                        new GameProfile.Property("custom", "b3RoZXI=", null));
            }
            return List.of(new GameProfile.Property("textures", "aGVsbG8=", "c2lnbmF0dXJl"));
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
     * Wave ON-1d's pin: the profile's signed properties ride the join
     * burst's tab-list ADD whole (the reference's PlayerInfoS2CPacket
     * lines 118-135 — the textures row is the online branch's own skin
     * carrier). Three arms: the SIGNED default, the UNSIGNED variant
     * (the no-signature boolean with no string behind it), and the
     * MULTI-property row (the count varint above one). Every ADD the
     * joiner's own wire carries — the broadcast self-copy AND the
     * per-player loop's — must carry the identical row.
     */
    @Test
    void theTabListAddCarriesTheSessionServicePropertiesWhole() throws Exception {
        // The signed default arm.
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            List<TestClient18.PropWire> props = onlineLoginCollectingOwnAdds(client, "SkinRider");
            assertEquals(1, props.size(), "the stub's single signed textures row");
            assertEquals("textures", props.get(0).name());
            assertEquals("aGVsbG8=", props.get(0).value());
            assertEquals("c2lnbmF0dXJl", props.get(0).signature(),
                    "the signed arm: the boolean true rides the signature string");
        }
        // The unsigned arm: the boolean false, never a string.
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            List<TestClient18.PropWire> props = onlineLoginCollectingOwnAdds(client, "UnsignedSkin");
            assertEquals(1, props.size());
            assertEquals("textures", props.get(0).name());
            assertEquals("dW5zaWduZWQ=", props.get(0).value());
            assertNull(props.get(0).signature(),
                    "the unsigned arm: no signature string behind the false boolean");
        }
        // The multi-property arm: the count varint above one, the rows in order.
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            List<TestClient18.PropWire> props = onlineLoginCollectingOwnAdds(client, "MultiSkin");
            assertEquals(2, props.size(), "the count varint's >1 form");
            assertEquals("textures", props.get(0).name());
            assertEquals("c2lnbmF0dXJl", props.get(0).signature());
            assertEquals("custom", props.get(1).name());
            assertNull(props.get(1).signature());
        }
    }

    /**
     * The whole encrypted login (Request -> Response -> the exchange ->
     * Success) with the join burst's own ADDs collected — every self-add
     * must carry the SAME properties (the reference's double-add quirk
     * rides the same profile twice).
     */
    private List<TestClient18.PropWire> onlineLoginCollectingOwnAdds(
            TestClient18 client, String name) throws Exception {
        client.sendHandshake(47, 2);
        client.sendLoginStart(name);
        TestClient18.EncryptionRequest request = client.readEncryptionRequest();
        SecretKey secret = TestClient18.clientSecret();
        client.sendEncryptionResponse(secret, request.publicKey(), request.nonce());
        client.enableEncryption(secret);

        byte[] exchange = client.readPacket();
        ByteBuf exchangeBody = Unpooled.wrappedBuffer(exchange);
        assertEquals(Protocol18.S2C_LOGIN_SET_COMPRESSION, ByteBufOps.readVarInt(exchangeBody));
        ByteBufOps.readVarInt(exchangeBody); // the threshold
        client.armCompression();
        client.armInboundCompression();

        byte[] success = client.readPacket();
        ByteBuf successBody = Unpooled.wrappedBuffer(success);
        assertEquals(Protocol18.S2C_LOGIN_SUCCESS, ByteBufOps.readVarInt(successBody));
        ByteBufOps.readString(successBody, 64); // the uuid
        ByteBufOps.readString(successBody, 64); // the name

        // The join burst through the negotiated cipher + inflater: collect
        // the joiner's own ADDs (the self-copy + the loop's final entry).
        List<TestClient18.TabEntry> adds = client.readUntilPositionCollectingTabAdds();
        List<TestClient18.TabEntry> selfAdds = adds.stream()
                .filter(e -> name.equals(e.name())).toList();
        assertFalse(selfAdds.isEmpty(), "the joiner's own ADD reached their own wire");
        for (TestClient18.TabEntry self : selfAdds) {
            assertEquals(onlineIdOf(name), self.uuid(),
                    "the tab entry keys on the ONLINE identity");
        }
        List<List<TestClient18.PropWire>> rows = selfAdds.stream()
                .map(TestClient18.TabEntry::properties).distinct().toList();
        assertEquals(1, rows.size(),
                "every self-add carries the identical property row (got " + rows + ")");
        return rows.get(0);
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
