package net.zaminmc.torch.launcher.console;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The console adapter's lifecycle and integration contracts (§30.4.5/6/2):
 * lines typed through the interactive reader reach the ONE command path,
 * log records print above a partially typed line, EOF ends the console but
 * never the server, requestShutdown is idempotent and unblocks a pending
 * read, a broken engine route stays isolated, and the plain fallback rides
 * the same dispatcher.
 */
class TorchConsoleTest {

    /** The recording target: full lifecycle without booting the engine. */
    static class RecordingTarget implements ConsoleTarget {
        int stops;
        int saves;
        final List<String> commands = new java.util.concurrent.CopyOnWriteArrayList<>();
        final List<String> names = List.of("help", "say");

        @Override public void requestStop() {
            stops++;
        }

        @Override public void saveAllNow() {
            saves++;
        }

        @Override public String stateLine() {
            return "Server state: RUNNING, players=0";
        }

        @Override public void consoleCommand(String raw) {
            commands.add(raw);
        }

        @Override public List<String> commandNames() {
            return names;
        }
    }

    /** A console over a dumb terminal fed by a pipe, run on a helper thread. */
    private static final class Harness implements AutoCloseable {
        final RecordingTarget target = new RecordingTarget();
        final PipedOutputStream feed = new PipedOutputStream();
        final ByteArrayOutputStream screen = new ByteArrayOutputStream();
        final Thread thread;
        final TorchConsole console;
        volatile Throwable failure;

        Harness(ConsoleConfig config) throws Exception {
            PipedInputStream in = new PipedInputStream(feed);
            DumbTerminal terminal = new DumbTerminal(in, screen);
            console = new TorchConsole(config, target, terminal);
            thread = new Thread(() -> {
                try {
                    console.run();
                } catch (Throwable t) {
                    failure = t;
                }
            }, "test-console");
            thread.setDaemon(true);
            thread.start();
        }

        void type(String line) throws Exception {
            feed.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            feed.flush();
        }

        void awaitLine(String needle) throws Exception {
            long deadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < deadline
                    && target.commands.stream().noneMatch(c -> c.contains(needle))) {
                Thread.sleep(50);
            }
            assertTrue(target.commands.stream().anyMatch(c -> c.contains(needle)),
                    "the line reached the dispatcher: " + needle);
        }

