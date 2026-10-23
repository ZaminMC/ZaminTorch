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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R-3 of the vanilla parity remainder: the login compression-threshold
 * exchange, checked at the wire.
 *
 * <p><b>The reference law (reference/1.8.8)</b>: {@code ServerLoginNetworkHandler
 * .acceptLogin} lines 92-101 sends the login-state Set Compression packet
 * (0x03, one varint — the threshold, CompressionThresholdS2CPacket lines
 * 20-26) BEFORE Login Success whenever the configured threshold is ≥ 0
 * ({@code MinecraftServer.getNetworkCompressionThreshold} defaults to 256;
 * a dedicated server reads network-compression-threshold from its
 * properties, and -1 disables the exchange entirely), then installs the
 * zlib CompressionDecoder/CompressionEncoder pair (Connection.
 * setCompressionThreshold lines 325-346) — from then every frame is
 * length-varint + data-length-varint + zlib payload. The client only ever
 * enables decompression when it RECEIVES 0x03 (ClientLoginNetworkHandler
 * lines 101-105): an exchange-less login is the protocol's own legal
 * uncompressed mode, byte-identical framing to the no-compression shape
 * (SizePrepender lines 8-20 / SplitterHandler: one length varint, max 3
 * bytes, then the payload).</p>
 *
 * <p><b>Torch's recorded divergence</b>: the engine never performs the
 * exchange — its permanent mode is the reference's threshold=-1 shape (the
 * ENGINEERING_PLAN's own slice decision: "compression disabled at first,
 * threshold introduced when measured necessary"). This test PINS that
 * state: the first S→C frame after Login Start is exactly Login Success
 * (0x02, never 0x03) with the reference's byte-exact body, the framing is
 * the one-length-varint uncompressed form for the whole join burst
 * (including over-threshold chunk frames), and FrameCodec's encoder emits
 * the SizePrepender form — not the two-varint CompressionEncoder form. If
 * compression is ever introduced, these pins fail until the divergence
 * record is rewritten with the owner's decision.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoginCompressionWireTest {

    @org.junit.jupiter.api.io.TempDir
    static Path dataDir;

    private EngineServer server;
    private V18ProtocolServer adapter;

    @BeforeAll
    void bootStack() throws Exception {
        EngineConfig config = new EngineConfig(
                "127.0.0.1", 0, "world", "ZaminTorch test", 20, 4, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
        adapter = new V18ProtocolServer(server, 60_000);
        adapter.start(server);
    }

    @AfterAll
    void stopStack() {
        server.shutdown(adapter::shutdown);
    }

    /**
     * FrameCodec's own frame form, driven directly: exactly the reference's
     * SizePrepender shape — one varint length prefix, then the payload. The
     * reference's CompressionEncoder would emit a SECOND varint (the
     * data-length, 0 for below-threshold frames) before the payload; this pin
     * fails the moment a compression stage enters the pipeline.
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
                "no second (data-length) varint — the compressed form's own marker");
        framed.release();
        channel.finishAndReleaseAll();
    }

    /**
     * The exchange-less login: the FIRST frame after Login Start is exactly
     * Login Success (0x02) — never the reference's Set Compression (0x03) —
     * and its body is byte-for-byte the reference's LoginSuccessS2CPacket
     * form (varint-length uuid string, then varint-length username; the
     * frame fully consumed).
     */
    @Test
    void theLoginSequenceSkipsTheCompressionExchangeStraightToLoginSuccess() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Compressless");

            byte[] frame = client.readPacket();
            ByteBuf body = Unpooled.wrappedBuffer(frame);
            int packetId = ByteBufOps.readVarInt(body);
            assertNotEquals(0x03, packetId,
                    "the reference's Set Compression exchange never rides this login");
            assertEquals(Protocol18.S2C_LOGIN_SUCCESS, packetId,
                    "the first login-state answer is Login Success itself");
            String uuid = ByteBufOps.readString(body, 64);
            String name = ByteBufOps.readString(body, 64);
            assertEquals("Compressless", name, "the reference's body order: uuid then username");
            assertEquals(36, uuid.length(), "the offline v3 uuid string form");
            assertEquals(4, countChar(uuid, '-'), "the uuid's dashed five-group form");
            assertEquals(0, body.readableBytes(),
                    "the Login Success body is exactly the reference's two strings");
            // The offline-mode identity itself (the v3 of "OfflinePlayer:name").
            assertEquals(java.util.UUID.nameUUIDFromBytes(
                            ("OfflinePlayer:Compressless").getBytes(StandardCharsets.UTF_8)).toString(),
                    uuid, "the offline uuid convention");
        }
        assertEquals(ServerState.RUNNING, server.state());
    }

    /**
     * The whole join burst stays in the UNCOMPRESSED frame form: every frame
     * parses as [packet-id varint][body] with a known play id — including
     * frames far over the reference's 256-byte compression threshold (the
     * chunk burst), which a compression stage would have zlib'd into a
     * second-varint form that cannot parse as a packet.
     */
    @Test
    void theJoinBurstRidesUncompressedFramesIncludingOverThresholdChunks() throws Exception {
        // The play-state ids this join burst can carry (Protocol18's own set).
        Set<Integer> knownIds = Set.of(
                Protocol18.S2C_JOIN_GAME, Protocol18.S2C_SERVER_DIFFICULTY,
                Protocol18.S2C_SPAWN_POSITION, Protocol18.S2C_TIME_UPDATE,
                Protocol18.S2C_CHUNK_DATA, Protocol18.S2C_PLAYER_POSITION_AND_LOOK,
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
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
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
                    // The exact-body pin: the reference's Join Game layout,
                    // fully consumed — the uncompressed parse to the last byte.
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
