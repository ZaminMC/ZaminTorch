package net.zaminmc.torch.server.concurrent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Phase 4 physical worker pool over its correctness list (the design's
 * §5 worker rules and §11 test list): every dispatched job runs exactly
 * once, independent jobs run concurrently (different domains on different
 * workers), the join is the enforced tick-edge barrier, a job's failure is
 * isolated from the batch and from the worker, and the shutdown is total
 * and idempotent.
 */
class DomainWorkerPoolTest {

    private DomainWorkerPool pool;

    @AfterEach
    void stopPool() {
        if (pool != null) {
            pool.shutdown();
            pool = null;
        }
    }

    @Test
    void everyDispatchedJobRunsExactlyOnce() {
        pool = new DomainWorkerPool(2);
        AtomicInteger runs = new AtomicInteger();
        pool.dispatchAndJoin(List.of(runs::incrementAndGet, runs::incrementAndGet, runs::incrementAndGet));
        assertEquals(3, runs.get(), "the join waits for the whole batch: every job ran");
        assertEquals(3, pool.dispatchedCount());
    }

    @Test
    void independentJobsRunConcurrently() throws InterruptedException {
        pool = new DomainWorkerPool(2);
        // Two jobs that meet in the middle: each opens its arrived gate then
        // waits for the other's. A serial executor deadlocks into the
        // timeouts; a concurrent one passes both gates.
        CountDownLatch firstArrived = new CountDownLatch(1);
        CountDownLatch secondArrived = new CountDownLatch(1);
        AtomicReference<String> verdict = new AtomicReference<>("timeout");
        Thread j1 = meet(firstArrived, secondArrived, verdict, "first-saw-second");
        Thread j2 = meet(secondArrived, firstArrived, verdict, "second-saw-first");
        pool.dispatchAndJoin(List.of(j1, j2));
        assertEquals("first-saw-second", verdict.get(),
                "both jobs overlapped: the pool ran them on different workers");
    }

    private Thread meet(CountDownLatch openMine, CountDownLatch awaitTheirs,
                        AtomicReference<String> verdict, String label) {
        Thread worker = new Thread(() -> {
            openMine.countDown();
            try {
                if (awaitTheirs.await(5, TimeUnit.SECONDS)) {
                    verdict.compareAndSet("timeout", label);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        worker.setDaemon(true);
        return worker;
    }

    @Test
    void theJoinIsTheEnforcedTickEdge() {
        pool = new DomainWorkerPool(1);
        long start = System.nanoTime();
        pool.dispatchAndJoin(List.of(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(120);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsedMillis >= 100,
                "dispatchAndJoin returns only after the job finished (the tick-edge join); took "
                        + elapsedMillis + "ms");
    }

    @Test
    void aFailingJobIsIsolatedFromTheBatchAndTheWorker() {
        pool = new DomainWorkerPool(2);
        AtomicInteger survivors = new AtomicInteger();
        RuntimeException boom = new RuntimeException("poisoned job");
        pool.dispatchAndJoin(List.of(
                () -> { throw boom; },
                survivors::incrementAndGet,
                survivors::incrementAndGet));
        assertEquals(2, survivors.get(), "the poisoned job never blocks its batch peers");
        assertEquals(1, pool.failedJobCount());
        // The worker survived: the next dispatch still runs.
        AtomicInteger after = new AtomicInteger();
        pool.dispatchAndJoin(List.of(after::incrementAndGet));
        assertEquals(1, after.get(), "a job failure never kills the worker");
    }

    @Test
    void emptyBatchJoinsWithoutWork() {
        pool = new DomainWorkerPool(2);
        pool.dispatchAndJoin(List.of());
        assertEquals(0, pool.dispatchedCount());
    }

    @Test
    void shutdownIsTotalAndIdempotent() {
        pool = new DomainWorkerPool(1);
        pool.shutdown();
        assertTrue(pool.isShutdown());
        assertThrows(IllegalStateException.class,
                () -> pool.dispatchAndJoin(List.of(() -> { })),
                "a shut-down pool refuses dispatch loudly");
        pool.shutdown(); // idempotent
        assertEquals(1, pool.workerCount());
        assertFalse(pool.dispatchedCount() > 0, "no job dispatched after shutdown");
    }

    @Test
    void defaultSizingStaysConservative() {
        // The oversubscription rule: half the reported cores, capped at 4,
        // never zero.
        int cores = Runtime.getRuntime().availableProcessors();
        int expected = Math.max(1, Math.min(4, cores / 2));
        assertEquals(expected, DomainWorkerPool.defaultWorkerCount());
    }
}
