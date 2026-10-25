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

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R-4 of the vanilla parity remainder: the keep-alive family's wire law —
 * the send cadence's payload semantic, the exchange keeping a silent-ish
 * client connected, and the timeout kick's observable form — against the
 * reference's own law.
 *
 * <p><b>The reference (reference/1.8.8)</b>: {@code ServerPlayNetworkHandler
 * .tick} lines 131-137 send a keep-alive every 41 ticks (&gt; 40 since the
 * last) whose payload is {@code (int) getTimeMillis()} — the send-time clock,
 * not a counter; {@code handleKeepAlive} lines 910-914 exact-matches the echo
 * against the saved value and feeds the (ping·3 + round-trip)/4 smoothing.
 * The reference has NO application-level unanswered-keep-alive kick at all:
 * its only drop for a silent connection is the pipeline's
 * {@code ReadTimeoutHandler(30)} (ConnectionListener.java line 104 — thirty
 * seconds of no inbound bytes), whose close path (Connection.disconnect,
 * lines 234-239) never sends a Disconnect packet.</p>
 *
 * <p><b>Torch's shape (the recorded divergence)</b>: the keep-alive payload
 * carries the reference's own millis form (the R-4 repair — it was a
 * from-scratch incrementing counter before); the exchange and the ping
 * smoothing are the reference's; AND the engine adds the slice-#1 design's
 * application-level kick (ENGINEERING_PLAN §10b "keep-alive with timeout
 * kick"): a keep-alive unanswered for 3× the interval drops the connection
 * WITH a play Disconnect packet carrying "Timed out" — stricter than the
 * reference for a client that sends traffic but never answers keep-alives
 * (the reference's read-timeout only fires on total inbound silence, which
 * Torch also carries, pinned by LoginFloodBoundaryTest at the reference's
 * own 30s). The kick and the 10s default cadence are the plan's own design,
 * recorded here rather than changed.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KeepAliveTimeoutWireTest {

    @org.junit.jupiter.api.io.TempDir
    static Path dataDir;

    private static final long INTERVAL_MS = 300;
    private static final long KICK_WINDOW_MS = INTERVAL_MS * 3;

    private EngineServer server;
    private V18ProtocolServer adapter;

    @BeforeAll
    void bootStack() throws Exception {
        EngineConfig config = new EngineConfig(
                "127.0.0.1", 0, "world", "ZaminTorch test", 20, 4, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
        // 300ms cadence → the kick window is 900ms; the read-idle bound stays
        // at the reference's own 30s so the kick is the only timer in play.
        adapter = new V18ProtocolServer(server, INTERVAL_MS);
        adapter.start(server);
    }

    @AfterAll
    void stopStack() {
        server.shutdown(adapter::shutdown);
    }

    private void awaitCondition(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
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

    /**
     * The payload law: every keep-alive id is the send-time clock's low 32
     * bits — the reference's own form (a fresh millis value per send, within
     * a bounded window of now). The old counter form (1, 2, 3...) fails this
     * pin by three orders of magnitude.
     */
    private static long wrappedDeltaMs(int payload, long now) {
        long diff = (payload & 0xFFFFFFFFL) - (now & 0xFFFFFFFFL);
        if (diff > Integer.MAX_VALUE) {
            diff -= 1L << 32;
        }
        if (diff < Integer.MIN_VALUE) {
            diff += 1L << 32;
        }
        return diff;
    }

    @Test
    void thePayloadIsTheReferenceSendTimeMillisAndTheExchangeKeepsTheClientAlive() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            login(client, "Exchanger");
            int previousPayload = Integer.MIN_VALUE;
            // Four full cycles: each answer clears the pending keep-alive, the
            // next send carries a FRESH millis id, and the connection survives
            // the whole window — the exchange is what keeps a client connected.
            for (int cycle = 0; cycle < 4; cycle++) {
                byte[] frame = client.readPacketOfType(Protocol18.S2C_KEEP_ALIVE, 5_000);
                io.netty.buffer.ByteBuf keepAlive = io.netty.buffer.Unpooled.wrappedBuffer(frame);
                ByteBufOps.readVarInt(keepAlive); // packet id
                int payload = ByteBufOps.readVarInt(keepAlive);
                long delta = wrappedDeltaMs(payload, System.currentTimeMillis());
                assertTrue(Math.abs(delta) < 120_000,
                        "keep-alive payload must be the send-time millis (was " + payload
                                + ", " + delta + "ms from now — the counter form?)");
                if (cycle > 0) {
                    assertNotEquals(previousPayload, payload,
                            "each send carries a fresh millis id");
                }
                previousPayload = payload;
                client.sendKeepAliveResponse(payload);
            }
            // The player is still connected after four answered cycles.
            assertTrue(server.playerRegistry().byName("Exchanger").isPresent(),
                    "the exchanger stayed connected through four answered cycles");
        }
    }

    @Test
    void anUnansweredKeepAliveKicksWithATimedOutDisconnectPacket() throws Exception {
        TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort());
        try {
            client.silenceKeepAlives(); // the silence arm: never answer
            login(client, "Ghost");
            // Silence: never answer a keep-alive, never send anything. The
            // kick window is 3 × 300ms from the first unanswered send.
            long firstKeepAliveAt = System.currentTimeMillis();
            boolean disconnectSeen = false;
            String reason = null;
            client.setReadTimeout(10_000);
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline && !disconnectSeen) {
                byte[] frame;
                try {
                    frame = client.readPacket();
                } catch (IOException e) {
                    break; // the close after the Disconnect packet — handled below
                }
                if (client.readPacketId(frame) == Protocol18.S2C_DISCONNECT) {
                    io.netty.buffer.ByteBuf body = io.netty.buffer.Unpooled.wrappedBuffer(frame);
                    ByteBufOps.readVarInt(body); // packet id
                    reason = ByteBufOps.readString(body, 256);
                    disconnectSeen = true;
                }
            }
            long kickedAfterMs = System.currentTimeMillis() - firstKeepAliveAt;
            assertTrue(disconnectSeen, "the ghost saw a play Disconnect packet before the close");
            assertTrue(reason != null && reason.contains("Timed out"),
                    "the kick's reason is the timed-out form: " + reason);
            // The kick fires in the 3×interval window — not the 30s read-idle
            // bound (which would leave this assertion dead) and not instantly.
            assertTrue(kickedAfterMs >= KICK_WINDOW_MS,
                    "the kick waited the full 3x window: " + kickedAfterMs + "ms");
            assertTrue(kickedAfterMs < 10_000,
                    "the kick fired well inside the read-idle bound: " + kickedAfterMs + "ms");
            // After the Disconnect packet the server closes the wire: the next
            // read is EOF (or throws) — no further frames.
            client.setReadTimeout(5_000);
            boolean sawEof = false;
            try {
                while (true) {
                    byte[] frame = client.readPacket();
                    if (frame.length == 0) {
                        sawEof = true; // defensive; EOF usually throws instead
                        break;
                    }
                }
            } catch (IOException e) {
                sawEof = true; // EOF/reset after the server's close
            }
            assertTrue(sawEof, "the wire closed after the Disconnect packet");
            awaitCondition(() -> server.playerRegistry().byName("Ghost").isEmpty(),
                    "the ghost left the registry");
        } finally {
            client.close();
        }
        assertEquals(ServerState.RUNNING, server.state(), "the server survived the kick");
    }
}
