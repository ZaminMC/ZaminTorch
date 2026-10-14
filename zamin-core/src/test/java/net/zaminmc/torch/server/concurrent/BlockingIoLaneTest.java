package net.zaminmc.torch.server.concurrent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blocking-I/O lane's own correctness list (the §28.1 fourth category):
 * the work runs off the simulation pool, every completion returns through
 * the owner's drain (never a hidden blocking wait), fresh applies and
 * stale discards validate at the boundary, admission refuses loudly, and
 * the shutdown cancels the queued work and interrupts the in-flight calls.
 *
 * <p>The suite's central waiting pattern: the worker's completion lands in
 * the owner's mailbox — {@code onFresh} itself runs only at the drain — so
 * every "the task finished" wait polls {@code pendingDeliveries()}, the
 * §28.9 observability read, instead of parking on an arm that by contract
 * cannot fire yet.</p>
 */
class BlockingIoLaneTest {

    private BlockingIoLane lane;

    @AfterEach
    void stopLane() {
        if (lane != null) {
            lane.shutdown();
            lane = null;
        }
    }

    /** Bounded wait until the mailbox holds the given delivery count. */
    private static void awaitDeliveries(BlockingIoLane lane, OwnershipDomain domain,
                                        int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (lane.pendingDeliveries(domain) < expected) {
            assertTrue(System.nanoTime() < deadline,
                    "the mailbox never reached " + expected + " waiting deliveries");
            TimeUnit.MILLISECONDS.sleep(10);
        }
    }

    @Test
    void theCompletionAppliesOnTheOwnersDrainInsideItsContext() throws Exception {
        lane = new BlockingIoLane(8, 2);
        OwnershipDomain domain = OwnershipDomain.create("test:io-fresh");
        // The worker thread runs the blocking work; the completion must NOT
        // run there — it lands in the mailbox for the owner's drain.
        AtomicInteger mailboxApplies = new AtomicInteger();
        lane.submit(domain, "read-config",
                () -> "the-value",
                (value, gen) -> mailboxApplies.incrementAndGet(),
                () -> { });
        awaitDeliveries(lane, domain, 1);
        assertEquals(0, mailboxApplies.get(),
                "the completion never runs on the worker thread");
        // The submitter thread here is NOT the bound owner: binding first,
        // then draining in context.
        domain.enter();
        try {
            assertEquals(1, lane.drainCompletions(domain));
            assertEquals(1, mailboxApplies.get(),
                    "the fresh completion applied exactly once, on the owner");
        } finally {
            domain.exit();
        }
        assertEquals(1, lane.appliedCount(domain));
        assertEquals(0, lane.staleCount(domain));
    }

    @Test
    void aStaleCompletionDiscardsThroughTheFallbackNeverApplies() throws Exception {
        lane = new BlockingIoLane(8, 2);
        OwnershipDomain domain = OwnershipDomain.create("test:io-stale");
        AtomicInteger freshApplies = new AtomicInteger();
        AtomicInteger staleDiscards = new AtomicInteger();
        lane.submit(domain, "save-chunk",
                () -> "saved",
                (value, gen) -> freshApplies.incrementAndGet(),
                () -> staleDiscards.incrementAndGet());
        awaitDeliveries(lane, domain, 1);
        // The generation moves BEFORE the owner drains: the completion was
        // computed against G1 and must never mutate state at G2.
        domain.transferOwnership();
        domain.enter();
        try {
            assertEquals(0, lane.drainCompletions(domain),
                    "the stale completion applied nothing");
        } finally {
            domain.exit();
        }
        assertEquals(0, freshApplies.get());
        assertEquals(1, staleDiscards.get());
        assertEquals(1, lane.staleCount(domain));
    }

    @Test
    void drainDemandsTheOwnerContext() throws Exception {
        lane = new BlockingIoLane(8, 2);
        OwnershipDomain domain = OwnershipDomain.create("test:io-context");
        lane.submit(domain, "touch", () -> "t", (v, g) -> { }, () -> { });
        awaitDeliveries(lane, domain, 1);
        // The drain is an authoritative apply: the caller must hold the
        // binding. This thread never entered.
        assertThrows(OwnershipViolationException.class,
                () -> lane.drainCompletions(domain));
        assertEquals(0, lane.appliedCount(domain));
    }

