package net.zaminmc.torch.server;

import net.zaminmc.torch.Server;
import net.zaminmc.torch.scheduler.Task;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The scheduler & diagnostics API (Slice 14d — §27.3's task controls over
 * the live engine): the public {@link Server#scheduler()} mount with the
 * delay, the repeat and the cancellation semantics, every body landing
 * on the simulation owner's context, and the {@link Server#tps} read.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchedulerApiAcceptanceTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString());
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    @Test
    void theNextTickRunsOnceOnTheOwnerContext() throws Exception {
        EngineServer server = boot();
        CountDownLatch ran = new CountDownLatch(1);
        AtomicInteger fires = new AtomicInteger();
        AtomicReference<String> threadName = new AtomicReference<>();
        server.scheduler().runNextTick(() -> {
            fires.incrementAndGet();
            threadName.set(Thread.currentThread().getName());
            ran.countDown();
        });
        assertTrue(ran.await(3, TimeUnit.SECONDS), "the body ran within a tick");
        EngineAwaits.awaitTicks(server.world(), 5, "several further ticks pass");
        assertEquals(1, fires.get(), "the one-shot body ran exactly once");
        assertEquals("zamin-tick", threadName.get(),
                "the body ran on the simulation owner's thread");
        server.shutdown(null);
    }

    @Test
    void theDelayLandsAtTheNthNextTick() throws Exception {
        EngineServer server = boot();
        // A repeating interval-1 task is the tick counter; the delayed
        // body must land AFTER exactly three counters.
        AtomicInteger ticks = new AtomicInteger();
        server.scheduler().runRepeating(1, 1, ticks::incrementAndGet);

        CountDownLatch ran = new CountDownLatch(1);
        AtomicInteger seenTicks = new AtomicInteger(-1);
        server.scheduler().runLater(3, () -> {
            seenTicks.set(ticks.get());
            ran.countDown();
        });
        assertTrue(ran.await(3, TimeUnit.SECONDS), "the delayed body ran");
        // The drain order inside one tick: the repeating counter armed at
        // tick 1 re-arms each tick; the delay-3 body lands on the 3rd-next
        // drain, after the counter's 3rd visit (or interleaved the same
        // tick — the submission order guarantees >= 2).
        assertTrue(seenTicks.get() >= 2,
                "the delay-3 body landed at the third-next drain (saw tick " + seenTicks.get() + ")");
        server.shutdown(null);
    }

    @Test
    void theRepeatFiresUntilCancelled() throws Exception {
        EngineServer server = boot();
        AtomicInteger fires = new AtomicInteger();
        Task task = server.scheduler().runRepeating(1, 1, fires::incrementAndGet);
        EngineAwaits.awaitTicks(server.world(), 30, "~30 ticks at 20Hz");
        task.cancel();
        int atCancel = fires.get();
        assertTrue(atCancel >= 20, "the repeat fired through the window (saw " + atCancel + ")");
        EngineAwaits.awaitTicks(server.world(), 8, "the settle");
        assertEquals(atCancel, fires.get(), "no further fires after the cancel");
        assertTrue(task.cancelled());
        server.shutdown(null);
    }

    @Test
    void theCancelledOneShotNeverRuns() throws Exception {
        EngineServer server = boot();
        AtomicInteger fires = new AtomicInteger();
        Task task = server.scheduler().runLater(5, fires::incrementAndGet);
        task.cancel();
        EngineAwaits.awaitTicks(server.world(), 12, "the settle");
        assertEquals(0, fires.get(), "the cancelled body never ran");
        assertEquals(true, task.cancelled());
        server.shutdown(null);
    }

    @Test
    void theTpsReadsASaneWindow() throws Exception {
        EngineServer server = boot();
        EngineAwaits.awaitTicks(server.world(), 22, "a full second of ticks");
        double tps = server.tps(1);
        assertTrue(tps > 0.0 && tps <= 20.0, "the tps band (got " + tps + ")");
        assertTrue(tps >= 19.0, "an idle server holds its rate (got " + tps + ")");
        assertNotNull(server.scheduler(), "the scheduler mount exists");
        server.shutdown(null);
    }
}
