package net.zaminmc.torch.server.concurrent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Phase 2 scheduler over its required correctness list (the design's
 * §11 + the rollout's Phase 2 tests-to-pass): submission order inside a
 * domain, delayed tasks run when due (not before), same-deadline tasks run
 * in sequence order, tasks run inside the domain's context, cancellation is
 * permanent, backpressure is loud, shutdown is total, and the telemetry
 * records the runs.
 */
class SimulationSchedulerTest {

    @Test
    void readyTasksRunInSubmissionOrderWithinTheDomain() {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        SimulationScheduler scheduler = new SimulationScheduler();
        List<String> order = new ArrayList<>();
        scheduler.submit(domain, () -> order.add("first"));
        scheduler.submit(domain, () -> order.add("second"));
        scheduler.submit(domain, () -> order.add("third"));
        scheduler.runDue(1);
        assertEquals(List.of("first", "second", "third"), order,
                "the FIFO inside the domain is the gameplay ordering contract");
        assertEquals(3, scheduler.executedCount(domain));
    }

    @Test
    void delayedTasksRunWhenDueNotBefore() {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        SimulationScheduler scheduler = new SimulationScheduler();
        AtomicBoolean ran = new AtomicBoolean(false);
        scheduler.submitDelayed(domain, () -> ran.set(true), 5);

        scheduler.runDue(4);
        assertFalse(ran.get(), "the task due at tick 5 stays parked at tick 4");
        assertEquals(1, scheduler.pendingFor(domain), "the delayed task is still pending");

        scheduler.runDue(5);
        assertTrue(ran.get(), "the task runs on its due tick");
        assertEquals(0, scheduler.pendingFor(domain));
    }

    @Test
    void sameDeadlineTasksRunInSequenceOrder() {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        SimulationScheduler scheduler = new SimulationScheduler();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            scheduler.submitDelayed(domain, () -> { }, 10);
            final int tag = i;
            scheduler.submitDelayed(domain, () -> order.add(tag), 10);
        }
        scheduler.runDue(10);
        assertEquals(List.of(0, 1, 2, 3, 4), order,
                "the stable sequence breaks same-deadline ties (design §16)");
    }

    @Test
    void tasksRunInsideTheDomainContext() {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        SimulationScheduler scheduler = new SimulationScheduler();
        AtomicBoolean inContext = new AtomicBoolean(false);
        AtomicBoolean gateHeld = new AtomicBoolean(false);
        scheduler.submit(domain, () -> {
            inContext.set(domain.inContext());
            gateHeld.set(domain.ticking());
        });
        scheduler.runDue(1);
        assertTrue(inContext.get(), "the task's run binds the domain executor");
        assertTrue(gateHeld.get(), "the task's run holds the single-writer slot");
    }

    @Test
    void cancellationIsPermanentNoResurrection() {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        SimulationScheduler scheduler = new SimulationScheduler();
        AtomicBoolean readyRan = new AtomicBoolean(false);
        AtomicBoolean delayedRan = new AtomicBoolean(false);
        scheduler.submit(domain, () -> readyRan.set(true));
        DomainTask delayed = scheduler.submitDelayed(domain, () -> delayedRan.set(true), 3);

        assertTrue(delayed.cancel(), "the queued delayed task cancels");
        scheduler.runDue(1); // the ready task runs; the cancelled one would not anyway
        assertTrue(readyRan.get());
        assertFalse(delayedRan.get());
        scheduler.runDue(10); // the resurrection pass
        assertFalse(delayedRan.get(), "the cancelled delayed task never runs");
    }

    @Test
    void admissionBackpressureIsLoud() {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        SimulationScheduler scheduler = new SimulationScheduler(3);
        for (int i = 0; i < 3; i++) {
            scheduler.submit(domain, () -> { });
        }
        assertThrows(IllegalStateException.class, () -> scheduler.submit(domain, () -> { }),
                "the bound refusal is an exception — never a silent drop");
        assertEquals(1, scheduler.rejectedSubmissions());
        scheduler.runDue(1); // the drain reopens the admission
        scheduler.submit(domain, () -> { });
        assertEquals(1, scheduler.pendingFor(domain));
    }

    @Test
    void shutdownStopsAdmissionAndCancelsEverythingQueued() {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        SimulationScheduler scheduler = new SimulationScheduler();
        AtomicBoolean ran = new AtomicBoolean(false);
        scheduler.submit(domain, () -> ran.set(true));
        scheduler.submitDelayed(domain, () -> { }, 5);

        scheduler.shutdown();
        assertTrue(scheduler.isShutdown());
        assertThrows(IllegalStateException.class, () -> scheduler.submit(domain, () -> { }),
                "admission stops at shutdown");
        assertThrows(IllegalStateException.class,
                () -> scheduler.submitDelayed(domain, () -> { }, 5));
        assertEquals(0, scheduler.pendingFor(domain), "the queues drain on shutdown");
        scheduler.runDue(10); // the post-shutdown walk runs nothing
        assertFalse(ran.get(), "the queued work never resurrects after shutdown");
        scheduler.shutdown(); // idempotent
        assertTrue(scheduler.isShutdown());
    }

    @Test
    void aFailingTaskNeverKillsTheSchedule() {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        SimulationScheduler scheduler = new SimulationScheduler();
        AtomicBoolean survivorRan = new AtomicBoolean(false);
        scheduler.submit(domain, () -> {
            throw new RuntimeException("the poisoned task");
        });
        scheduler.submit(domain, () -> survivorRan.set(true));
        scheduler.runDue(1);
        assertTrue(survivorRan.get(), "the failure skips one task, not the schedule");
        assertEquals(2, scheduler.executedCount(domain));
    }

    @Test
    void telemetryRecordsTheRuns() {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        SimulationScheduler scheduler = new SimulationScheduler();
        scheduler.submit(domain, () -> { });
        scheduler.runDue(1);
        assertTrue(scheduler.lastDurationNanos(domain) > 0,
                "the last duration records (the design §17 minimum telemetry)");
        assertEquals(1, scheduler.executedCount(domain));
    }

    @Test
    void crossDomainWalkDrainsEachDomainInStableOrder() {
        OwnershipDomain alpha = OwnershipDomain.create("simulation:alpha");
        OwnershipDomain beta = OwnershipDomain.create("simulation:beta");
        SimulationScheduler scheduler = new SimulationScheduler();
        List<String> order = new ArrayList<>();
        scheduler.submit(beta, () -> order.add("beta-1"));
        scheduler.submit(alpha, () -> order.add("alpha-1"));
        scheduler.submit(alpha, () -> order.add("alpha-2"));
        scheduler.submit(beta, () -> order.add("beta-2"));
        scheduler.runDue(1);
        // The stable domain-name walk: alpha fully drains, then beta — the
        // Phase 2 serial contract (per-domain FIFO preserved; the rotation
        // policy goes live with the Phase 4 pool).
        assertEquals(List.of("alpha-1", "alpha-2", "beta-1", "beta-2"), order);
    }

    @Test
    void delayedAdmissionCarriesTheCancelHandle() {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        SimulationScheduler scheduler = new SimulationScheduler();
        AtomicReference<DomainTask> handle = new AtomicReference<>();
        handle.set(scheduler.submitDelayed(domain, () -> { }, 2));
        assertFalse(handle.get().isCancelled());
        assertTrue(handle.get().cancel());
        scheduler.runDue(5);
        assertEquals(0, scheduler.pendingFor(domain));
    }
}
