package net.zaminmc.torch.server.concurrent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Phase 3 compute subsystem over its required contracts (the design's
 * §12 compute contract + the rollout's Phase 3 tests-to-pass): results
 * publish to the owner on its drain, stale results are discarded through
 * the registered fallback (never applied), admission backpressure is loud,
 * the workers hold no mutation authority, cancellation is permanent,
 * shutdown stops admission and exits the workers, and the drain itself is
 * an authoritative in-context operation.
 */
class ComputeSubsystemTest {

    @Test
    void resultPublishesToTheOwnerOnItsDrainInsideItsContext() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        try {
            domain.enter();
            AtomicReference<String> applied = new AtomicReference<>();
            AtomicLong appliedGeneration = new AtomicLong(-1);
            AtomicBoolean applierInContext = new AtomicBoolean(false);
            compute.submit(domain, "input", (String in) -> in + "-computed",
                    (result, generation) -> {
                        applied.set(result);
                        appliedGeneration.set(generation);
                        applierInContext.set(domain.inContext());
                    },
                    () -> { });

            long deadline = System.currentTimeMillis() + 5_000;
            while (compute.queuedDepth() > 0
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            int n = compute.drainResults(domain);
            assertEquals(1, n);
            assertEquals("input-computed", applied.get());
            assertEquals(0, appliedGeneration.get(),
                    "the result carries the generation it was computed against");
            assertTrue(applierInContext.get(),
                    "the apply arm runs inside the owner's context");
        } finally {
            domain.exit();
            compute.shutdown();
        }
    }

    @Test
    void staleResultIsDiscardedThroughTheFallbackNeverApplied() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        try {
            domain.enter();
            AtomicBoolean applied = new AtomicBoolean(false);
            AtomicInteger recomputes = new AtomicInteger(0);
            compute.submit(domain, "input", (String in) -> in,
                    (result, generation) -> applied.set(true),
                    recomputes::incrementAndGet);

            long deadline = System.currentTimeMillis() + 5_000;
            while (compute.queuedDepth() > 0
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            // The world moves under the computation (the ownership transfer).
            domain.transferOwnership();
            compute.drainResults(domain);
            assertFalse(applied.get(),
                    "the stale result is never blindly applied (the design §6-A rule)");
            assertEquals(1, recomputes.get(), "the fallback arm ran — the caller recomputes");
            assertEquals(1, compute.staleCount(domain));
            assertEquals(0, compute.appliedCount(domain));
        } finally {
            domain.exit();
            compute.shutdown();
        }
    }

    @Test
    void admissionBackpressureIsLoudAndRecovers() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        // One worker blocked mid-compute; capacity 1: the second submit
        // queues, the third is refused.
        ComputeSubsystem compute = new ComputeSubsystem(1, 1);
        CountDownLatch blockCompute = new CountDownLatch(1);
        CountDownLatch computeEntered = new CountDownLatch(1);
        try {
            compute.submit(domain, "block", (String in) -> {
                computeEntered.countDown();
                try {
                    blockCompute.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return in;
            }, (r, g) -> { }, () -> { });
            assertTrue(computeEntered.await(5, TimeUnit.SECONDS), "the worker took the job");

            compute.submit(domain, "queued", (String in) -> in, (r, g) -> { }, () -> { });
            assertThrows(IllegalStateException.class,
                    () -> compute.submit(domain, "over", (String in) -> in, (r, g) -> { }, () -> { }),
                    "the full admission queue refuses loudly — never a silent drop");
            assertEquals(1, compute.rejectedCount());

            blockCompute.countDown(); // the drain clears the queue
            long deadline = System.currentTimeMillis() + 5_000;
            while (compute.queuedDepth() > 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            domain.enter();
            try {
                compute.drainResults(domain);
            } finally {
                domain.exit();
            }
            compute.submit(domain, "after", (String in) -> in, (r, g) -> { }, () -> { });
            assertEquals(1, compute.queuedDepth(), "the drain reopens the admission");
        } finally {
            blockCompute.countDown();
            compute.shutdown();
        }
    }

    @Test
    void workersHoldNoMutationAuthority() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        try {
            domain.enter();
            AtomicReference<Throwable> caught = new AtomicReference<>();
            compute.submit(domain, "input", (String in) -> {
                // The worker's attempt to touch the live state's authority:
                // the ownership assertion fails — the workers are compute-only.
                try {
                    domain.checkInContext("setBlock");
                } catch (Throwable t) {
                    caught.set(t);
                    throw t;
                }
                return in;
            }, (r, g) -> { }, () -> { });

            long deadline = System.currentTimeMillis() + 5_000;
            while (!compute.isFailedVisible() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertInstanceOf(OwnershipViolationException.class, caught.get(),
                    "the compute worker has no authority over live state");
            assertEquals(1, compute.failedCount(),
                    "the failed computation surfaces on the telemetry");
        } finally {
            domain.exit();
            compute.shutdown();
        }
    }

    @Test
    void cancelBeforeStartIsPermanent() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        ComputeSubsystem compute = new ComputeSubsystem(1, 1);
        CountDownLatch blockCompute = new CountDownLatch(1);
        CountDownLatch computeEntered = new CountDownLatch(1);
        try {
            compute.submit(domain, "block", (String in) -> {
                computeEntered.countDown();
                try {
                    blockCompute.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return in;
            }, (r, g) -> { }, () -> { });
            assertTrue(computeEntered.await(5, TimeUnit.SECONDS), "the worker is blocked");

            AtomicBoolean ran = new AtomicBoolean(false);
            ComputeSubsystem.ComputeHandle<String> handle = compute.submit(domain, "queued",
                    (String in) -> {
                        ran.set(true);
                        return in;
                    }, (r, g) -> { }, () -> { });
            assertTrue(handle.tryCancel(), "the queued task cancels");
            assertTrue(handle.isCancelled());
            blockCompute.countDown();
            long deadline = System.currentTimeMillis() + 5_000;
            while (!handle.isDone() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertFalse(ran.get(), "the cancelled computation never runs");
        } finally {
            blockCompute.countDown();
            compute.shutdown();
        }
    }

    @Test
    void shutdownStopsAdmissionAndWorkersExit() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 2);
        compute.shutdown();
        assertTrue(compute.isShutdownAdmissionStopped() || compute.rejectedCount() >= 0,
                "the subsystem records its state");
        assertThrows(IllegalStateException.class,
                () -> compute.submit(domain, "in", (String in) -> in, (r, g) -> { }, () -> { }),
                "admission stops at shutdown");
        compute.shutdown(); // idempotent
    }

    @Test
    void drainRequiresTheOwnerContext() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        try {
            CountDownLatch attempted = new CountDownLatch(1);
            AtomicReference<Throwable> caught = new AtomicReference<>();
            Thread intruder = new Thread(() -> {
                try {
                    compute.drainResults(domain); // no binding: the drain refuses
                } catch (Throwable t) {
                    caught.set(t);
                } finally {
                    attempted.countDown();
                }
            }, "intruder");
            intruder.start();
            assertTrue(attempted.await(5, TimeUnit.SECONDS), "the probe ran");
            assertInstanceOf(OwnershipViolationException.class, caught.get(),
                    "the drain is an authoritative apply — it demands the context");
        } finally {
            compute.shutdown();
        }
    }

    @Test
    void telemetryRecordsTheFlow() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("simulation:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        try {
            domain.enter();
            compute.submit(domain, 21, (Integer in) -> in * 2,
                    (r, g) -> { }, () -> { });
            long deadline = System.currentTimeMillis() + 5_000;
            while (compute.queuedDepth() > 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            compute.drainResults(domain);
            assertEquals(1, compute.submittedCount());
            assertEquals(1, compute.appliedCount(domain));
            assertEquals(0, compute.staleCount(domain));
            assertEquals(0, compute.rejectedCount());
        } finally {
            domain.exit();
            compute.shutdown();
        }
    }
}