    @Test
    void admissionRefusesLoudlyWhenFull() throws Exception {
        lane = new BlockingIoLane(2, 1);
        OwnershipDomain domain = OwnershipDomain.create("test:io-bound");
        // Park the single worker inside a task so the queue fills: one
        // running, two queued (capacity 2).
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        lane.submit(domain, "hold", () -> {
            running.countDown();
            release.await();
            return "held";
        }, (v, g) -> { }, () -> { });
        assertTrue(running.await(5, TimeUnit.SECONDS));
        lane.submit(domain, "queued-1", () -> "one", (v, g) -> { }, () -> { });
        lane.submit(domain, "queued-2", () -> "two", (v, g) -> { }, () -> { });
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> lane.submit(domain, "over-bound", () -> "three",
                        (v, g) -> { }, () -> { }),
                "the full queue refuses loudly, never drops silently");
        assertTrue(refused.getMessage().contains("refused"));
        release.countDown();
        assertEquals(1, lane.rejectedCount());
    }

    @Test
    void aFailingTaskDiscardsItsResultAndNeverKillsTheWorker() throws Exception {
        lane = new BlockingIoLane(8, 2);
        OwnershipDomain domain = OwnershipDomain.create("test:io-failure");
        AtomicInteger failedTaskSeen = new AtomicInteger();
        lane.submit(domain, "boom", () -> {
            throw new IllegalStateException("disk on fire");
        }, (v, g) -> failedTaskSeen.incrementAndGet(), () -> { });
        // The failure never offers a delivery; poll the failed telemetry.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (lane.failedCount() == 0 && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertEquals(1, lane.failedCount());
        // The worker survived: the next task still runs and delivers.
        lane.submit(domain, "after-failure", () -> "ok", (v, g) -> { }, () -> { });
        awaitDeliveries(lane, domain, 1);
        domain.enter();
        try {
            assertEquals(1, lane.drainCompletions(domain));
        } finally {
            domain.exit();
        }
        assertEquals(0, failedTaskSeen.get(),
                "the failed task's result never applied anywhere");
    }

    @Test
    void shutdownCancelsQueuedAndInterruptsInFlight() throws Exception {
        lane = new BlockingIoLane(8, 2);
        OwnershipDomain domain = OwnershipDomain.create("test:io-shutdown");
        // One in-flight task that blocks on the interruptible sleep, one
        // already delivered into the mailbox, one worker parked on a second
        // interruptible hold, and one still-queued task behind it.
        CountDownLatch inFlight = new CountDownLatch(1);
        AtomicReference<String> inFlightOutcome = new AtomicReference<>("never");
        lane.submit(domain, "in-flight", () -> {
            inFlight.countDown();
            try {
                TimeUnit.SECONDS.sleep(30);
            } catch (InterruptedException e) {
                inFlightOutcome.set("interrupted");
                throw e;
            }
            return "completed";
        }, (v, g) -> { }, () -> { });
        assertTrue(inFlight.await(5, TimeUnit.SECONDS));
        lane.submit(domain, "delivered", () -> "d", (v, g) -> { }, () -> { });
        awaitDeliveries(lane, domain, 1);
        CountDownLatch holdRunning = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> holdOutcome = new AtomicReference<>("never");
        lane.submit(domain, "hold-two", () -> {
            holdRunning.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                holdOutcome.set("interrupted");
                throw e;
            }
            return "held";
        }, (v, g) -> { }, () -> { });
        assertTrue(holdRunning.await(5, TimeUnit.SECONDS));
        BlockingIoLane.IoHandle<?> queued = lane.submit(domain, "still-queued",
                () -> "q", (v, g) -> { }, () -> { });

        lane.shutdown();
        assertTrue(lane.isShutdown());
        // Both in-flight calls were interrupted (bounded: the joins returned).
        assertEquals("interrupted", inFlightOutcome.get(),
                "the shutdown interrupts the first blocking call in bounded time");
        assertEquals("interrupted", holdOutcome.get(),
                "the shutdown interrupts the second blocking call in bounded time");
        assertTrue(queued.isCancelled(),
                "the queued task was cancelled at shutdown and never runs");
        assertEquals(1, lane.cancelledAtShutdownCount());
        assertThrows(IllegalStateException.class,
                () -> lane.submit(domain, "after-shutdown", () -> "x",
                        (v, g) -> { }, () -> { }),
                "a shut-down lane refuses new work loudly");
        // The already-delivered completion still drains after shutdown.
        domain.enter();
        try {
            assertEquals(1, lane.drainCompletions(domain));
        } finally {
            domain.exit();
        }
    }

    @Test
    void tryCancelPreventsAQueuedTaskFromRunning() throws Exception {
        lane = new BlockingIoLane(8, 1);
        OwnershipDomain domain = OwnershipDomain.create("test:io-cancel");
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        lane.submit(domain, "hold", () -> {
            running.countDown();
            release.await();
            return "held";
        }, (v, g) -> { }, () -> { });
        assertTrue(running.await(5, TimeUnit.SECONDS));
        BlockingIoLane.IoHandle<?> queued = lane.submit(domain, "cancel-me",
                () -> "never", (v, g) -> { }, () -> { });
        assertTrue(queued.tryCancel());
        // Release the hold: the worker picks the cancelled task up and skips
        // it (isCancelled — never runs, never resurrects).
        release.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (lane.queuedDepth() > 0 && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertEquals(0, lane.queuedDepth(), "the worker consumed the cancelled task");
        assertTrue(queued.isCancelled());
        assertTrue(queued.isDone());
        assertFalse(queued.isFailed());
    }
}