        void awaitScreen(String needle) throws Exception {
            long deadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < deadline
                    && !screen.toString(StandardCharsets.UTF_8).contains(needle)) {
                Thread.sleep(50);
            }
            assertTrue(screen.toString(StandardCharsets.UTF_8).contains(needle),
                    "the terminal showed: " + needle);
        }

        void join() throws InterruptedException {
            thread.join(5_000);
            assertFalse(thread.isAlive(), "the console thread ends bounded");
        }

        @Override public void close() throws Exception {
            try {
                feed.close();
            } catch (Exception ignored) {
                // already closed
            }
            console.requestShutdown();
            join();
        }
    }

    private static ConsoleConfig interactiveConfig(Path history) {
        ConsoleConfig base = ConsoleConfig.defaults();
        return new ConsoleConfig(base.interactive(), base.ansi(), base.prompt(),
                true, history, 100, List.of(), true, base.terminalThreshold(),
                base.terminalQueueCapacity(), base.plainFallbackEnabled());
    }

    @TempDir
    Path dir;

    @Test
    void typedLinesReachTheOneCommandPath() throws Exception {
        try (Harness harness = new Harness(interactiveConfig(dir.resolve("h1")))) {
            harness.type("say hello console");
            harness.awaitLine("say hello console");
            harness.type("SAVED-ARGUMENT-Case");
            harness.awaitLine("SAVED-ARGUMENT-Case");
            assertEquals(0, harness.target.stops, "ordinary commands never stop the server");
        }
    }

    @Test
    void logRecordsPrintAboveAPartiallyTypedLineAndTheLineSurvives() throws Exception {
        try (Harness harness = new Harness(interactiveConfig(dir.resolve("h2")))) {
            // The console's own ready line rides the bridge: its arrival on
            // the screen PROVES the bridge is attached before we continue.
            harness.awaitScreen("Console ready");
            // "say " typed but NOT submitted; a log record lands mid-edit; the
            // line then completes and must reach the dispatcher intact.
            harness.feed.write("say ".getBytes(StandardCharsets.UTF_8));
            harness.feed.flush();
            Thread.sleep(150); // the dumb terminal's echo settles first
            Logger.getLogger("torch.midline").info("midline log burst");
            harness.awaitScreen("midline log burst");
            harness.feed.write("still-typing\n".getBytes(StandardCharsets.UTF_8));
            harness.feed.flush();
            harness.awaitLine("still-typing");
        }
    }

    @Test
    void eofEndsTheConsoleButNeverTheServer() throws Exception {
        RecordingTarget target = new RecordingTarget();
        DumbTerminal terminal = new DumbTerminal(
                new ByteArrayInputStream("state\n".getBytes(StandardCharsets.UTF_8)),
                new ByteArrayOutputStream());
        TorchConsole console = new TorchConsole(interactiveConfig(dir.resolve("h3")),
                target, terminal);
        console.run(); // EOF after one line: returns on its own
        assertEquals(0, target.stops, "EOF is not a stop");
        assertEquals(0, target.commands.size(), "state is a built-in, not an engine command");
    }

    @Test
    void stopCommandRequestsTheShutdownAndEndsTheLoop() throws Exception {
        try (Harness harness = new Harness(interactiveConfig(dir.resolve("h4")))) {
            harness.type("stop");
            harness.join();
            assertEquals(1, harness.target.stops, "stop converges on the idempotent route");
            assertTrue(harness.console.isShutdownRequested());
        }
    }

    @Test
    void requestShutdownIsIdempotentAndUnblocksAPendingRead() throws Exception {
        try (Harness harness = new Harness(interactiveConfig(dir.resolve("h5")))) {
            Thread.sleep(200); // the loop is parked in readLine
            harness.console.requestShutdown();
            harness.console.requestShutdown(); // idempotent
            harness.join();
            assertEquals(0, harness.target.stops,
                    "console shutdown alone never stops the server");
        }
    }

    @Test
    void aBrokenEngineRouteNeverKillsTheReadingLoop() throws Exception {
        RecordingTarget broken = new RecordingTarget() {
            @Override public void consoleCommand(String raw) {
                super.consoleCommand(raw); // record the attempt...
                throw new RuntimeException("engine route wedged"); // ...then fail
            }
        };
        PipedOutputStream feed = new PipedOutputStream();
        DumbTerminal terminal = new DumbTerminal(new PipedInputStream(feed),
                new ByteArrayOutputStream());
        TorchConsole console = new TorchConsole(interactiveConfig(dir.resolve("h6")),
                broken, terminal);
        Thread thread = new Thread(console::run, "test-console-broken");
        thread.setDaemon(true);
        thread.start();
        feed.write("first\n".getBytes(StandardCharsets.UTF_8));
        feed.flush();
        feed.write("second\n".getBytes(StandardCharsets.UTF_8));
        feed.flush();
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && broken.commands.size() < 2) {
            Thread.sleep(50);
        }
        assertEquals(2, broken.commands.size(), "the loop survives a throwing route");
        feed.close();
        console.requestShutdown();
        thread.join(5_000);
        assertFalse(thread.isAlive());
    }

    @Test
    void plainFallbackRidesTheSameDispatcherOverSystemIn() throws Exception {
        InputStream original = System.in;
        try {
            PipedOutputStream feed = new PipedOutputStream();
            PipedInputStream in = new PipedInputStream(feed);
            System.setIn(in);
            // Force the plain path: interactive OFF (the injected terminal is
            // only consulted when interactive is not OFF).
            ConsoleConfig off = ConsoleConfig.apply(ConsoleConfig.defaults(),
                    props("console.interactive", "off"));
            RecordingTarget target = new RecordingTarget();
            TorchConsole console = new TorchConsole(off, target);
            Thread thread = new Thread(console::run, "test-console-plain");
            thread.setDaemon(true);
            thread.start();
            feed.write("say plain path\n".getBytes(StandardCharsets.UTF_8));
            feed.flush();
            long deadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < deadline && target.commands.isEmpty()) {
                Thread.sleep(50);
            }
            assertEquals(List.of("say plain path"), target.commands);
            feed.write("stop\n".getBytes(StandardCharsets.UTF_8));
            feed.flush();
            thread.join(5_000);
            assertFalse(thread.isAlive(), "stop ends the plain loop");
            assertEquals(1, target.stops);
        } finally {
            System.setIn(original);
        }
    }

    private static java.util.Properties props(String... pairs) {
        java.util.Properties properties = new java.util.Properties();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            properties.setProperty(pairs[i], pairs[i + 1]);
        }
        return properties;
    }
}
