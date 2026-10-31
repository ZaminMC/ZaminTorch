package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ML-014 — the login-flood boundary: what a flood of concurrent in-progress
 * logins costs the tick lane and memory, measured; and the reference's own
 * reap bound, pinned.
 *
 * <p><b>The reference's own queueing</b> (reference/1.8.8): the accept path
 * is UNBOUNDED — no connection cap, no per-IP limit (ConnectionListener's
 * channels/connections lists grow freely) — and the bound is carried by two
 * thirty-second mechanisms: {@code ReadTimeoutHandler(30)} first in every
 * child pipeline (ConnectionListener.java line 104) and the login handler's
 * 600-tick "Took too long to log in" deadline (ServerLoginNetworkHandler.
 * java lines 65-67). The join acceptance itself is serialized onto the
 * server thread through the per-tick connection walk (MinecraftServer.java
 * lines 589-590, the profiler's own "connection" section).</p>
 *
 * <p><b>Torch's shape</b>: the login state machine lives on the channel's
 * event loop (offline mode's single-packet login completes in one hop, so
 * the reference's server-thread deferral has no window to serve), the only
 * numeric bound is {@code maxPlayers} on REGISTERED sessions, and — before
 * this row — NOTHING bounded the in-progress population: a handshake-only
 * connection was immortal and invisible to the engine. THE FIX this row
 * landed is the reference's own first mechanism: {@code ReadTimeoutHandler}
 * at the reference's own value (30s default), first in the pipeline, reaping
 * silent connections cleanly and QUIETLY (a reaped flood must not spray
 * WARNING stacks). The measurement below then answers the cost question the
 * row asks: the in-progress flood costs the tick lane NOTHING (the engine
 * never sees them — ticks keep the 20 TPS rhythm through the window) and
 * memory linear in N (both harness ends counted); the completing-login
 * flood's join window and per-tick fan carry the real per-player costs.</p>
 *
 * <p><b>The DenseOwner doctrine</b> (DenseOwnerProfilingTest): the
 * assertions are structural — the tick lane never stalls, the flood never
 * becomes players, the per-player section fan grows with the population —
 * and the NUMBERS are the printed evidence for the record.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoginFloodBoundaryTest {

    @org.junit.jupiter.api.io.TempDir
    static Path dataDir;

    /** Awaits an engine condition with a generous deadline (flood windows). */
    private static void await(BooleanSupplier condition, String description)
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

    private static long heapUsed() {
        for (int i = 0; i < 2; i++) {
            System.gc();
        }
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    /** One boot of the measured stack: engine + adapter on an ephemeral port. */
    private EngineServer bootEngine(V18ProtocolServer[] adapterOut, int readTimeoutSeconds)
            throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "flood"
                + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "login flood", 20, 4, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        V18ProtocolServer adapter = new V18ProtocolServer(server, 300_000, readTimeoutSeconds);
        adapter.start(server);
        adapterOut[0] = adapter;
        return server;
    }

    /** One sampling window: the profiler snapshot + the tick anchor. */
    private record Window(Map<String, Long> nanos, long ticks) {
    }

    private static Window sample(EngineServer server) {
        return new Window(server.profilerSectionNanos(), server.world().totalTicks());
    }

    private static Window waitForTicks(EngineServer server, int ticks, String description)
            throws InterruptedException {
        long anchor = server.world().totalTicks();
        await(() -> server.world().totalTicks() >= anchor + ticks, description);
        return sample(server);
    }

    /** The window's per-tick nanos for every tick-section (the DenseOwner face). */
    private static Map<String, Double> perTickDeltas(EngineServer server,
                                                     Window before, Window after) {
        String tickPrefix = "root.levels." + server.config().worldName() + ".tick.";
        long ticks = Math.max(1, after.ticks() - before.ticks());
        Map<String, Double> deltas = new LinkedHashMap<>();
        for (Map.Entry<String, Long> row : after.nanos().entrySet()) {
            String key = row.getKey();
            if (!key.startsWith(tickPrefix)) {
                continue;
            }
            long delta = row.getValue() - before.nanos().getOrDefault(key, 0L);
            if (delta > 0) {
                deltas.put(key.substring(tickPrefix.length()), delta / (double) ticks);
            }
        }
        return deltas;
    }

    /** The evidence table: the window's per-tick costs, top rows first. */
    private static void printEvidence(String title, Map<String, Double> deltas) {
        List<Map.Entry<String, Double>> rows = new ArrayList<>(deltas.entrySet());
        rows.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        System.out.println("=== ML-014 LOGIN-FLOOD EVIDENCE: " + title + " ===");
        double total = rows.stream()
                .filter(e -> !"tick".equals(e.getKey()))
                .mapToDouble(Map.Entry::getValue).sum();
        System.out.printf("tick-section total: %.1f us/tick%n", total / 1000.0);
        int shown = 0;
        for (Map.Entry<String, Double> row : rows) {
            if ("tick".equals(row.getKey()) || shown >= 10) {
                continue;
            }
            System.out.printf("  %-22s %10.1f us/tick  (%5.1f%%)%n",
                    row.getKey(), row.getValue() / 1000.0,
                    total > 0 ? row.getValue() * 100.0 / total : 0.0);
            shown++;
        }
    }

    private static double perTick(Map<String, Double> deltas, String section) {
        return deltas.getOrDefault(section, 0.0);
    }

    /**
     * The measurement: N in-progress logins (handshake sent, login never
     * completed — the immortal population before the bound) against a
     * running engine. THE ANSWER: the tick lane never sees them — forty
     * ticks keep the nominal 20 TPS rhythm through the window, the section
     * fan carries no flood cost, and the players registry never grows;
     * memory is linear in N (both harness ends counted, in-process).
     */
    @Test
    void theFloodOfInProgressLoginsCostsTheTickLaneNothing() throws Exception {
        V18ProtocolServer[] adapterOut = new V18ProtocolServer[1];
        EngineServer server = bootEngine(adapterOut, V18ProtocolServer.DEFAULT_READ_TIMEOUT_SECONDS);
        V18ProtocolServer adapter = adapterOut[0];
        try {
            // The measuring player, opped for the /debug face.
            try (TestClient18 op = new TestClient18("127.0.0.1", adapter.boundPort())) {
                op.sendHandshake(47, 2);
                op.sendLoginStart("FloodOp");
                op.readLoginSuccess();
                op.readUntilPositionAndLook();
                await(() -> server.playerRegistry().byName("FloodOp").isPresent(), "the op joined");
                var opSession = server.playerRegistry().byName("FloodOp").orElseThrow();
                server.ticker().submit(() -> opSession.setOpLevel(3));
                await(() -> opSession.opLevel() >= 3, "the op opped");
                server.chatService().submitChat(opSession, "/debug start");

                Window opened = waitForTicks(server, 2, "the profiler opened");
                Window baselineEnd = waitForTicks(server, 40, "the baseline window");
                Map<String, Double> baseline = perTickDeltas(server, opened, baselineEnd);

                // THE FLOOD: one hundred in-progress logins, silent.
                final int flood = 100;
                long heapBefore = heapUsed();
                List<TestClient18> clients = new ArrayList<>(flood);
                for (int i = 0; i < flood; i++) {
                    TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort());
                    clients.add(client);
                    client.sendHandshake(47, Protocol18.NEXT_STATE_LOGIN);
                }
                await(() -> adapter.connections().size() >= flood, "the flood landed");
                long heapAfter = heapUsed();

                // The tick lane through the flood window: forty ticks at the
                // nominal rhythm — a stalled lane would miss the deadline.
                long wallStart = System.nanoTime();
                Window floodEnd = waitForTicks(server, 40, "the flood window");
                double elapsedSec = (System.nanoTime() - wallStart) / 1e9;
                Map<String, Double> during = perTickDeltas(server, baselineEnd, floodEnd);

                // THE MEASUREMENT RECORD (the deliverable's numbers).
                System.out.println("=== ML-014 LOGIN-FLOOD EVIDENCE: "
                        + flood + " in-progress logins ===");
                System.out.printf("tick lane: 40 ticks in %.2fs (nominal 2.00s at 20 TPS)%n",
                        elapsedSec);
                System.out.printf("tick-rate hold: %.1f ticks/sec through the flood%n",
                        40.0 / elapsedSec);
                System.out.printf("memory: %.1f KiB per in-progress connection "
                                + "(both harness ends, in-process)%n",
                        (heapAfter - heapBefore) / 1024.0 / flood);
                System.out.printf("connection table: %d live (the op's own plus the flood)%n",
                        adapter.connections().size());
                printEvidence("baseline (one player)", baseline);
                printEvidence("through the flood (one player, hundred pending)", during);

                // The structural pins: no stall, no engine cost, no players.
                // (The section tables above are the PRINTED evidence — the
                // DenseOwner doctrine: the flood's tick-lane cost reads as
                // the unchanged per-tick fan, one player's worth in both
                // windows; a stalled lane would fail the elapsed pin.)
                assertTrue(elapsedSec < 3.0,
                        "the tick lane kept the nominal rhythm: 40 ticks in " + elapsedSec + "s");
                assertEquals(1, server.players().size(),
                        "the in-progress flood never became players");
                assertEquals(flood + 1, adapter.connections().size(),
                        "the flood plus the measuring op is the whole connection table");

                for (TestClient18 client : clients) {
                    client.close();
                }
                await(() -> adapter.connections().size() <= 1,
                        "the flood closed (the op's own connection remains)");
            }
        } finally {
            server.shutdown(adapter::shutdown);
        }
    }

    /**
     * The completing-login flood: twelve concurrent full logins against the
     * running engine — the join window's wall time (the event-loop join
     * bursts, serialized per channel), the per-tick fan that thirteen
     * players carry, and the memory per joined session.
     */
    @Test
    void twelveConcurrentFullLoginsMeasureTheJoinWindowAndThePerTickFan() throws Exception {
        V18ProtocolServer[] adapterOut = new V18ProtocolServer[1];
        EngineServer server = bootEngine(adapterOut, V18ProtocolServer.DEFAULT_READ_TIMEOUT_SECONDS);
        V18ProtocolServer adapter = adapterOut[0];
        try {
            // The measuring player, opped for the /debug face.
            try (TestClient18 op = new TestClient18("127.0.0.1", adapter.boundPort())) {
                op.sendHandshake(47, 2);
                op.sendLoginStart("FloodOp");
                op.readLoginSuccess();
                op.readUntilPositionAndLook();
                await(() -> server.playerRegistry().byName("FloodOp").isPresent(), "the op joined");
                var opSession = server.playerRegistry().byName("FloodOp").orElseThrow();
                server.ticker().submit(() -> opSession.setOpLevel(3));
                await(() -> opSession.opLevel() >= 3, "the op opped");
                server.chatService().submitChat(opSession, "/debug start");
                Window opened = waitForTicks(server, 2, "the profiler opened");
                // The one-player baseline is a NOISE FLOOR, not a single
                // sample: three consecutive windows, the quietest kept per
                // section. A co-scheduled test fork's heavy phase can
                // inflate any ONE window (run 558's own evidence: this
                // class measured 39.3 and 98.3 us/tick one-player
                // baselines across its two seats in the same run), and
                // the structural pins below compare the thirteen-player
                // cost against the one-player HONEST cost.
                Map<String, Double> baseline = null;
                Window baselineEnd = opened;
                for (int i = 0; i < 3; i++) {
                    Window end = waitForTicks(server, 40,
                            "the baseline window " + (i + 1) + " of 3");
                    Map<String, Double> sample = perTickDeltas(server, baselineEnd, end);
                    if (baseline == null) {
                        baseline = sample;
                    } else {
                        for (Map.Entry<String, Double> row : sample.entrySet()) {
                            Double held = baseline.get(row.getKey());
                            if (held == null || row.getValue() < held) {
                                baseline.put(row.getKey(), row.getValue());
                            }
                        }
                    }
                    baselineEnd = end;
                }

                // THE FLOOD: twelve concurrent full logins.
                final int flood = 12;
                long heapBefore = heapUsed();
                long wallStart = System.nanoTime();
                List<TestClient18> clients = new ArrayList<>(flood);
                try {
                    ExecutorService pool = Executors.newFixedThreadPool(flood);
                    try {
                        List<Future<TestClient18>> joins = new ArrayList<>(flood);
                        for (int i = 0; i < flood; i++) {
                            String name = "Flooder" + (i < 10 ? "0" : "") + i;
                            final String joinName = name;
                            joins.add(pool.submit(() -> joinFully(adapter, joinName)));
                        }
                        for (Future<TestClient18> join : joins) {
                            clients.add(join.get(60, TimeUnit.SECONDS));
                        }
                    } finally {
                        pool.shutdownNow();
                    }
                } catch (java.util.concurrent.ExecutionException e) {
                    throw new AssertionError("a concurrent login failed: " + e.getCause(), e);
                }
                double joinWindowSec = (System.nanoTime() - wallStart) / 1e9;
                long heapAfter = heapUsed();
                await(() -> server.players().size() >= 1 + flood, "all flooders registered");

                // The steady per-tick fan with the full population.
                Window floodEnd = waitForTicks(server, 40, "the populated window");
                Map<String, Double> during = perTickDeltas(server, baselineEnd, floodEnd);

                System.out.println("=== ML-014 LOGIN-FLOOD EVIDENCE: "
                        + flood + " concurrent full logins ===");
                System.out.printf("join window: %d full logins in %.2fs (%.0f ms per login, "
                                + "the serialized event-loop join bursts)%n",
                        flood, joinWindowSec, joinWindowSec * 1000.0 / flood);
                System.out.printf("memory: %.1f KiB per joined session (both harness ends)%n",
                        (heapAfter - heapBefore) / 1024.0 / flood);
                printEvidence("baseline (one player)", baseline);
                printEvidence("populated (thirteen players)", during);

                // The structural pins: the per-player fan grows with the
                // population (the tick-lane cost a completing flood leaves
                // behind), and every login landed.
                assertEquals(1 + flood, server.players().size(), "the whole flood joined");
                assertTrue(perTick(during, "playerBodies") > perTick(baseline, "playerBodies"),
                        "thirteen players carry a heavier playerBodies fan than one");
                assertTrue(perTick(during, "inhabitedTime") > perTick(baseline, "inhabitedTime"),
                        "the inhabited-time walk grows with the population too");

                for (TestClient18 client : clients) {
                    client.close();
                }
                await(() -> server.players().size() <= 1, "the flooders left (the op remains)");
            }
        } finally {
            server.shutdown(adapter::shutdown);
        }
    }

    /** One full login from a worker thread. */
    private static TestClient18 joinFully(V18ProtocolServer adapter, String name) throws Exception {
        TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort());
        client.sendHandshake(47, 2);
        client.sendLoginStart(name);
        client.readLoginSuccess();
        client.readUntilPositionAndLook();
        return client;
    }

    /**
     * THE PIN of the bound: a flood of silent in-progress logins is reaped
     * by the read-idle timeout — the reference's own mechanism
     * (ConnectionListener.java line 104) at a test-shortened window — with
     * the flood never touching the players registry and the reaping itself
     * quiet (no WARNING stacks: the timeout is the mechanism working).
     */
    @Test
    void theReadTimeoutReapsTheSilentInProgressLoginsQuietly() throws Exception {
        assertEquals(30, V18ProtocolServer.DEFAULT_READ_TIMEOUT_SECONDS,
                "the default carries the reference's own thirty-second value");
        V18ProtocolServer[] adapterOut = new V18ProtocolServer[1];
        EngineServer server = bootEngine(adapterOut, 1); // one-second reap window
        V18ProtocolServer adapter = adapterOut[0];
        try {
            Logger connectionLogger = Logger.getLogger(V18Connection.class.getName());
            Logger serverLogger = Logger.getLogger(EngineServer.class.getName());
            LogCapture capture = new LogCapture();
            final List<TestClient18> clients = new ArrayList<>();
            for (Logger logger : new Logger[] {connectionLogger, serverLogger}) {
                logger.addHandler(capture);
                logger.setUseParentHandlers(false);
            }
            try {
                final int flood = 20;
                for (int i = 0; i < flood; i++) {
                    TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort());
                    clients.add(client);
                    client.sendHandshake(47, Protocol18.NEXT_STATE_LOGIN);
                }
                await(() -> adapter.connections().size() >= flood, "the flood landed");
                assertEquals(0, server.players().size(),
                        "the silent flood never became players");

                // The reap: every silent connection is closed by the timeout.
                await(() -> adapter.connections().isEmpty(), "the timeout reaped the flood");

                assertTrue(capture.records.stream().noneMatch(
                                record -> record.getLevel().intValue() >= Level.WARNING.intValue()),
                        () -> "the reaping stayed quiet, but logged: "
                                + capture.records.stream()
                                .map(r -> r.getLevel() + ": " + r.getMessage())
                                .toList());
                assertEquals(0, server.players().size(),
                        "the reaped flood left no players behind");
            } finally {
                for (Logger logger : new Logger[] {connectionLogger, serverLogger}) {
                    logger.removeHandler(capture);
                    logger.setUseParentHandlers(true);
                }
                for (TestClient18 client : clients) {
                    client.close();
                }
            }
        } finally {
            server.shutdown(adapter::shutdown);
        }
    }

    /** Collects every record the named loggers emit while attached. */
    private static final class LogCapture extends Handler {
        final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
