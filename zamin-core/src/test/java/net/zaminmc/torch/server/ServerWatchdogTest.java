package net.zaminmc.torch.server;

import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ML-013's watchdog family, over the reference's own ServerWatchdog shape
 * (net.minecraft.server.dedicated.ServerWatchdog, 1.8.8 reference lines
 * 17-90): the idle lane never trips (the pre-loop window), the healthy lane
 * never trips (the heartbeat refreshes every tick), a wedged tick trips the
 * whole stall walk — the two fatal lines, the crash report under
 * crash-reports/crash-...-server.txt with the tick thread's stack and the
 * thread dump, the force exit — the loop exits when the lane stops, and the
 * keep-up warn law is the reference's own (MinecraftServer lines 396-401:
 * >2000ms warns, at most once per 15000ms, the reference's own line).
 */
class ServerWatchdogTest {

    private static FrozenBlockRegistry registry;

    @BeforeAll
    static void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
    }

    private static EngineWorld world(String name, Thread owner) {
        return new EngineWorld(name, registry,
                new FlatWorldGenerator(registry, 4), owner);
    }

    @Test
    void theIdleLaneNeverTrips(@TempDir Path root) {
        // No tick has ever started: the heartbeat is 0 (the reference reads
        // the same window through getNextTickTime(); a loop that has not
        // started cannot be stalled).
        EngineTicker ticker = new EngineTicker(20);
        ticker.attachWorld(world("idle", Thread.currentThread()));
        AtomicInteger exits = new AtomicInteger();
        ServerWatchdog watchdog = new ServerWatchdog(ticker, 1, root.resolve("crash-reports"),
                exits::incrementAndGet);
        assertFalse(watchdog.tripIfStalled(), "the idle lane is not a stall");
        assertEquals(0, exits.get(), "no force exit");
        assertEquals(0, listCrashReports(root).size(), "no crash report");
    }

    @Test
    void theHealthyLaneNeverTrips(@TempDir Path root) throws Exception {
        // A real loop, real ticks, the handler finishing each: the
        // heartbeat refreshes every 50ms and the 60s default threshold (the
        // reference's own) never sees a stall.
        EngineTicker ticker = new EngineTicker(20);
        Thread loop = new Thread(ticker::runLoop, "healthy-loop");
        ticker.attachWorld(world("healthy", loop));
        CountDownLatch threeTicks = new CountDownLatch(3);
        ticker.setTickHandler(threeTicks::countDown);
        loop.start();
        assertTrue(threeTicks.await(5, TimeUnit.SECONDS), "the loop ticks");
        AtomicInteger exits = new AtomicInteger();
        ServerWatchdog watchdog = new ServerWatchdog(ticker,
                EngineConfig.DEFAULT_MAX_TICK_TIME_MILLIS,
                root.resolve("crash-reports"), exits::incrementAndGet);
        assertFalse(watchdog.tripIfStalled(), "a live lane is not a stall");
        assertEquals(0, exits.get(), "no force exit");
        assertEquals(0, listCrashReports(root).size(), "no crash report");
        ticker.stop();
        assertTrue(ticker.awaitStop(5000), "the loop stops");
        loop.join(5000);
    }

    @Test
    void theWedgedTickTripsTheWholeStallWalk(@TempDir Path root) throws Exception {
        // The reference's fatal arm (lines 33-66): a tick wedged past the
        // threshold dumps every thread, writes the crash report, and
        // force-exits — here through the capturing seam, in-process.
        EngineTicker ticker = new EngineTicker(20);
        Thread loop = new Thread(ticker::runLoop, "wedged-loop");
        ticker.attachWorld(world("wedged", loop));
        CountDownLatch release = new CountDownLatch(1);
        ticker.setTickHandler(() -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        loop.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (ticker.currentTickStartNanos() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(ticker.currentTickStartNanos() != 0, "the first tick started");
        // The in-flight tick is now wedged on the latch; let its age pass
        // the 200ms threshold.
        Thread.sleep(300);

        AtomicInteger exits = new AtomicInteger();
        ServerWatchdog watchdog = new ServerWatchdog(ticker, 200,
                root.resolve("crash-reports"), exits::incrementAndGet);
        assertTrue(watchdog.tripIfStalled(), "the wedged tick trips");

        assertEquals(1, exits.get(), "the force exit ran exactly once");
        List<Path> reports = listCrashReports(root);
        assertEquals(1, reports.size(), "one crash report written: " + reports);
        String report = Files.readString(reports.get(0));
        assertTrue(report.startsWith("---- ZaminTorch Crash Report ----"),
                "the reference's crash header shape: " + report.substring(0, 60));
        assertTrue(report.contains("// "), "the witty comment line rides the header");
        assertTrue(report.contains("Description: Watching Server"),
                "the reference's own description (ServerWatchdog line 52)");
        assertTrue(report.contains("Time: "), "the timestamp row");
        assertTrue(report.contains("Thread: zamin-tick"),
                "the wedged lane's own thread, named by the loop (runLoop)");
        assertTrue(report.contains("-- Thread Dump --"),
                "the reference's Thread Dump category (line 54)");
        assertTrue(report.contains("-- Torch --"), "the engine's own details section");
        assertTrue(report.contains("Max tick time: 200ms"), "the threshold row");
        assertTrue(report.contains("Tick interval: 50.00ms"),
                "the reference's own 0.05 seconds at the 20 TPS default");
        assertTrue(report.contains("Completed ticks: "), "the tick census row");
        assertTrue(report.contains("theWedgedTickTripsTheWholeStallWalk"),
                "the wedged tick's own frame — the stall is the handler's latch park");

        release.countDown();
        ticker.stop();
        assertTrue(ticker.awaitStop(5000), "the loop stops after the release");
        loop.join(5000);
    }

    @Test
    void theLoopExitsWhenTheLaneStops() throws Exception {
        // The reference's own loop condition (line 29: while isRunning()):
        // a lane that is not running ends the watchdog loop promptly.
        EngineTicker ticker = new EngineTicker(20);
        ticker.attachWorld(world("stopped", Thread.currentThread()));
        AtomicInteger exits = new AtomicInteger();
        ServerWatchdog watchdog = new ServerWatchdog(ticker, 60_000,
                Path.of("crash-reports-nowhere"), exits::incrementAndGet);
        Thread watcher = new Thread(watchdog, "watcher");
        watcher.start();
        watcher.join(5000);
        assertFalse(watcher.isAlive(), "the watchdog loop returned on the stopped lane");
        assertEquals(0, exits.get(), "no force exit on a clean exit");
    }

    @Test
    void theKeepUpLawIsTheReferenceOwn() {
        // The reference's decision, 1:1 (MinecraftServer lines 396-401):
        // j > 2000L && nextTickTime - lastWarnTime >= 15000L.
        EngineTicker ticker = new EngineTicker(20);
        ticker.attachWorld(world("keepup", Thread.currentThread()));
        long nextTick = System.nanoTime();
        assertFalse(ticker.shouldWarnKeepUp(TimeUnit.MILLISECONDS.toNanos(1999), nextTick),
                "a 1999ms lateness stays silent (the reference's > 2000L)");
        assertFalse(ticker.shouldWarnKeepUp(TimeUnit.MILLISECONDS.toNanos(500), nextTick),
                "a half-second lateness stays silent — the 2000ms band keeps the warmup norm quiet");
        assertTrue(ticker.shouldWarnKeepUp(TimeUnit.MILLISECONDS.toNanos(2001), nextTick),
                "the first past-two-ticks lateness warns (the epoch-0 anchor)");
        // The cadence: a second lateness inside the 15000ms window is
        // silent, whatever its size.
        assertFalse(ticker.shouldWarnKeepUp(TimeUnit.MILLISECONDS.toNanos(2500),
                        nextTick + TimeUnit.SECONDS.toNanos(5)),
                "the 15s cadence suppresses the repeat (>= 15000L not met)");
        // ... and one past the window warns again.
        assertTrue(ticker.shouldWarnKeepUp(TimeUnit.MILLISECONDS.toNanos(2100),
                        nextTick + TimeUnit.SECONDS.toNanos(16)),
                "a lateness past the 15s window warns again");
    }

    @Test
    void theKeepUpLineIsTheReferenceOwnLine() throws Exception {
        // The reference's message, verbatim shape (MinecraftServer lines
        // 397-399): "Can't keep up! Did the system time change, or is the
        // server overloaded? Running {}ms behind, skipping {} tick(s)" —
        // driven here through the real loop with one slow first tick; the
        // second tick's start lands >2s past its deadline and warns exactly
        // once (the 15s cadence holds every later lateness silent).
        EngineTicker ticker = new EngineTicker(20);
        Thread loop = new Thread(ticker::runLoop, "keepup-loop");
        ticker.attachWorld(world("keepup-line", loop));
        AtomicBoolean slowedOnce = new AtomicBoolean();
        ticker.setTickHandler(() -> {
            if (slowedOnce.compareAndSet(false, true)) {
                try {
                    Thread.sleep(2300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        Logger tickerLogger = Logger.getLogger(EngineTicker.class.getName());
        List<String> lines = new Vector<>();
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
        tickerLogger.addHandler(capture);
        try {
            loop.start();
            // The first tick takes 2.3s; the second tick starts ~2.25s late
            // and warns; a moment past the warn, the lane stops.
            Thread.sleep(3400);
            ticker.stop();
            assertTrue(ticker.awaitStop(5000), "the loop stops");
            loop.join(5000);
        } finally {
            tickerLogger.removeHandler(capture);
        }
        List<String> keepUp = lines.stream()
                .filter(line -> line.startsWith("Can't keep up!")).toList();
        assertEquals(1, keepUp.size(), "exactly one keep-up line: " + lines);
        String line = keepUp.get(0);
        assertTrue(line.startsWith("Can't keep up! Did the system time change, "
                        + "or is the server overloaded? Running "),
                "the reference's own line: " + line);
        assertTrue(line.matches(".*Running \\d+ms behind, skipping \\d+ tick\\(s\\)"),
                "the millis and tick-count tails ride the line: " + line);
    }

    private static List<Path> listCrashReports(Path root) {
        try (var stream = Files.list(root.resolve("crash-reports"))) {
            return stream.filter(p -> p.getFileName().toString().startsWith("crash-")).toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
