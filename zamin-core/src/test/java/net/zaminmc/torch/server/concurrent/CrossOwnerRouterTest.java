package net.zaminmc.torch.server.concurrent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cross-owner router over the design's §7 protocol rules (Phase 4's
 * first unit): intents run in the TARGET's context at its boundary,
 * duplicates admit exactly-once, the pending bound refuses loudly, a
 * failing intent skips itself, and the drain is an authoritative
 * in-context operation.
 */
class CrossOwnerRouterTest {

    @Test
    void theIntentRunsInTheTargetContextNotTheSource() throws Exception {
        OwnershipDomain source = OwnershipDomain.create("simulation:source");
        OwnershipDomain target = OwnershipDomain.create("simulation:target");
        CrossOwnerRouter router = new CrossOwnerRouter();

        AtomicBoolean ranInTargetContext = new AtomicBoolean(false);
        router.submit(new CrossOwnerRouter.Intent(target, UUID.randomUUID(),
                source.name(), source.generation(), "hopper-pull",
                () -> ranInTargetContext.set(target.inContext())));

        // The target's own authority drains (the design: the target applies
        // in its own context at its own boundary).
        target.enter();
        try {
            assertEquals(1, router.drainIntents(target));
        } finally {
            target.exit();
        }
        assertTrue(ranInTargetContext.get(),
                "the action ran inside the target's bound context");

        // The source's context never saw the action.
        source.enter();
        try {
            assertEquals(0, router.drainIntents(source),
                    "the source's drain sees none of the target's intents");
        } finally {
            source.exit();
        }
    }

    @Test
    void duplicatesAdmitExactlyOnceWithinTheWindow() throws Exception {
        OwnershipDomain source = OwnershipDomain.create("simulation:source");
        OwnershipDomain target = OwnershipDomain.create("simulation:target");
        CrossOwnerRouter router = new CrossOwnerRouter();

        UUID operationId = UUID.randomUUID();
        AtomicInteger runs = new AtomicInteger(0);
        CrossOwnerRouter.Intent intent = new CrossOwnerRouter.Intent(target, operationId,
                source.name(), source.generation(), "explosion-block",
                runs::incrementAndGet);

        assertTrue(router.submit(intent), "the first admission takes");
        assertFalse(router.submit(intent), "the duplicate drops harmlessly");
        assertFalse(router.submit(intent), "the duplicate drops harmlessly");
        assertEquals(2, router.duplicatesDropped(),
                "both duplicate admissions dropped");
        assertEquals(1, router.submittedCount());

        target.enter();
        try {
            assertEquals(1, router.drainIntents(target));
        } finally {
            target.exit();
        }
        assertEquals(1, runs.get(), "exactly-once: the operation ran once");

        // The window cleared at drain: a re-delivery is a NEW operation.
        assertTrue(router.submit(intent), "the post-drain re-delivery admits fresh");
        target.enter();
        try {
            router.drainIntents(target);
        } finally {
            target.exit();
        }
        assertEquals(2, runs.get());
    }

    @Test
    void admissionBoundRefusesLoudly() {
        OwnershipDomain source = OwnershipDomain.create("simulation:source");
        OwnershipDomain target = OwnershipDomain.create("simulation:target");
        CrossOwnerRouter router = new CrossOwnerRouter(2);
        CountDownLatch blockTarget = new CountDownLatch(1);

        for (int i = 0; i < 2; i++) {
            router.submit(new CrossOwnerRouter.Intent(target, UUID.randomUUID(),
                    source.name(), source.generation(), "piston-push",
                    () -> {
                        try {
                            blockTarget.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }));
        }
        assertThrows(IllegalStateException.class,
                () -> router.submit(new CrossOwnerRouter.Intent(target, UUID.randomUUID(),
                        source.name(), source.generation(), "piston-push", () -> { })),
                "the bound refusal is an exception — never a silent drop");
        assertEquals(1, router.refusedCount());
        blockTarget.countDown();
    }

    @Test
    void aFailingIntentSkipsItselfNeverTheDrain() throws Exception {
        OwnershipDomain source = OwnershipDomain.create("simulation:source");
        OwnershipDomain target = OwnershipDomain.create("simulation:target");
        CrossOwnerRouter router = new CrossOwnerRouter();

        List<String> order = new ArrayList<>();
        router.submit(new CrossOwnerRouter.Intent(target, UUID.randomUUID(),
                source.name(), source.generation(), "poisoned",
                () -> {
                    order.add("poisoned");
                    throw new RuntimeException("the precondition refused it");
                }));
        router.submit(new CrossOwnerRouter.Intent(target, UUID.randomUUID(),
                source.name(), source.generation(), "healthy",
                () -> order.add("healthy")));

        target.enter();
        try {
            assertEquals(1, router.drainIntents(target),
                    "the failure skips one intent, not the drain");
        } finally {
            target.exit();
        }
        assertEquals(List.of("poisoned", "healthy"), order,
                "the admission order holds and the healthy intent ran");
        assertEquals(1, router.failedCount());
    }

    @Test
    void shutdownStopsAdmissionAndCancelsQueued() throws Exception {
        OwnershipDomain source = OwnershipDomain.create("simulation:source");
        OwnershipDomain target = OwnershipDomain.create("simulation:target");
        CrossOwnerRouter router = new CrossOwnerRouter();
        AtomicBoolean ran = new AtomicBoolean(false);
        router.submit(new CrossOwnerRouter.Intent(target, UUID.randomUUID(),
                source.name(), source.generation(), "doomed",
                () -> ran.set(true)));

        router.shutdown();
        assertThrows(IllegalStateException.class,
                () -> router.submit(new CrossOwnerRouter.Intent(target, UUID.randomUUID(),
                        source.name(), source.generation(), "late", () -> { })),
                "admission stops at shutdown");
        assertEquals(0, router.pendingFor(target), "the queue drains on shutdown");
        target.enter();
        try {
            assertEquals(0, router.drainIntents(target));
        } finally {
            target.exit();
        }
        assertFalse(ran.get(), "the queued intent never resurrects");
        router.shutdown(); // idempotent
    }

    @Test
    void drainDemandsTheTargetContext() throws Exception {
        OwnershipDomain target = OwnershipDomain.create("simulation:target");
        CrossOwnerRouter router = new CrossOwnerRouter();
        CountDownLatch attempted = new CountDownLatch(1);
        AtomicReference<Throwable> caught = new AtomicReference<>();
        Thread intruder = new Thread(() -> {
            try {
                router.drainIntents(target); // no binding: the drain refuses
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
    }

    @Test
    void telemetryRecordsTheFlow() throws Exception {
        OwnershipDomain source = OwnershipDomain.create("simulation:source");
        OwnershipDomain target = OwnershipDomain.create("simulation:target");
        CrossOwnerRouter router = new CrossOwnerRouter();
        router.submit(new CrossOwnerRouter.Intent(target, UUID.randomUUID(),
                source.name(), source.generation(), "block-update", () -> { }));
        target.enter();
        try {
            router.drainIntents(target);
        } finally {
            target.exit();
        }
        assertEquals(1, router.appliedCount(target));
        assertEquals(0, router.duplicatesDropped());
        assertEquals(0, router.refusedCount());
        assertEquals(0, router.failedCount());
    }
}
