package net.zaminmc.torch.launcher.console;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The terminal sink's policy (§30.4.7): the threshold hides from the screen
 * only, the bounded queue drops-and-counts loudly, formatting survives with
 * color rules, and a slow output never blocks the logging thread.
 */
class TerminalLogBridgeTest {

    private static final class RecordingOutput implements TerminalLogBridge.Output {
        final List<String> lines = new ArrayList<>();
        volatile long blockNanos;

        @Override
        public synchronized void printAbove(String line) {
            if (blockNanos > 0) {
                try {
                    Thread.sleep(blockNanos / 1_000_000, (int) (blockNanos % 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lines.add(line);
        }
    }

    private static ConsoleConfig config(Level threshold, int queue) {
        ConsoleConfig base = ConsoleConfig.defaults();
        return new ConsoleConfig(base.interactive(), base.ansi(), base.prompt(),
                base.historyEnabled(), base.historyFile(), base.historyLimit(),
                base.sensitiveCommands(), base.completionEnabled(), threshold, queue,
                base.plainFallbackEnabled());
    }

    private static LogRecord record(Level level, String message) {
        LogRecord record = new LogRecord(level, message);
        record.setLoggerName("torch.test");
        record.setMillis(System.currentTimeMillis());
        return record;
    }

    @Test
    void recordsBelowTheTerminalThresholdNeverReachTheScreen() throws Exception {
        RecordingOutput output = new RecordingOutput();
        try (TerminalLogBridge bridge = new TerminalLogBridge(config(Level.WARNING, 32),
                output, false)) {
            bridge.start();
            bridge.publish(record(Level.INFO, "kept in the file, hidden on screen"));
            bridge.publish(record(Level.WARNING, "visible"));
            bridge.publish(record(Level.SEVERE, "visible too"));
        }
        assertEquals(2, output.lines.size(), "only WARNING+ reached the screen");
        assertTrue(output.lines.stream().noneMatch(l -> l.contains("hidden")));
        assertTrue(output.lines.get(0).contains("visible"));
    }

    @Test
    void ansiOnTranslatesLegacyCodesAndAnsiOffStrips() {
        RecordingOutput color = new RecordingOutput();
        RecordingOutput plain = new RecordingOutput();
        try (TerminalLogBridge on = new TerminalLogBridge(config(Level.ALL, 32), color, true);
             TerminalLogBridge off = new TerminalLogBridge(config(Level.ALL, 32), plain, false)) {
            on.start();
            off.start();
            on.publish(record(Level.INFO, "§cx§r plain"));
            off.publish(record(Level.INFO, "§cx§r plain"));
        }
        assertTrue(color.lines.get(0).contains("\u001b[91m"),
                "color terminal translates (§c is LIGHT red, SGR 91)");
        assertTrue(color.lines.get(0).endsWith("\u001b[0m"), "the trailing reset stops the bleed");
        assertFalse(plain.lines.get(0).contains("§"), "plain terminal strips");
        assertFalse(plain.lines.get(0).contains("\u001b"), "plain terminal emits no ANSI");
        assertTrue(plain.lines.get(0).contains("x plain"), "the text itself survives");
    }

    @Test
    void throwableDetailsSurvive() {
        RecordingOutput output = new RecordingOutput();
        try (TerminalLogBridge bridge = new TerminalLogBridge(config(Level.ALL, 32),
                output, false)) {
            bridge.start();
            LogRecord record = record(Level.SEVERE, "boom");
            record.setThrown(new IllegalStateException("the cause chain"));
            bridge.publish(record);
        }
        String line = output.lines.get(0);
        assertTrue(line.contains("boom"));
        assertTrue(line.contains("IllegalStateException"), "the exception class survives");
        assertTrue(line.contains("the cause chain"), "the message survives");
    }

    @Test
    void aFullQueueDropsCountsAndNeverBlocksTheLoggingThread() throws Exception {
        RecordingOutput slow = new RecordingOutput();
        slow.blockNanos = 50_000_000L; // 50 ms per line: a very slow terminal
        TerminalLogBridge bridge = new TerminalLogBridge(config(Level.ALL, 4), slow, false);
        bridge.start();
        long start = System.nanoTime();
        for (int i = 0; i < 50; i++) {
            bridge.publish(record(Level.INFO, "line " + i)); // must not block
        }
        long publishNanos = System.nanoTime() - start;
        // The property is NEVER-BLOCKING, not zero descheduling: publish is
        // format + offer (no wait anywhere), and the failure mode this seat
        // pins — a publish that waits on the sink's 50 ms/line drain — costs
        // the loop ~2.3 s (46 unabsorbed lines x 50 ms). A budget well under
        // half that failure mode discriminates cleanly while a loaded
        // runner's thread descheduling (run 571's three-fork contention, the
        // cold-compile heat of a first run) stays far under it; the old
        // 20 ms wall measured the scheduler, not the bridge.
        assertTrue(publishNanos < 1_000_000_000L,
                "50 publishes against a slow 4-slot queue return in well under the"
                        + " ~2.3 s a blocking publish would cost — the logging thread"
                        + " never waits on the sink");
        assertTrue(bridge.droppedCount() > 0, "the overflow was counted loudly");
        bridge.close();
        // Everything that made it into the queue is delivered in publish order
        // (numeric, not lexicographic: "line 9" precedes "line 10").
        java.util.List<Integer> deliveredIndexes = new ArrayList<>();
        for (String line : slow.lines) {
            deliveredIndexes.add(Integer.parseInt(
                    line.substring(line.lastIndexOf(' ') + 1).trim()));
        }
        for (int i = 1; i < deliveredIndexes.size(); i++) {
            assertTrue(deliveredIndexes.get(i) > deliveredIndexes.get(i - 1),
                    "delivery order is preserved");
        }
    }

    @Test
    void escBytesInsideMessagesNeverReachTheTerminal() {
        RecordingOutput output = new RecordingOutput();
        try (TerminalLogBridge bridge = new TerminalLogBridge(config(Level.ALL, 32),
                output, true)) {
            bridge.start();
            bridge.publish(record(Level.INFO, "chat\u001b[2Jwipe\u001b]0;x\u0007end"));
        }
        String line = output.lines.get(0);
        assertFalse(line.contains("\u001b[2J"), "the injected clear-screen dies");
        assertFalse(line.contains("\u001b]0;"), "the injected title change dies");
        assertTrue(line.contains("chat") && line.contains("wipe") && line.contains("end"));
    }
}
