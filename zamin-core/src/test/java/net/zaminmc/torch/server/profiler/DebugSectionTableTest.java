package net.zaminmc.torch.server.profiler;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ML-006 — the /debug stop section table (B-7's ask): the stop arm now
 * prints the reference-shaped per-section table to the console (name,
 * count, avg/visit, total, % of root, sorted by total descending), built
 * from frozen UNDECAYED snapshots (taken before the file report's decay
 * walk), beside the reference-1:1 file report. The pure builder is pinned
 * deterministically (exact counts, magnitude-separated ordering); the
 * live /debug cycle is pinned through the console capture — the header,
 * the column row, the engine's own section names, and the root count
 * against the tick span (equal, or one less on a mid-flight tick).
 */
class DebugSectionTableTest {

    @TempDir
    Path dataDir;

    net.zaminmc.torch.server.EngineServer server;
    PlayerSession alice;

    /** The console capture: collects every record the engine's logger
     * publishes while attached. */
    private final List<String> console = new ArrayList<>();
    private Handler capture;
    private Logger engineLogger;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "dsect" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        server = new net.zaminmc.torch.server.EngineServer(config);
        server.start();
        var accepted = (net.zaminmc.torch.server.net.EngineBridge.Accepted)
                server.joinRequest(new SilentLink(), "Alice",
                        UUID.nameUUIDFromBytes("OfflinePlayer:Alice".getBytes()));
        alice = accepted.session();
        server.ticker().submit(alice::markPlaying);
        await(() -> alice.state() == net.zaminmc.torch.entity.PlayerState.PLAYING, "playing");
        server.ticker().submit(() -> alice.setOpLevel(3));
        await(() -> alice.opLevel() >= 3, "Alice opped");
        engineLogger = Logger.getLogger(net.zaminmc.torch.server.EngineServer.class.getName());
        capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                // The stop command publishes from its own thread while the
                // test reads — both sides hold the same monitor.
                synchronized (console) {
                    console.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        engineLogger.addHandler(capture);
    }

    @AfterEach
    void stop() {
        if (engineLogger != null && capture != null) {
            engineLogger.removeHandler(capture);
        }
        server.shutdown(null);
    }

    private static final class SilentLink implements net.zaminmc.torch.server.net.ClientLink {
        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }
    }

    private void await(java.util.function.BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    /** The pure builder over a magnitude-separated walk: root nests all
     * three children, so its total dominates; heavy > mid > light by 20x
     * and 5x per-visit margins, so the descending order is deterministic
     * even under CI scheduling jitter. The count column is exact — the
     * wall times are not, only their ordering and parseability are pinned. */
    @Test
    void theTableCarriesTheReferenceShapeAndTheCountColumns() throws Exception {
        EngineProfiler profiler = new EngineProfiler();
        profiler.start();
        for (int i = 0; i < 3; i++) {
            profiler.push("root");
            profiler.push("heavy");   // ~20 ms per visit
            Thread.sleep(20);
            profiler.pop();
            profiler.push("mid");     // ~5 ms per visit
            Thread.sleep(5);
            profiler.pop();
            profiler.push("light");   // ~1 ms per visit
            Thread.sleep(1);
            profiler.pop();
            profiler.pop();
        }
        java.util.Map<String, Long> nanos = profiler.sectionNanosSnapshot();
        java.util.Map<String, Long> counts = profiler.sectionCountsSnapshot();
        assertEquals(nanos.keySet(), counts.keySet(),
                "every time row carries its count row");
        String table = EngineProfiler.buildSectionTable(2000, 40, nanos, counts);

        assertTrue(table.startsWith("---- Section table (name, count, avg, total) ----"),
                "the table's own header");
        assertTrue(table.contains("Time span: 2000 ms"), "the reference's span line");
        assertTrue(table.contains("Tick span: 40 ticks"), "the reference's tick line");
        assertTrue(table.contains("// This is approximately 20.00 ticks per second. It should be 20 ticks per second"),
                "the reference's ticks-per-second line");
        assertTrue(table.contains(String.format("%-48s %8s %12s %12s %10s",
                "Section", "count", "avg/visit", "total", "% of root")),
                "the column header row");

        // The rows, total-descending: root nests everything (dominates),
        // then heavy (60 ms) > mid (15 ms) > light (3 ms).
        Pattern row = Pattern.compile("^(\\S+)\\s+(\\d+)\\s+([0-9.-]+ ms|-)\\s+([0-9.]+ ms)\\s+([0-9.]+%|-)$");
        List<String[]> rows = new ArrayList<>();
        for (String line : table.split("\n")) {
            Matcher m = row.matcher(line.trim());
            if (m.matches()) {
                rows.add(new String[]{m.group(1), m.group(2), m.group(3), m.group(4), m.group(5)});
            }
        }
        assertEquals(4, rows.size(), "one row per section: " + rows);
        assertEquals("root", rows.get(0)[0]);
        assertEquals("root.heavy", rows.get(1)[0]);
        assertEquals("root.mid", rows.get(2)[0]);
        assertEquals("root.light", rows.get(3)[0]);
        // The count column is exact: three visits each.
        for (String[] r : rows) {
            assertEquals("3", r[1], "the visit count for " + r[0]);
        }
        // avg/visit = total/count; the % column against the root total.
        for (String[] r : rows) {
            double totalMs = Double.parseDouble(r[3].replace(" ms", ""));
            double avgMs = Double.parseDouble(r[2].replace(" ms", ""));
            assertEquals(avgMs, totalMs / 3, 0.02, "avg/visit for " + r[0]);
        }
        double rootTotalMs = Double.parseDouble(rows.get(0)[3].replace(" ms", ""));
        assertEquals(100.0, Double.parseDouble(rows.get(0)[4].replace("%", "")), 0.01,
                "root is 100% of itself");
        double heavyShare = Double.parseDouble(rows.get(1)[4].replace("%", ""));
        double heavyTotalMs = Double.parseDouble(rows.get(1)[3].replace(" ms", ""));
        assertEquals(heavyShare, heavyTotalMs * 100 / rootTotalMs, 0.05,
                "the share column matches total/root");
        profiler.stop();
    }

    /** A section without a count row (a defensive union) never divides:
     * avg prints "-". */
    @Test
    void theZeroCountRowPrintsADashForAvg() {
        String table = EngineProfiler.buildSectionTable(1000, 20,
                java.util.Map.of("root.lone", 5_000_000L),
                java.util.Map.of());
        assertTrue(table.contains("root.lone"), "the row kept");
        Pattern lone = Pattern.compile("^root\\.lone\\s+0\\s+-\\s+5\\.00 ms\\s+-$", java.util.regex.Pattern.MULTILINE);
        assertTrue(lone.matcher(table).find(), "count 0, avg -, no share (no root total)");
    }

    /** The live /debug cycle prints the table to the console: the header,
     * the engine's own section names, and the root count against the tick
     * span (equal, or one less when the stop lands mid-tick). */
    @Test
    void theStopPrintsTheTableToTheConsole() throws Exception {
        server.chatService().submitChat(alice, "/debug start");
        long startTicks = server.world().totalTicks();
        await(() -> server.world().totalTicks() >= startTicks + 5, "some profiled ticks");
        server.chatService().submitChat(alice, "/debug stop");
        AtomicBoolean seen = new AtomicBoolean();
        await(() -> {
            synchronized (console) {
                for (String message : console) {
                    if (message != null && message.startsWith("---- Section table")) {
                        seen.set(true);
                        return true;
                    }
                }
            }
            return false;
        }, "the console table");
        assertTrue(seen.get(), "the table reached the console");

        String table = null;
        synchronized (console) {
            for (String message : console) {
                if (message != null && message.startsWith("---- Section table")) {
                    table = message;
                    break;
                }
            }
        }
        assertNotNull(table, "the table record");
        assertTrue(table.contains("Time span:"), "the span line");
        Matcher tickSpan = Pattern.compile("Tick span: (\\d+) ticks").matcher(table);
        assertTrue(tickSpan.find(), "the tick span line");
        int span = Integer.parseInt(tickSpan.group(1));
        assertTrue(span >= 5, "the span covers the awaited ticks: " + span);
        assertTrue(table.contains(String.format("%-48s %8s %12s %12s %10s",
                "Section", "count", "avg/visit", "total", "% of root")),
                "the column header row");
        // The engine's own walk: the root row first (dominates by nesting),
        // the levels.<world>.tick family present.
        Matcher rootRow = Pattern.compile("^root\\s+(\\d+)\\s+", Pattern.MULTILINE).matcher(table);
        assertTrue(rootRow.find(), "the root row");
        int rootCount = Integer.parseInt(rootRow.group(1));
        assertTrue(rootCount == span || rootCount == span - 1,
                "the root visits match the tick span (or one less on a mid-flight tick): "
                        + rootCount + " vs " + span);
        assertTrue(Pattern.compile("^root\\.levels\\.\\S+\\.tick\\s+\\d+", Pattern.MULTILINE).matcher(table).find(),
                "the world's tick section row");
        assertTrue(table.contains("% of root") || table.contains("root"),
                "the share column present");
    }
}
