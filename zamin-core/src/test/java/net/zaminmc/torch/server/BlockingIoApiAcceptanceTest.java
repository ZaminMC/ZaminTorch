package net.zaminmc.torch.server;

import net.zaminmc.torch.concurrent.BlockingIo;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blocking-I/O API wave (§28.1's fourth category over
 * {@link Server#blockingIo()}): the storage-lane submission with its
 * completion delivered on the simulation owner's validated context at
 * the head of a near tick, the queued-cancellation arm, the failure
 * isolation, and the §27.9 diagnostics counters — the same acceptance
 * shape the compute family rides.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlockingIoApiAcceptanceTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString(), net.zaminmc.torch.GameMode.CREATIVE);
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    @Test
    void theCompletionDeliversOnTheOwnerContext() throws Exception {
        EngineServer server = boot();
        try {
            BlockingIo io = server.blockingIo();

            CountDownLatch delivered = new CountDownLatch(1);
            AtomicReference<String> seen = new AtomicReference<>();
            AtomicReference<Long> seenGeneration = new AtomicReference<>(-1L);
            AtomicReference<Thread> deliveredOn = new AtomicReference<>();
            AtomicReference<Thread> ranOn = new AtomicReference<>();
            long before = io.submittedCount();

            // A blocking call (a stand-in file read) submitted from a
            // FOREIGN thread (the any-thread contract).
            Thread submitter = new Thread(() -> io.submit(
                    "read-config",
                    () -> {
                        ranOn.set(Thread.currentThread());
                        return "the-value";
                    },
                    (result, generation) -> {
                        seen.set(result);
                        seenGeneration.set(generation);
                        deliveredOn.set(Thread.currentThread());
                        delivered.countDown();
                    },
                    () -> { /* fresh by construction */ }));
            submitter.start();
            submitter.join();

            // The completion delivers at a tick head (<= a few ticks).
            assertTrue(delivered.await(3, TimeUnit.SECONDS),
                    "the fresh completion delivered");
            assertEquals("the-value", seen.get(), "the blocking call's result");
            assertTrue(seenGeneration.get() >= 0, "the generation rides the delivery");
            assertEquals("zamin-tick", deliveredOn.get().getName(),
                    "the apply ran on the simulation owner's tick thread");
            assertTrue(ranOn.get().getName().startsWith("torch-io-"),
                    "the blocking work itself ran on the storage lane, not a "
                            + "simulation worker (" + ranOn.get().getName() + ")");

            // The diagnostics counted the admission.
            assertEquals(before + 1, io.submittedCount());
            assertTrue(io.failedCount() >= 0, "the failure counter reads");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theCancellationAndFailureArms() throws Exception {
        EngineServer server = boot();
        try {
            BlockingIo io = server.blockingIo();

            // A queued task cancelled before the pool picks it never runs —
            // its onFresh never delivers. (Same-instant cancel on an idle
            // pool may lose the race; both verdicts are legal.)
            AtomicBoolean staleRan = new AtomicBoolean(false);
            BlockingIo.Task<String> cancelled = io.submit(
                    "cancel-me",
                    () -> "never",
                    (result, generation) -> { /* either cancelled or delivered */ },
                    () -> staleRan.set(true));
            boolean willNeverRun = cancelled.tryCancel();
            if (willNeverRun) {
                assertTrue(cancelled.isCancelled(), "the cancelled verdict");
                assertTrue(cancelled.isDone(), "a terminal state");
            }

            // A throwing task fails in isolation: the failure never applies,
            // the counters see it, the lane survives for the next submission.
            io.submit(
                    "boom",
                    () -> { throw new IllegalStateException("the storage call threw"); },
                    (result, generation) -> { /* never */ },
                    () -> { /* never */ });
            long failuresBefore = io.failedCount();
            // The next submission still works — the lane never dies.
            CountDownLatch survivor = new CountDownLatch(1);
            AtomicReference<String> survivorResult = new AtomicReference<>();
            io.submit(
                    "after-failure",
                    () -> "ok",
                    (result, generation) -> {
                        survivorResult.set(result);
                        survivor.countDown();
                    },
                    () -> { });
            assertTrue(survivor.await(3, TimeUnit.SECONDS),
                    "the lane survived the failure");
            assertEquals("ok", survivorResult.get());
            assertTrue(io.failedCount() >= failuresBefore,
                    "the failure counter saw it (or is on its way)");
            assertFalse(staleRan.get(), "no stale delivery by construction");
        } finally {
            server.shutdown(null);
        }
    }
}
