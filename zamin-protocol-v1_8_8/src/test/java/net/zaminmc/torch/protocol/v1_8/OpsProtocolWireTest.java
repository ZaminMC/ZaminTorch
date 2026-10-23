package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ML-012 + ML-017 — the ops wire pair on the real socket: Server Difficulty
 * 0x41 (the join walk's own packet at the reference's onLogin slot, and the
 * /difficulty change-time fan-out to every connected client) and the
 * resource pack end-to-end (the configured join-time 0x48, the unconfigured
 * silence, and the C2S 0x19 status logging).
 *
 * <p>The wire forms are pinned against the reference packet classes
 * themselves: DifficultyS2CPacket.write carries ONE difficulty byte (the
 * locked flag rides the constructor only — protocol 47's own shape), and
 * ResourcePackS2CPacket.write carries the url string then the hash string,
 * nothing else (the 1.9+ request-id varint never rides this protocol).</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpsProtocolWireTest {

    @org.junit.jupiter.api.io.TempDir
    static Path dataDir;

    /** The unconfigured stack (no resource-pack row): the difficulty lane. */
    private EngineServer plainServer;
    private V18ProtocolServer plainAdapter;

    /** The configured stack: the resource-pack rows are set. */
    private EngineServer packedServer;
    private V18ProtocolServer packedAdapter;

    private static final String PACK_URL = "https://example.com/torch-pack.zip";
    private static final String PACK_HASH = "0123456789abcdef0123456789abcdef01234567"; // 40 chars

    @BeforeAll
    void bootStacks() throws Exception {
        plainServer = new EngineServer(new EngineConfig(
                "127.0.0.1", 0, "world", "ops wire tests", 20, 4, 20,
                dataDir.resolve("plain").toString()));
        plainServer.start();
        plainAdapter = new V18ProtocolServer(plainServer, 2_000);
        plainAdapter.start(plainServer);

        packedServer = new EngineServer(new EngineConfig(
                "127.0.0.1", 0, "world", "ops wire tests", 20, 4, 20,
                dataDir.resolve("packed").toString(),
                net.zaminmc.torch.GameMode.SURVIVAL, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE, false,
                EngineConfig.DEFAULT_SPAWN_PROTECTION,
                false, "", EngineConfig.UNSET_REMOTE_PORT, false, EngineConfig.UNSET_REMOTE_PORT,
                PACK_URL, PACK_HASH));
        packedServer.start();
        packedAdapter = new V18ProtocolServer(packedServer, 2_000);
        packedAdapter.start(packedServer);
    }

    @AfterAll
    void stopStacks() {
        plainServer.shutdown(plainAdapter::shutdown);
        packedServer.shutdown(packedAdapter::shutdown);
    }

    private static void awaitCondition(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    /** Joins one scripted client through to the initial position packet. */
    private TestClient18 joinClient(V18ProtocolServer adapter, String name) throws Exception {
        TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort());
        client.sendHandshake(47, 2);
        client.sendLoginStart(name);
        String identity = client.readLoginSuccess();
        assertTrue(identity.endsWith("/" + name), identity);
        client.readUntilPositionAndLook();
        return client;
    }

    // ------------------------------------------------------------- ML-012 (0x41)

    @Test
    void theJoinWalkCarriesTheDifficultyPacketRightAfterJoinGame() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", plainAdapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("JoinDiff");
            client.readLoginSuccess();

            // The reference's own onLogin order (PlayerManager lines 113-129):
            // Login(Join Game) -> brand -> Difficulty -> Spawn Point. The
            // engine has no brand packet, so the wire reads Join Game then
            // Server Difficulty then Spawn Position.
            byte[] joinGame = client.readPacket();
            assertEquals(Protocol18.S2C_JOIN_GAME, client.readPacketId(joinGame),
                    "the join burst opens with Join Game");

            byte[] difficulty = client.readPacket();
            assertEquals(Protocol18.S2C_SERVER_DIFFICULTY, client.readPacketId(difficulty),
                    "Server Difficulty follows Join Game (the reference's own slot)");
            io.netty.buffer.ByteBuf body =
                    io.netty.buffer.Unpooled.wrappedBuffer(difficulty);
            ByteBufOps.readVarInt(body); // the packet id
            // DifficultyS2CPacket.write: ONE byte, the difficulty id — and
            // nothing else (the locked flag never rides protocol 47). The
            // byte is the engine's LIVE runtime tier (a prior test may have
            // flipped it; the shared stack's tier is the join packet's own
            // source of truth).
            int tier = body.readUnsignedByte();
            assertEquals(plainServer.difficulty(), tier,
                    "the join packet carries the engine's live difficulty tier");
            assertTrue(tier >= 0 && tier <= 3, "the reference's own id band 0..3");
            assertFalse(body.isReadable(),
                    "the reference's write carries the single difficulty byte alone");

            byte[] spawnPosition = client.readPacket();
            assertEquals(Protocol18.S2C_SPAWN_POSITION, client.readPacketId(spawnPosition),
                    "Spawn Position follows (the reference's own order)");
            client.readUntilPositionAndLook();
        }
        awaitCondition(() -> plainServer.players().isEmpty(), "the joiner left");
    }

    @Test
    void difficultyChangeReachesEveryConnectedClientBeforeRespawn() throws Exception {
        try (TestClient18 first = joinClient(plainAdapter, "HardOne");
             TestClient18 second = joinClient(plainAdapter, "HardTwo")) {
            awaitCondition(() -> plainServer.players().size() == 2, "both joiners present");
            plainServer.consoleCommand("difficulty hard");
            awaitCondition(() -> plainServer.difficulty() == 3, "the hard tier landed");

            for (TestClient18 client : new TestClient18[]{first, second}) {
                byte[] packet = client.readPacketOfType(Protocol18.S2C_SERVER_DIFFICULTY, 5_000);
                io.netty.buffer.ByteBuf body =
                        io.netty.buffer.Unpooled.wrappedBuffer(packet);
                ByteBufOps.readVarInt(body); // the packet id
                assertEquals(3, body.readUnsignedByte(),
                        "/difficulty hard lands the byte 3 on every client");
                assertFalse(body.isReadable(),
                        "the change-time packet is the same single-byte form");
            }
            // A second flip re-fans (the tier id follows the command, not
            // the respawn): peaceful answers 0.
            plainServer.consoleCommand("difficulty peaceful");
            awaitCondition(() -> plainServer.difficulty() == 0, "the peaceful tier landed");
            for (TestClient18 client : new TestClient18[]{first, second}) {
                byte[] packet = client.readPacketOfType(Protocol18.S2C_SERVER_DIFFICULTY, 5_000);
                io.netty.buffer.ByteBuf body =
                        io.netty.buffer.Unpooled.wrappedBuffer(packet);
                ByteBufOps.readVarInt(body);
                assertEquals(0, body.readUnsignedByte(), "the second flip re-syncs the byte 0");
            }
        }
        awaitCondition(() -> plainServer.players().isEmpty(), "the joiners left");
    }

    // ------------------------------------------------------------- ML-017 (0x48)

    @Test
    void theConfiguredResourcePackRidesTheJoinWalkInTheReferenceForm() throws Exception {
        try (TestClient18 client = joinClient(packedAdapter, "Packed")) {
            // The reference's own placement (PlayerManager.onLogin lines
            // 147-150): right after sendWorldInfo — here, after the world
            // border's join sync, before the window items.
            byte[] packet = client.readPacketOfType(Protocol18.S2C_RESOURCE_PACK_SEND, 5_000);
            io.netty.buffer.ByteBuf body =
                    io.netty.buffer.Unpooled.wrappedBuffer(packet);
            ByteBufOps.readVarInt(body); // the packet id
            // ResourcePackS2CPacket.write: the url string then the hash
            // string, nothing else (no 1.9-style request id).
            assertEquals(PACK_URL, ByteBufOps.readString(body, 32767), "the configured url");
            assertEquals(PACK_HASH, ByteBufOps.readString(body, 40), "the configured hash");
            assertFalse(body.isReadable(),
                    "the reference's write carries the two strings alone");
        }
        awaitCondition(() -> packedServer.players().isEmpty(), "the joiner left");
    }

    @Test
    void theUnconfiguredJoinNeverSendsTheResourcePack() throws Exception {
        try (TestClient18 client = joinClient(plainAdapter, "Unpacked")) {
            // Read past the whole join tail: the resource-pack slot sits
            // between the border init and the window items — once the window
            // items (0x30) arrive, the send point has passed unsent.
            List<Integer> seen = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < deadline) {
                byte[] packet = client.readPacket();
                int id = client.readPacketId(packet);
                seen.add(id);
                if (id == Protocol18.S2C_WINDOW_ITEMS) {
                    break;
                }
            }
            assertTrue(seen.contains(Protocol18.S2C_WINDOW_ITEMS),
                    "the join tail drained to the window items: " + seen);
            assertFalse(seen.contains(Protocol18.S2C_RESOURCE_PACK_SEND),
                    "no url configured means no 0x48 ever (the reference's own gate)");
        }
        awaitCondition(() -> plainServer.players().isEmpty(), "the joiner left");
    }

    @Test
    void theResourcePackStatusAnswerIsLoggedInTheReferenceVocabulary() throws Exception {
        // The reference's server handler is empty (ServerPlayNetworkHandler
        // line 568); the owed behavior is the observable log line. The wire
        // form is ResourcePackC2SPacket.read's own: the hash string then the
        // result varint (the Response enum order).
        List<String> lines = new CopyOnWriteArrayList<>();
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                lines.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger connectionLogger = Logger.getLogger(V18Connection.class.getName());
        connectionLogger.addHandler(capture);
        connectionLogger.setLevel(Level.INFO);
        boolean oldParent = connectionLogger.getUseParentHandlers();
        connectionLogger.setUseParentHandlers(false);
        // The connection stays OPEN while the frame lands: closing a socket
        // with unread server packets in its receive buffer races a TCP RST
        // that would discard the frame before the event loop reads it.
        try (TestClient18 client = joinClient(plainAdapter, "StatusReporter")) {
            io.netty.buffer.ByteBuf status = io.netty.buffer.Unpooled.buffer(64);
            ByteBufOps.writeVarInt(status, Protocol18.C2S_RESOURCE_PACK_STATUS);
            ByteBufOps.writeString(status, PACK_HASH);
            ByteBufOps.writeVarInt(status, 1); // DECLINED (the enum's own order)
            byte[] payload = new byte[status.readableBytes()];
            status.readBytes(payload);
            status.release();
            client.sendPacket(payload);
            try {
                awaitCondition(() -> lines.stream().anyMatch(
                                line -> line.contains("StatusReporter") && line.contains("DECLINED")),
                        "the declined status logged: " + lines);
                String line = lines.stream().filter(l -> l.contains("DECLINED"))
                        .findFirst().orElseThrow();
                assertTrue(line.contains(PACK_HASH), "the logged line carries the hash: " + line);
            } finally {
                connectionLogger.removeHandler(capture);
                connectionLogger.setUseParentHandlers(oldParent);
            }
        }
        awaitCondition(() -> plainServer.players().isEmpty(), "the joiner left");
    }
}
