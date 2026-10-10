package net.zaminmc.torch.server.concurrent;

import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The cross-owner interaction protocol (the permanent architecture's Phase 4
 * first unit — the design's §7 protocol and ADR-8): gameplay operations that
 * cross an ownership boundary travel as immutable intents, the
 * <em>target</em> authority validates and applies them in its own context at
 * its own boundary, admission is exactly-once within the in-flight window
 * (the operation id is the idempotency key), and the pending bound makes
 * backpressure loud. A one-way intent is the default; the result path rides
 * the same envelope when an operation needs its answer delivered back
 * (the design §10.1: "a one-way message should be the default when it can
 * express the gameplay operation correctly").
 *
 * <p>The hard prohibitions hold by construction (design §7.2): the source
 * never mutates the target's state directly — it enqueues an intent; the
 * target's drain runs on the target's own authority; no simulation worker
 * blocks waiting for another domain's progress. The four mandatory hard
 * cases (hopper transfer, piston across a boundary, redstone ordering,
 * entity-transfer-with-AI) each express through this envelope — the per-
 * mechanic protocols layer on top in their own slices.</p>
 */
public final class CrossOwnerRouter {

    private static final Logger LOGGER = Logger.getLogger(CrossOwnerRouter.class.getName());

    /** The default per-target pending bound (the explicit overload contract). */
    public static final int DEFAULT_MAX_PENDING_PER_TARGET = 1024;

    /**
     * One cross-owner operation: the immutable envelope (the design §7.1 —
     * operation id, source identity + generation, the target, and the
     * action the TARGET runs in its own context). The action closes over
     * whatever payload it needs; the payload must be immutable or
     * effectively so (the design invariant 4: no live mutable objects
     * cross the boundary inside the envelope).
     */
    public record Intent(OwnershipDomain target, UUID operationId,
                         String sourceDomain, long sourceGeneration,
                         String operation, Runnable action) {

        public Intent {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(sourceDomain, "sourceDomain");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(action, "action");
        }
    }

    /** One target's inbound queue + the exactly-once window. */
    private static final class TargetBox {
        final OwnershipDomain domain;
        final Queue<Intent> inbound = new ConcurrentLinkedQueue<>();
        /** The in-flight operation ids: admission until drain (exactly-once). */
        final Map<UUID, Boolean> inFlight = new ConcurrentHashMap<>();
        final AtomicLong lastDurationNanos = new AtomicLong();
        final AtomicLong appliedCount = new AtomicLong();

        TargetBox(OwnershipDomain domain) {
            this.domain = domain;
        }

        int pending() {
            return inbound.size();
        }
    }

    private final Map<String, TargetBox> targets = new ConcurrentHashMap<>();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final int maxPendingPerTarget;
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong duplicatesDropped = new AtomicLong();
    private final AtomicLong refused = new AtomicLong();
    private final AtomicLong failedIntents = new AtomicLong();

    public CrossOwnerRouter() {
        this(DEFAULT_MAX_PENDING_PER_TARGET);
    }

    public CrossOwnerRouter(int maxPendingPerTarget) {
        if (maxPendingPerTarget < 1) {
            throw new IllegalArgumentException("maxPendingPerTarget must be positive");
        }
        this.maxPendingPerTarget = maxPendingPerTarget;
    }

    /**
     * Admits a cross-owner intent. Any thread; the source does NOT touch the
     * target's state here. Exactly-once within the in-flight window: a
     * duplicate operation id (queued or already applied but not yet drained)
     * is dropped harmlessly — the design's duplicate-delivery rule.
     *
     * @return true when admitted; false when a duplicate (the caller treats
     *         "already handled" as success)
     * @throws IllegalStateException on shutdown or a full target queue — the
     *         loud refusal (the design invariant 8)
     */
    public boolean submit(Intent intent) {
        Objects.requireNonNull(intent, "intent");
        TargetBox box = boxOf(intent.target());
        if (shutdown.get()) {
            refused.incrementAndGet();
            throw new IllegalStateException("Router is shut down; intent '"
                    + intent.operation() + "' refused for target '"
                    + intent.target().name() + "'");
        }
        synchronized (box) {
            if (box.pending() >= maxPendingPerTarget) {
                refused.incrementAndGet();
                throw new IllegalStateException("Cross-owner pending bound exceeded for target '"
                        + intent.target().name() + "' (" + maxPendingPerTarget
                        + "); intent '" + intent.operation() + "' refused");
            }
            if (box.inFlight.putIfAbsent(intent.operationId(), Boolean.TRUE) != null) {
                duplicatesDropped.incrementAndGet();
                return false; // exactly-once: the duplicate is harmless
            }
        }
        box.inbound.offer(intent);
        submitted.incrementAndGet();
        return true;
    }

    private TargetBox boxOf(OwnershipDomain domain) {
        return targets.computeIfAbsent(domain.name(), k -> new TargetBox(domain));
    }

    /**
     * The target's boundary call: applies every inbound intent in the
     * target's context, in admission order. The intents validate their own
     * preconditions inside the action (the design §7.1 step 3 — the target
     * "validates target existence, ownership generation, permissions, and
     * current gameplay preconditions"); a failing intent is logged, counted,
     * and skipped — one bad intent never kills the drain. The window's
     * operation ids clear here: a re-delivery after processing is a new
     * operation by contract.
     *
     * @return the number of intents applied
     * @throws OwnershipViolationException when the caller is not the target's
     *         bound executor (STRICT) — the drain is an authoritative apply
     */
    public int drainIntents(OwnershipDomain target) {
        target.checkInContext("drainIntents");
        TargetBox box = targets.get(target.name());
        if (box == null) {
            return 0;
        }
        long start = System.nanoTime();
        int applied = 0;
        Intent intent;
        while ((intent = box.inbound.poll()) != null) {
            try {
                intent.action().run();
                box.appliedCount.incrementAndGet();
                applied++;
            } catch (Throwable t) {
                failedIntents.incrementAndGet();
                LOGGER.log(Level.SEVERE, "Cross-owner intent '"
                        + intent.operation() + "' from '" + intent.sourceDomain()
                        + "' failed in target '" + target.name() + "'; skipped", t);
            } finally {
                box.inFlight.remove(intent.operationId());
            }
        }
        box.lastDurationNanos.set(System.nanoTime() - start);
        return applied;
    }

    /**
     * Stops admission permanently and cancels everything queued — the
     * in-flight window clears so a restarting router starts clean. Idempotent.
     */
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        for (TargetBox box : targets.values()) {
            synchronized (box) {
                box.inbound.clear();
                box.inFlight.clear();
            }
        }
        LOGGER.fine("Cross-owner router shut down; queued intents cancelled");
    }

    /** @return the pending intent count for the target. */
    public int pendingFor(OwnershipDomain target) {
        TargetBox box = targets.get(target.name());
        return box == null ? 0 : box.pending();
    }

    /** @return the admitted-intent count (telemetry). */
    public long submittedCount() {
        return submitted.get();
    }

    /** @return the dropped-duplicate count (the exactly-once telemetry). */
    public long duplicatesDropped() {
        return duplicatesDropped.get();
    }

    /** @return the refused-intent count (the backpressure telemetry). */
    public long refusedCount() {
        return refused.get();
    }

    /** @return the failed-intent count (the precondition-failure telemetry). */
    public long failedCount() {
        return failedIntents.get();
    }

    /** @return how many intents the target applied (telemetry). */
    public long appliedCount(OwnershipDomain target) {
        TargetBox box = targets.get(target.name());
        return box == null ? 0 : box.appliedCount.get();
    }
}
