package net.zaminmc.torch.protocol.v1_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.zaminmc.torch.ServerState;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave ON-1a: the login compression-threshold exchange, checked at the
 * wire — the record the pre-wave pins guarded, rewritten under the
 * owner's DEC-001 decision (2026-10-26) that landed the exchange whole.
 *
 * <p><b>The reference law (reference/1.8.8)</b>: {@code ServerLoginNetworkHandler
 * .acceptLogin} lines 92-101 sends the login-state Set Compression packet
 * (0x03, one varint — the threshold, CompressionThresholdS2CPacket lines
 * 20-26) BEFORE Login Success whenever the configured threshold is ≥ 0
 * ({@code MinecraftServer.getNetworkCompressionThreshold} defaults to 256;
 * a dedicated server reads network-compression-threshold from its
 * properties, and any negative value disables the exchange entirely),
 * then installs the zlib CompressionDecoder/CompressionEncoder pair
 * (Connection.setCompressionThreshold lines 325-346) — from then every
 * frame is length-varint + data-length-varint + zlib payload. The client
 * only ever enables decompression when it RECEIVES 0x03
 * (ClientLoginNetworkHandler lines 101-105), and arms its own outbound
 * form with it: an exchange-less login is the protocol's own legal
 * uncompressed mode, byte-identical framing to the no-compression shape
 * (SizePrepender lines 8-20 / SplitterHandler: one length varint, max 3
 * bytes, then the payload).</p>
 *
 * <p><b>The canonical operator shape</b> (the properties path's default,
 * threshold=256): the exchange rides FIRST, Login Success second in the
 * two-varint form, and the whole join burst — the over-threshold chunk
 * frames included — rides compressed, inflating byte-exact through an
 * independent client-side walk. <b>The disabled branch</b> (a negative
 * threshold — the pre-wave permanent mode, the compat constructors' own
 * shape): no exchange, one-varint framing, the historical pins kept as
 * the branch's own record. Both branches are pinned here, each against
 * its own boot.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoginCompressionWireTest {

    @org.junit.jupiter.api.io.TempDir
    static Path disabledDataDir;

    @org.junit.jupiter.api.io.TempDir
    static Path canonicalDataDir;

    /** The disabled-branch stack (threshold -1: the compat shape, no exchange). */
    private EngineServer disabledServer;
    private V18ProtocolServer disabledAdapter;

    /** The canonical-operators stack (threshold 256, offline login). */
    private EngineServer canonicalServer;
    private V18ProtocolServer canonicalAdapter;

    @BeforeAll
    void bootStacks() throws Exception {
        EngineConfig disabled = new EngineConfig(
                "127.0.0.1", 0, "world", "ZaminTorch test", 20, 4, 20, disabledDataDir.toString());
        disabledServer = new EngineServer(disabled);
        disabledServer.start();
        disabledAdapter = new V18ProtocolServer(disabledServer, 60_000);
        disabledAdapter.start(disabledServer);

        EngineConfig canonical = new EngineConfig(
                "127.0.0.1", 0, "world", "ZaminTorch test", 20, 4, 20, canonicalDataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE, false, 16,
                false, "", 0, false, 0, "", "", 60_000L,
                false, 256);
        canonicalServer = new EngineServer(canonical);
        canonicalServer.start();
        canonicalAdapter = new V18ProtocolServer(canonicalServer, 60_000);
        canonicalAdapter.start(canonicalServer);
    }

    @AfterAll
    void stopStacks() {
        disabledServer.shutdown(disabledAdapter::shutdown);
        canonicalServer.shutdown(canonicalAdapter::shutdown);
    }

    /**
     * FrameCodec's own frame form, driven directly: exactly the reference's
     * SizePrepender shape — one varint length prefix, then the payload. The
     * compression stage (when armed) adds its SECOND varint (the
     * data-length, 0 for below-threshold frames) INSIDE this framing; the
     * outer form never changes, either branch.
     */
    @Test
    void frameCodecEmitsTheReferenceUncompressedFrameForm() {
        ByteBuf payload = Unpooled.wrappedBuffer(new byte[]{0x01, 0x02, 0x03, 0x04});
        EmbeddedChannel channel = new EmbeddedChannel(new FrameCodec.Encoder());
        assertTrue(channel.writeOutbound(payload.copy()));
        ByteBuf framed = channel.readOutbound();
        assertEquals(5, framed.readableBytes(), "one length varint + the four payload bytes");
        assertEquals(4, ByteBufOps.readVarInt(framed), "the length prefix is the payload size");
        assertEquals(0x01, framed.readByte());
        assertEquals(0x02, framed.readByte());
        assertEquals(0x03, framed.readByte());
        assertEquals(0x04, framed.readByte());
        assertEquals(0, framed.readableBytes(),
                "no second (data-length) varint at the FRAMING layer — the compressed "
                        + "form's own marker lives inside, added by the compression stage");
        framed.release();
        channel.finishAndReleaseAll();
    }

    // ------------------------------------------------------------------ the disabled branch (-1)

    /**
     * The exchange-less login (a negative threshold): the FIRST frame after
     * Login Start is exactly Login Success (0x02) — never the reference's
     * Set Compression (0x03) — and its body is byte-for-byte the
     * reference's LoginSuccessS2CPacket form (varint-length uuid string,
     * then varint-length username; the frame fully consumed).
     */
    @Test
    void theExchangelessLoginSkipsTheCompressionPacketStraightToLoginSuccess() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", disabledAdapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Compressless");

            byte[] frame = client.readPacket();
            ByteBuf body = Unpooled.wrappedBuffer(frame);
            int packetId = ByteBufOps.readVarInt(body);
            assertNotEquals(0x03, packetId,
                    "the disabled branch never rides the Set Compression exchange");
            assertEquals(Protocol18.S2C_LOGIN_SUCCESS, packetId,
                    "the first login-state answer is Login Success itself");
            String uuid = ByteBufOps.readString(body, 64);
            String name = ByteBufOps.readString(body, 64);
            assertEquals("Compressless", name, "the reference's body order: uuid then username");
            assertEquals(36, uuid.length(), "the offline v3 uuid string form");
            assertEquals(4, countChar(uuid, '-'), "the uuid's dashed five-group form");
            assertEquals(0, body.readableBytes(),
                    "the Login Success body is exactly the reference's two strings");
            assertEquals(java.util.UUID.nameUUIDFromBytes(
                            ("OfflinePlayer:Compressless").getBytes(StandardCharsets.UTF_8)).toString(),
                    uuid, "the offline uuid convention");
        }
        assertEquals(ServerState.RUNNING, disabledServer.state());
    }

    /**
     * The disabled branch's whole join burst stays in the UNCOMPRESSED
     * frame form: every frame parses as [packet-id varint][body] with a
     * known play id — including frames far over the reference's 256-byte
     * compression threshold (the chunk burst), which a compression stage
     * would have zlib'd into a two-varint form that cannot parse as a
     * packet.
     */
    @Test
    void theExchangelessJoinBurstRidesUncompressedFramesIncludingOverThresholdChunks() throws Exception {
        Set<Integer> knownIds = knownPlayIds();
        try (TestClient18 client = new TestClient18("127.0.0.1", disabledAdapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Framer");
            client.readLoginSuccess();

            int frames = 0;
            int overThresholdFrames = 0;
            boolean joinGameChecked = false;
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline) {
                byte[] frame = client.readPacket();
                frames++;
                ByteBuf body = Unpooled.wrappedBuffer(frame);
                int packetId = ByteBufOps.readVarInt(body);
                assertTrue(knownIds.contains(packetId),
                        "frame " + frames + " carries a known play id (0x"
                                + Integer.toHexString(packetId) + ") — a compressed form "
                                + "would fail exactly here");
                if (frame.length > 256) {
                    overThresholdFrames++;
                }
                if (packetId == Protocol18.S2C_JOIN_GAME && !joinGameChecked) {
                    body.readInt();          // entity id
                    body.readByte();         // gamemode
                    body.readByte();         // dimension
                    body.readByte();         // difficulty
                    body.readByte();         // max players (legacy)
                    ByteBufOps.readString(body, 32); // level type
                    body.readBoolean();      // reduced debug info
                    assertEquals(0, body.readableBytes(),
                            "Join Game's body parses to the exact frame end uncompressed");
                    joinGameChecked = true;
                }
                if (packetId == Protocol18.S2C_PLAYER_POSITION_AND_LOOK
                        && joinGameChecked && overThresholdFrames > 0) {
                    break; // the whole burst rode through, chunks included
                }
            }
            assertTrue(joinGameChecked, "the Join Game frame was observed and pinned");
            assertTrue(overThresholdFrames >= 1,
                    "at least one over-threshold (256B+) frame rode uncompressed: "
                            + overThresholdFrames + " of " + frames + " frames");
        }
    }

    // ------------------------------------------------------------------ the canonical branch (256)

    /**
     * The canonical login performs the exchange FIRST: the initial
     * login-state answer is Set Compression (0x03) carrying exactly the
     * configured threshold (256 — the two-byte varint 81 02), the frame
     * byte-exact against the reference's CompressionThresholdS2CPacket
     * form, and Login Success follows as the SECOND frame — the
     * reference's own acceptLogin order, lines 92-104.
     */
    @Test
    void theCanonicalLoginPerformsTheCompressionExchangeFirst() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", canonicalAdapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Compressed");

            byte[] exchange = client.readPacket();
            ByteBuf body = Unpooled.wrappedBuffer(exchange);
            assertEquals(Protocol18.S2C_LOGIN_SET_COMPRESSION, ByteBufOps.readVarInt(body),
                    "the first login-state answer is Set Compression itself");
            assertEquals(256, ByteBufOps.readVarInt(body), "the configured threshold rides whole");
            assertEquals(0, body.readableBytes(),
                    "the exchange body is exactly the reference's one varint");
            // The byte-exact pin: [0x03][varint 256] = 03 80 02, three bytes.
            assertArrayEquals(new byte[]{0x03, (byte) 0x80, 0x02}, exchange,
                    "the exchange frame is byte-exact (varint 256 = 80 02)");

            // A real client arms its own outbound form on 0x03
            // (ClientLoginNetworkHandler lines 101-105).
            client.armCompression();

            byte[] success = client.readPacket();
            ByteBuf successBody = Unpooled.wrappedBuffer(success);
            int dataLength = ByteBufOps.readVarInt(successBody);
            assertEquals(0, dataLength,
                    "Login Success (under the threshold) rides the raw form — data-length zero");
            assertEquals(Protocol18.S2C_LOGIN_SUCCESS, ByteBufOps.readVarInt(successBody),
                    "the second frame is Login Success, the reference's own order");
            String uuid = ByteBufOps.readString(successBody, 64);
            String name = ByteBufOps.readString(successBody, 64);
            assertEquals("Compressed", name);
            assertEquals(java.util.UUID.nameUUIDFromBytes(
                            ("OfflinePlayer:Compressed").getBytes(StandardCharsets.UTF_8)).toString(),
                    uuid);
            assertEquals(0, successBody.readableBytes(),
                    "the Login Success body is exactly the reference's two strings");
        }
        assertEquals(ServerState.RUNNING, canonicalServer.state());
    }

    /**
     * The canonical branch's whole join burst rides the two-varint form:
     * every frame inflates through the independent client-side walk (a
     * fresh Inflater per frame, the size checked against the declared
     * data-length) and parses as [packet-id varint][body] with a known
     * play id — the over-threshold chunk frames arrive DEFLATED (a
     * non-zero data-length whose wire size is smaller than the payload),
     * and the below-threshold frames ride the raw zero-length form.
     */
    @Test
    void theCanonicalJoinBurstRidesCompressedFramesThatInflateByteExact() throws Exception {
        Set<Integer> knownIds = knownPlayIds();
        try (TestClient18 client = new TestClient18("127.0.0.1", canonicalAdapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Inflater");
            client.readPacket(); // the exchange (0x03) — pinned by the test above
            client.armCompression();
            client.readPacket(); // Login Success (0x02, raw ride)

            int frames = 0;
            int deflatedFrames = 0;
            int rawRideFrames = 0;
            boolean joinGameChecked = false;
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline) {
                byte[] frame = client.readPacket();
                frames++;
                ByteBuf wire = Unpooled.wrappedBuffer(frame);
                int dataLength = ByteBufOps.readVarInt(wire);
                byte[] payload;
                if (dataLength == 0) {
                    payload = new byte[wire.readableBytes()];
                    wire.readBytes(payload);
                    rawRideFrames++;
                } else {
                    byte[] compressed = new byte[wire.readableBytes()];
                    wire.readBytes(compressed);
                    assertTrue(compressed.length < dataLength,
                            "a deflated frame's wire size is smaller than its payload (frame "
                                    + frames + ": " + compressed.length + " vs " + dataLength + ")");
                    Inflater inflater = new Inflater();
                    inflater.setInput(compressed);
                    payload = new byte[dataLength];
                    assertEquals(dataLength, inflater.inflate(payload),
                            "the frame inflates to exactly its declared data length");
                    assertTrue(inflater.finished(), "the zlib stream is whole");
                    inflater.reset();
                    deflatedFrames++;
                }
                wire.release();
                ByteBuf body = Unpooled.wrappedBuffer(payload);
                int packetId = ByteBufOps.readVarInt(body);
                assertTrue(knownIds.contains(packetId),
                        "frame " + frames + " inflates to a known play id (0x"
                                + Integer.toHexString(packetId) + ")");
                if (packetId == Protocol18.S2C_JOIN_GAME && !joinGameChecked) {
                    body.readInt();          // entity id
                    body.readByte();         // gamemode
                    body.readByte();         // dimension
                    body.readByte();         // difficulty
                    body.readByte();         // max players (legacy)
                    ByteBufOps.readString(body, 32); // level type
                    body.readBoolean();      // reduced debug info
                    assertEquals(0, body.readableBytes(),
                            "Join Game's body parses to the exact frame end once inflated");
                    joinGameChecked = true;
                }
                if (packetId == Protocol18.S2C_PLAYER_POSITION_AND_LOOK
                        && joinGameChecked && deflatedFrames > 0) {
                    break; // the whole burst rode through, chunks deflated and inflated back
                }
            }
            assertTrue(joinGameChecked, "the Join Game frame was observed and pinned");
            assertTrue(deflatedFrames >= 1,
                    "at least one over-threshold frame arrived deflated: "
                            + deflatedFrames + " of " + frames + " frames");
            assertTrue(rawRideFrames >= 1,
                    "the below-threshold frames ride the raw zero-length form: "
                            + rawRideFrames + " of " + frames + " frames");
        }
    }

    // ------------------------------------------------------------------ shared

    /** The play-state ids this join burst can carry (Protocol18's own set). */
    private static Set<Integer> knownPlayIds() {
        return Set.of(
                Protocol18.S2C_JOIN_GAME, Protocol18.S2C_SERVER_DIFFICULTY,
                Protocol18.S2C_SPAWN_POSITION, Protocol18.S2C_TIME_UPDATE,
                Protocol18.S2C_CHUNK_DATA, Protocol18.S2C_PLAYER_POSITION_AND_LOOK,
                Protocol18.S2C_CHUNK_BULK, Protocol18.S2C_MULTI_BLOCK_CHANGE,
                Protocol18.S2C_CHAT, Protocol18.S2C_PLAYER_INFO,
                Protocol18.S2C_PLUGIN_MESSAGE, Protocol18.S2C_WINDOW_ITEMS,
                Protocol18.S2C_UPDATE_HEALTH, Protocol18.S2C_SET_EXPERIENCE,
                Protocol18.S2C_PLAYER_ABILITIES, Protocol18.S2C_HELD_ITEM_CHANGE,
                Protocol18.S2C_WORLD_BORDER, Protocol18.S2C_PLAYER_LIST_HEADER_FOOTER,
                Protocol18.S2C_KEEP_ALIVE, Protocol18.S2C_CHANGE_GAME_STATE,
                Protocol18.S2C_SPAWN_ENTITY, Protocol18.S2C_SPAWN_MOB,
                Protocol18.S2C_ENTITY_METADATA, Protocol18.S2C_ENTITY_VELOCITY,
                Protocol18.S2C_SCOREBOARD_OBJECTIVE, Protocol18.S2C_UPDATE_SCORE,
                Protocol18.S2C_DISPLAY_SCOREBOARD, Protocol18.S2C_TEAMS,
                Protocol18.S2C_NAMED_SPAWN,
                Protocol18.S2C_ATTACH_ENTITY, Protocol18.S2C_ENTITY_TELEPORT);
    }

    private static int countChar(String value, char c) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }
}
