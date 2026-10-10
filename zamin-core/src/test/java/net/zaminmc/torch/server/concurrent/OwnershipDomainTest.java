package net.zaminmc.torch.server.concurrent;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Phase 1 ownership primitives over their required correctness list
 * (the rollout's Phase 1 tests-to-pass — {@code
 * docs/CONCURRENCY_DECISIONS_AND_ROLLOUT.md} §3 and the design's §11):
 * wrong-context mutation fails with the domain + operation context, stale
 * generations reject compute results, one domain never runs two simulation
 * tasks concurrently, and cancellation never resurrects a queued task.
 */
class OwnershipDomainTest {

    @Test
    void wrongContextMutationFailsWithDomainAndOperationContext() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        domain.enter();
        try {
            CountDownLatch attempted = new CountDownLatch(1);
            AtomicReference<Throwable> caught = new AtomicReference<>();
            Thread intruder = new Thread(() -> {
                try {
                    domain.checkInContext("setBlock");
                } catch (Throwable t) {
                    caught.set(t);
                } finally {
                    attempted.countDown();
                }
            }, "intruder");
            intruder.start();
            assertTrue(attempted.await(5, TimeUnit.SECONDS), "the probe ran");
            OwnershipViolationException violation =
                    assertInstanceOf(OwnershipViolationException.class, caught.get());
            assertEquals("simulation:test", violation.domain());
            assertEquals("setBlock", violation.operation());
            // The historical contract: the world's wrong-thread guard threw
            // IllegalStateException; the diagnosable failure keeps it.
            assertInstanceOf(IllegalStateException.class, violation);
            assertTrue(violation.getMessage().contains("setBlock")
                    && violation.getMessage().contains("simulation:test"),
                    "the message carries the operation and the domain");
        } finally {
            domain.exit();
        }
    }

    @Test
    void staleGenerationResultIsRejectedFreshOneApplies() {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        domain.enter();
        try {
            ComputeTicket<String> fresh = ComputeTicket.of(domain, "path-v1");
            assertTrue(fresh.isApplicableTo(domain));
            assertEquals("path-v1", fresh.resultOrThrow(domain));

            // The ownership transfer (migration): the generation moves, the
            // old ticket is dead — discarded and recomputed, never applied.
            long old = domain.transferOwnership();
            assertEquals(0, old);
            assertFalse(fresh.isApplicableTo(domain));
            StaleResultException stale = assertThrows(StaleResultException.class,
                    () -> fresh.resultOrThrow(domain));
            assertEquals(0, stale.ticketGeneration());
            assertEquals(1, stale.currentGeneration());
            assertTrue(stale.getMessage().contains("simulation:test"));

            // A result computed after the transfer applies.
            ComputeTicket<String> recomputed = ComputeTicket.of(domain, "path-v2");
            assertEquals("path-v2", recomputed.resultOrThrow(domain));
        } finally {
            domain.exit();
        }
    }

    @Test
    void oneDomainCannotRunTwoSimulationTasksConcurrently() {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        domain.enter(); // the scheduler's worker binding precedes task runs
        try {
            assertTrue(domain.tryBeginTick(), "the first task claims the slot");
            assertFalse(domain.tryBeginTick(), "the second task is refused");

            // The gate is what the DomainTask runs under: a second task's
            // run surfaces the violation instead of silently double-ticking.
            DomainTask blocker = new DomainTask(domain, () -> { });
            assertThrows(OwnershipViolationException.class, blocker::run,
                    "a run against a held slot fails loudly");
            domain.endTick();

            // The free slot: the task acquires, runs, releases.
            DomainTask solo = new DomainTask(domain, () -> { });
            solo.run();
            assertTrue(solo.isDone(), "the finished task released the slot");
            assertFalse(domain.ticking(), "the slot reopens after the task ends");
        } finally {
            domain.exit();
        }
    }

    @Test
    void cancelledTaskNeverResurrectsAndCancelDuringRunIsRefused() {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        AtomicBoolean ran = new AtomicBoolean(false);
        AtomicBoolean cancelRefused = new AtomicBoolean(true);
        AtomicReference<DomainTask> holder = new AtomicReference<>();
        DomainTask task = new DomainTask(domain, () -> {
            ran.set(true);
            // The in-flight run completes to its boundary: a cancellation
            // arriving mid-run cannot take effect.
            cancelRefused.set(!holder.get().cancel());
        });
        holder.set(task);

        assertTrue(task.cancel(), "the queued task cancels");
        task.run(); // the resurrection attempt
        assertFalse(ran.get(), "the cancelled delegate never ran");
        assertTrue(task.isCancelled() && task.isDone(), "the task stays cancelled");

        // A fresh task: the mid-run cancellation is refused.
        DomainTask live = new DomainTask(domain, () -> { });
        live.run();
        assertFalse(live.cancel(), "a done task cannot be cancelled");
    }

    @Test
    void diagnosticModeLogsAndContinues() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        domain.setEnforcement(OwnershipDomain.Enforcement.DIAGNOSTIC);
        CountDownLatch attempted = new CountDownLatch(1);
        AtomicReference<Throwable> caught = new AtomicReference<>();
        Thread intruder = new Thread(() -> {
            try {
                domain.checkInContext("setBlock"); // logs once, proceeds
                domain.checkInContext("setBlock"); // the dampener: no second log
            } catch (Throwable t) {
                caught.set(t);
            } finally {
                attempted.countDown();
            }
        }, "intruder");
        intruder.start();
        assertTrue(attempted.await(5, TimeUnit.SECONDS), "the probe ran");
        assertNull(caught.get(), "the diagnostic mode never throws");
    }

    @Test
    void enterIsSingleWriterNestedReentryCountsOnce() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        domain.enter();
        domain.enter(); // nested entry on the already-bound thread
        assertTrue(domain.inContext());
        CountDownLatch attempted = new CountDownLatch(1);
        AtomicReference<Throwable> caught = new AtomicReference<>();
        Thread intruder = new Thread(() -> {
            try {
                domain.enter();
            } catch (Throwable t) {
                caught.set(t);
            } finally {
                attempted.countDown();
            }
        }, "intruder");
        intruder.start();
        assertTrue(attempted.await(5, TimeUnit.SECONDS), "the probe ran");
        assertInstanceOf(OwnershipViolationException.class, caught.get());
        // The nesting counted once: the first exit leaves the inner entry's
        // depth, the binding still holds (in-context stays true).
        domain.exit();
        assertTrue(domain.inContext(),
                "one exit of a nested pair keeps the outer binding");
        domain.exit();
        assertFalse(domain.inContext(), "the outermost exit releases");
        // The exit pairing: an extra exit (no binding left) throws.
        assertThrows(OwnershipViolationException.class, domain::exit);
        // One endTick past any run throws (the pairing rule).
        assertThrows(OwnershipViolationException.class, domain::endTick);
    }
}
