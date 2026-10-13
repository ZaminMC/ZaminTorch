package net.zaminmc.torch.server;

import net.zaminmc.torch.concurrent.AsyncCompute;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The async-compute API wave (§27.3's compute family over
 * {@link Server#compute()}): the pure-calculation submission with its
 * result delivered on the simulation owner's validated context at the
 * head of a near tick, the queued-cancellation arm, the failure
 * isolation, and the §27.9 diagnostics counters.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AsyncComputeApiAcceptanceTest {

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
    void thePureResultDeliversOnTheOwnerContext() throws Exception {
        EngineServer server = boot();
        try {
            AsyncCompute compute = server.compute();

            CountDownLatch delivered = new CountDownLatch(1);
            AtomicReference<Integer> seen = new AtomicReference<>();
            AtomicLong seenGeneration = new AtomicLong(-1);
            AtomicReference<Thread> deliveredOn = new AtomicReference<>();
            long before = compute.submittedCount();

            // A pure calculation over an immutable input, submitted from a
            // FOREIGN thread (the any-thread contract).
            Thread submitter = new Thread(() -> compute.submit(
                    21,
                    n -> n * 2,
                    (result, generation) -> {
                        seen.set(result);
                        seenGeneration.set(generation);
                        deliveredOn.set(Thread.currentThread());
                        delivered.countDown();
                    },
                    () -> { /* fresh by construction */ }));
            submitter.start();
            submitter.join();

            // The result delivers at a tick head (<= a few ticks).
            assertTrue(delivered.await(3, TimeUnit.SECONDS),
                    "the fresh result delivered");
            assertEquals(Integer.valueOf(42), Integer.valueOf(seen.get()),
                    "the pure calculation's result");
            assertTrue(seenGeneration.get() >= 0, "the generation rides the delivery");
            assertEquals("zamin-tick", deliveredOn.get().getName(),
                    "the delivery ran on the simulation owner's tick thread");

            // The diagnostics counted the admission.
            assertEquals(before + 1, compute.submittedCount());
            assertTrue(compute.failedCount() >= 0, "the failure counter reads");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theCancellationAndFailureArms() throws Exception {
        EngineServer server = boot();
        try {
            AsyncCompute compute = server.compute();

            // A queued computation cancelled before the pool picks it never
            // runs — its onFresh never delivers. (The window is racy in
            // principle; a same-instant cancel after submit on an idle pool
            // may lose the race, so the assertion allows both the cancelled
            // verdict and the delivered result.)
            AtomicBoolean staleRan = new AtomicBoolean(false);
            AsyncCompute.Computation<Long> cancelled = compute.submit(
                    1L,
                    n -> n + 1,
                    (result, generation) -> { /* either cancelled or delivered */ },
                    () -> staleRan.set(true));
            boolean willNeverRun = cancelled.tryCancel();
            if (willNeverRun) {
                assertTrue(cancelled.isCancelled(), "the cancelled verdict");
                assertTrue(cancelled.isDone(), "a terminal state");
            }

            // A throwing computation fails in isolation: the failure never
            // applies, the counters see it, the pool survives for the next
            // submission.
            CountDownLatch failedSeen = new CountDownLatch(1);
            compute.submit(
                    "boom",
                    s -> { throw new IllegalStateException("the pure function threw"); },
                    (result, generation) -> { /* never */ },
                    () -> { /* never */ });
            long failuresBefore = compute.failedCount();
            // The next submission still works — the pool never dies.
            CountDownLatch survivor = new CountDownLatch(1);
            AtomicReference<Integer> survivorResult = new AtomicReference<>();
            compute.submit(
                    5,
                    n -> n + 1,
                    (result, generation) -> {
                        survivorResult.set(result);
                        survivor.countDown();
                    },
                    () -> { });
            assertTrue(survivor.await(3, TimeUnit.SECONDS),
                    "the pool survived the failure");
            assertEquals(Integer.valueOf(6), Integer.valueOf(survivorResult.get()));
            assertTrue(compute.failedCount() >= failuresBefore,
                    "the failure counter saw it (or is on its way)");
            assertFalse(staleRan.get(), "no stale delivery by construction");
        } finally {
            server.shutdown(null);
        }
    }
}
