package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.ServerState;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE QUIET-DISCONNECT CLASSIFICATION (ML-009b — the audit's B-4 row):
 * an abrupt client TCP reset arrives as the IOException family, and the
 * reference answers it with ONE quiet disconnect line — never a WARNING,
 * never a stacktrace (Connection.exceptionCaught lines 110-119 hand the
 * "Internal Exception: &lt;cause&gt;" reason to the walk;
 * ServerPlayNetworkHandler.onDisconnect line 573 logs the INFO line).
 * Proven over the real wire: an RST (SO_LINGER(0) + close) through a
 * logged-in session stays quiet — the engine's own "Player disconnected"
 * record carries the cause — while a genuinely malformed frame (a
 * chat-message packet with no body) stays LOUD exactly as before: the
 * WARNING + the throwable for the frame that broke the connection
 * (§126/§54 — an invisible drop is undiagnosable). Both walks leave the
 * server RUNNING.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DisconnectClassificationTest {

    @TempDir
    static Path dataDir;

    private EngineServer server;
    private V18ProtocolServer adapter;

    // The two loggers the classification writes through, captured for the
    // per-test assertions (JUL's default INFO level already carries every
    // record these arms emit; no filtering changed).
    private static final Logger CONNECTION_LOGGER =
            Logger.getLogger(V18Connection.class.getName());
    private static final Logger ENGINE_LOGGER =
            Logger.getLogger(EngineServer.class.getName());
    private static final List<LogRecord> RECORDS = new CopyOnWriteArrayList<>();
    private static final Handler CAPTURE = new Handler() {
        @Override
        public void publish(LogRecord record) {
            RECORDS.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    @BeforeAll
    void bootStack() throws Exception {
        CONNECTION_LOGGER.addHandler(CAPTURE);
        ENGINE_LOGGER.addHandler(CAPTURE);
        EngineConfig config = new EngineConfig(
                "127.0.0.1", 0, "world", "ZaminTorch test", 20, 4, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
        // 2s cycle: the scripted clients never answer keep-alives, so the
        // 6s kick window must cover each test's whole scripted flow.
        adapter = new V18ProtocolServer(server, 2_000);
        adapter.start(server);
    }

    @AfterAll
    void stopStack() {
        server.shutdown(adapter::shutdown);
        CONNECTION_LOGGER.removeHandler(CAPTURE);
        ENGINE_LOGGER.removeHandler(CAPTURE);
    }

    private void awaitCondition(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private PlayerSession login(TestClient18 client, String name) throws Exception {
        client.sendHandshake(47, 2);
        client.sendLoginStart(name);
        client.readLoginSuccess();
        client.readUntilPositionAndLook();
        awaitCondition(() -> server.playerRegistry().byName(name).isPresent(),
                "player registered: " + name);
        PlayerSession session = server.playerRegistry().byName(name).orElseThrow();
        awaitCondition(() -> session.state() == PlayerState.PLAYING, name + " reached PLAYING");
        return session;
    }

    private static List<LogRecord> recordsFrom(Logger logger) {
        return RECORDS.stream()
                .filter(record -> logger.getName().equals(record.getLoggerName()))
                .toList();
    }

    @Test
    void anAbruptResetLogsOneQuietDisconnectLineNeverAStack() throws Exception {
        TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort());
        try {
            login(client, "Resetter");
            RECORDS.clear();
            client.resetAbruptly();
            awaitCondition(() -> server.players().isEmpty(), "the resetter left");

            // THE QUIET LAW: the connection logger stayed below WARNING and
            // never attached a throwable — the netty stacktrace noise the
            // audit recorded is gone.
            for (LogRecord record : recordsFrom(CONNECTION_LOGGER)) {
                assertTrue(record.getLevel().intValue() < Level.WARNING.intValue(),
                        "the reset stayed quiet: " + record.getLevel()
                                + " " + record.getMessage());
                assertNull(record.getThrown(), "no stacktrace rode the reset");
            }
            // ONE quiet disconnect line through the engine's own walk, the
            // reference's "Internal Exception" reason carrying the cause —
            // the error is classified, never swallowed.
            List<LogRecord> disconnects = recordsFrom(ENGINE_LOGGER).stream()
                    .filter(record -> record.getMessage() != null
                            && record.getMessage().contains("Player disconnected: Resetter"))
                    .toList();
            assertEquals(1, disconnects.size(),
                    "exactly one quiet disconnect line: " + disconnects);
            assertEquals(Level.INFO, disconnects.get(0).getLevel());
            assertNull(disconnects.get(0).getThrown(), "the quiet line carries no stack");
            // THE COORDINATOR'S REVIEW CORRECTION: the original pin demanded
            // the reason contain "Internal Exception" — over-pinning TCP's
            // delivery flavor. A reset (SO_LINGER 0 + close) can reach the
            // server either way: the IOException family through
            // exceptionCaught ("Internal Exception: <cause>" — Connection's
            // own reason form) OR a plain channelInactive ("connection
            // closed") when the RST lands on an idle reader. Both are the
            // reference's own quiet vocabulary; the pinned law (the row's
            // actual acceptance) is ONE quiet INFO line, never a WARNING,
            // never a stacktrace — observed both ways across runs.
            String reason = disconnects.get(0).getMessage();
            assertTrue(reason.contains("Player disconnected: Resetter")
                            && (reason.contains("Internal Exception")
                                || reason.contains("connection closed")),
                    "the quiet family's reason form: " + reason);
        } finally {
            client.close();
        }
        assertEquals(ServerState.RUNNING, server.state(), "the server survived the reset");
    }

    @Test
    void aGenuinelyMalformedFrameStaysLoud() throws Exception {
        TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort());
        try {
            login(client, "Garbler");
            // The scripted client must keep DRAINING the socket after login:
            // the join burst would otherwise overflow the receive buffer and
            // RST the connection before the malformed frame is read — that
            // reset is the quiet arm's own event, not this test's.
            AtomicBoolean keepAliveSeen = new AtomicBoolean();
            AtomicBoolean stopDrain = new AtomicBoolean();
            Thread drainer = new Thread(() -> {
                try {
                    client.setReadTimeout(500);
                    while (!stopDrain.get()) {
                        byte[] packet;
                        try {
                            packet = client.readPacket();
                        } catch (java.net.SocketTimeoutException quiet) {
                            continue; // nothing to read right now — keep draining
                        }
                        io.netty.buffer.ByteBuf id =
                                io.netty.buffer.Unpooled.wrappedBuffer(packet);
                        if (ByteBufOps.readVarInt(id) == Protocol18.S2C_KEEP_ALIVE) {
                            keepAliveSeen.set(true);
                        }
                    }
                } catch (java.io.IOException drainedOrClosed) {
                    // the connection ended (this test's own close) — exit
                }
            }, "it-garbler-drain");
            drainer.setDaemon(true);
            drainer.start();
            // One server keep-alive consumed end-to-end: the connection is
            // proven healthy and drained right now.
            awaitCondition(keepAliveSeen::get, "a keep-alive drained");

            RECORDS.clear();
            // A chat-message packet with no body: the string read breaks
            // inside a KNOWN packet — a protocol violation, not an unclean
            // exit. This must stay loud.
            client.sendPacket(new byte[] {(byte) Protocol18.C2S_CHAT_MESSAGE});
            awaitCondition(() -> recordsFrom(CONNECTION_LOGGER).stream().anyMatch(
                            record -> record.getLevel() == Level.WARNING
                                    && record.getThrown() != null),
                    "the loud WARNING with its throwable landed");
            // The loud walk's own engine record: the "protocol error"
            // reason — NOT the quiet arm's "Internal Exception" form.
            awaitCondition(() -> recordsFrom(ENGINE_LOGGER).stream().anyMatch(
                            record -> record.getMessage() != null
                                    && record.getMessage().contains(
                                            "Player disconnected: Garbler (protocol error)")),
                    "the loud walk's engine record landed");
            awaitCondition(() -> server.players().isEmpty(), "the garbler left");
            stopDrain.set(true);
            drainer.join(2_000);
        } finally {
            client.close();
        }
        assertEquals(ServerState.RUNNING, server.state(), "the server survived the garbage");
    }
}
