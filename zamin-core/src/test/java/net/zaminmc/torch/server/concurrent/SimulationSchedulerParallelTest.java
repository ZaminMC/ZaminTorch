package net.zaminmc.torch.server.concurrent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Phase 4 parallel walk over its correctness list: the caller-bound
 * domain drains inline (nested binding, the live single-domain run stays
 * byte-identical), foreign domains dispatch to the pool and run
 * concurrently, the join waits for every foreign drain (the enforced
 * tick-edge barrier), one domain never runs two tasks at once even under
 * the parallel walk, and the FIFO inside a domain survives the dispatch.
 */
class SimulationSchedulerParallelTest {

    private DomainWorkerPool pool;

    @AfterEach
    void stopPool() {
        if (pool != null) {
            pool.shutdown();
            pool = null;
        }
    }

    @Test
    void callerBoundDomainDrainsInlineOnTheCallerThread() {
        OwnershipDomain caller = OwnershipDomain.create("simulation:caller");
        OwnershipDomain foreign = OwnershipDomain.create("simulation:foreign");
        pool = new DomainWorkerPool(2);
        SimulationScheduler scheduler = new SimulationScheduler();
        AtomicReference<String> callerTaskThread = new AtomicReference<>();
        AtomicReference<String> foreignTaskThread = new AtomicReference<>();
        AtomicBoolean callerInContext = new AtomicBoolean(false);
        scheduler.submit(caller, () -> {
            callerTaskThread.set(Thread.currentThread().getName());
            callerInContext.set(caller.inContext());
        });
        scheduler.submit(foreign, () -> foreignTaskThread.set(Thread.currentThread().getName()));
        caller.enter();
        try {
            scheduler.runDueParallel(1, pool);
        } finally {
            caller.exit();
        }
        assertEquals(Thread.currentThread().getName(), callerTaskThread.get(),
                "the caller-bound domain drains inline (nested enter, identical gameplay)");
        assertTrue(callerInContext.get());
        assertTrue(foreignTaskThread.get().startsWith("zamin-domain-"),
                "the foreign domain's drain dispatched to a pool worker");
        assertEquals(1, scheduler.executedCount(caller));
        assertEquals(1, scheduler.executedCount(foreign));
    }

    @Test
    void foreignDomainsRunConcurrently() {
        OwnershipDomain a = OwnershipDomain.create("simulation:a");
        OwnershipDomain b = OwnershipDomain.create("simulation:b");
        pool = new DomainWorkerPool(2);
        SimulationScheduler scheduler = new SimulationScheduler();
        CountDownLatch aArrived = new CountDownLatch(1);
        CountDownLatch bArrived = new CountDownLatch(1);
        AtomicBoolean bothOverlapped = new AtomicBoolean(false);
        scheduler.submit(a, () -> {
            aArrived.countDown();
            try {
                bothOverlapped.set(bArrived.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        scheduler.submit(b, () -> {
            bArrived.countDown();
            try {
                aArrived.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        scheduler.runDueParallel(1, pool);
        assertTrue(bothOverlapped.get(),
                "two foreign domains' drains ran at the same time on different workers");
    }

    @Test
    void theJoinWaitsForEveryForeignDrain() {
        OwnershipDomain foreign = OwnershipDomain.create("simulation:sleepy");
        pool = new DomainWorkerPool(1);
        SimulationScheduler scheduler = new SimulationScheduler();
        scheduler.submit(foreign, () -> {
            try {
                TimeUnit.MILLISECONDS.sleep(120);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        long start = System.nanoTime();
        scheduler.runDueParallel(1, pool);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsedMillis >= 100,
                "runDueParallel returns only after every domain's due work finished "
                        + "(the tick-edge barrier); took " + elapsedMillis + "ms");
    }

    @Test
    void oneDomainNeverRunsTwoTasksAtOnceUnderTheParallelWalk() {
        OwnershipDomain foreign = OwnershipDomain.create("simulation:gated");
        pool = new DomainWorkerPool(2);
        SimulationScheduler scheduler = new SimulationScheduler();
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        AtomicBoolean secondSawGateHeld = new AtomicBoolean(false);
        scheduler.submit(foreign, () -> {
            int now = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(now, Math::max);
            try {
                TimeUnit.MILLISECONDS.sleep(60);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            concurrent.decrementAndGet();
        });
        scheduler.submit(foreign, () -> {
            // The gate is held by the first task until it finishes: the
            // single-writer slot must be visible and the tasks must not
            // overlap.
            if (foreign.ticking()) {
                secondSawGateHeld.set(true);
            }
            maxConcurrent.accumulateAndGet(concurrent.incrementAndGet(), Math::max);
            concurrent.decrementAndGet();
        });
        scheduler.runDueParallel(1, pool);
        assertEquals(1, maxConcurrent.get(),
                "the FIFO plus the gate: the domain's two tasks ran back to back");
        assertTrue(secondSawGateHeld.get(),
                "the second task observed the single-writer slot held");
    }

    @Test
    void fifoInsideAForeignDomainSurvivesTheDispatch() {
        OwnershipDomain foreign = OwnershipDomain.create("simulation:ordered");
        pool = new DomainWorkerPool(2);
        SimulationScheduler scheduler = new SimulationScheduler();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            final int tag = i;
            scheduler.submit(foreign, () -> order.add(tag));
        }
        scheduler.runDueParallel(1, pool);
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7),
                order, "the submission order inside the domain is the gameplay contract");
    }

    @Test
    void everyDomainCallerBoundWalksSeriallyIdentically() {
        OwnershipDomain caller = OwnershipDomain.create("simulation:only");
        pool = new DomainWorkerPool(2);
        SimulationScheduler scheduler = new SimulationScheduler();
        List<String> order = new ArrayList<>();
        scheduler.submit(caller, () -> order.add("first"));
        scheduler.submit(caller, () -> order.add("second"));
        scheduler.submitDelayed(caller, () -> order.add("delayed"), 2);
        caller.enter();
        try {
            int executed = 0;
            executed += scheduler.runDueParallel(1, pool);
            assertEquals(2, executed, "the delayed task stays parked at tick 1");
            executed += scheduler.runDueParallel(2, pool);
            assertEquals(3, executed, "the delayed task runs at its due tick");
        } finally {
            caller.exit();
        }
        assertEquals(List.of("first", "second", "delayed"), order,
                "with every domain caller-bound the walk is the serial behavior");
        assertEquals(0, pool.dispatchedCount(),
                "no job dispatched: the inline path never touches the workers");
    }

    @Test
    void delayedForeignTasksRunWhenDueThroughThePool() {
        OwnershipDomain foreign = OwnershipDomain.create("simulation:delayed");
        pool = new DomainWorkerPool(1);
        SimulationScheduler scheduler = new SimulationScheduler();
        AtomicBoolean ran = new AtomicBoolean(false);
        scheduler.submitDelayed(foreign, () -> ran.set(true), 5);
        scheduler.runDueParallel(4, pool);
        assertEquals(false, ran.get(), "not due yet: parked");
        assertEquals(1, scheduler.pendingFor(foreign));
        scheduler.runDueParallel(5, pool);
        assertTrue(ran.get(), "the due delayed task rides the pool drain");
        assertEquals(0, scheduler.pendingFor(foreign));
    }
}
