package net.zaminmc.torch.server.concurrent;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The ownership migration protocol (the permanent architecture's Phase 4 —
 * the design's §9 "migration is a protocol, not a map update"): the
 * single-writer handoff of one domain's scope from the old context to the
 * new one through explicit phases, so a transfer is never an uncertain
 * window where two authorities could both resume (the design's §9 step 8
 * failure rule).
 *
 * <p>The protocol steps (design §9):</p>
 * <ol>
 *   <li>{@link #begin()} — the safe-point check: the domain's single-writer
 *       gate must be free (a migration starts between tasks, never mid-task)
 *       and the scheduler's admission for the domain holds (new submissions
 *       refused loudly; §9 step 2).</li>
 *   <li>{@link #drainQueued(SimulationScheduler, long)} — the already-queued
 *       work finishes at the old generation (§9 step 3: finish in-flight
 *       authoritative work at a defined safe point).</li>
 *   <li>{@link #transfer(Runnable)} — the scope's complete mutable state
 *       moves inside the OLD owner's context (§9 step 4; the caller holds
 *       the binding).</li>
 *   <li>{@link #publish()} — the generation bumps atomically (§9 step 5:
 *       "publish the new owner and increment generation atomically from the
 *       router's perspective") and admission releases (§9 step 7: resume
 *       scheduling). Every reference or result captured under the old
 *       generation is now diagnosably stale — compute tickets reject, the
 *       router revalidates (§9 step 6).</li>
 * </ol>
 *
 * <p>{@link #abort()} is the explicit failure recovery (§9 step 8): the
 * state never moved, admission releases, and the generation bumps anyway —
 * the fail-safe rule that no half-migrated observer keeps authority. A
 * migration instance runs once: begin twice refuses, and after publish or
 * abort the protocol is closed.</p>
 */
public final class OwnershipMigration {

    /** The protocol's phase machine: one path, one completion. */
    public enum Phase { NOT_STARTED, DRAINING, TRANSFERRING, PUBLISHED, ABORTED }

    private final OwnershipDomain domain;
    private final SimulationScheduler scheduler;
    private final AtomicBoolean begun = new AtomicBoolean(false);
    private volatile Phase phase = Phase.NOT_STARTED;

    public OwnershipMigration(OwnershipDomain domain, SimulationScheduler scheduler) {
        this.domain = Objects.requireNonNull(domain, "domain");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** The domain whose scope transfers. */
    public OwnershipDomain domain() {
        return domain;
    }

    /** @return the protocol's current phase. */
    public Phase phase() {
        return phase;
    }

    /**
     * §9 steps 1-2: the safe point plus the admission hold. The gate must be
     * free — a migration never interrupts an active task (the single-writer
     * invariant); admission holds so no new authoritative work enters while
     * the ownership is in flight.
     *
     * @throws IllegalStateException when a task holds the gate, or the
     *         protocol already ran
     */
    public void begin() {
        if (!begun.compareAndSet(false, true)) {
            throw new IllegalStateException("Migration already ran for '"
                    + domain.name() + "' (phase " + phase + ")");
        }
        if (domain.ticking()) {
            throw new IllegalStateException("Migration refused for '"
                    + domain.name() + "': the single-writer gate is held — "
                    + "a migration starts between tasks, never mid-task");
        }
        scheduler.holdAdmission(domain);
        phase = Phase.DRAINING;
    }

    /**
     * §9 step 3: the queued work finishes at the old generation. The caller
     * runs this inside the old owner's context ({@code drainDomain}
     * asserts it).
     *
     * @return the number of tasks executed
     */
    public int drainQueued(long nowTick) {
        requirePhase(Phase.DRAINING, "drainQueued");
        return scheduler.drainDomain(domain, nowTick);
    }

    /**
     * §9 step 4: the state transfer, run in the OLD owner's context. The
     * runnable closes over the scope's state and moves it to its new home;
     * the protocol never touches the payload.
     */
    public void transfer(Runnable stateMove) {
        requirePhase(Phase.DRAINING, "transfer");
        Objects.requireNonNull(stateMove, "stateMove");
        phase = Phase.TRANSFERRING;
        try {
            stateMove.run();
        } catch (Throwable t) {
            // A failed state move is the uncertain transfer: abort loudly —
            // the scope stays with the old owner, the generation bumps, and
            // no half-migrated observer keeps authority (§9 step 8).
            abort();
            throw t;
        }
        // The transfer completed; the protocol stays in its draining window
        // until publish closes it.
        phase = Phase.DRAINING;
    }

    /**
     * §9 steps 5-7: the atomic publish — the generation bumps (every
     * old-generation reference becomes diagnosably stale), then admission
     * releases and scheduling resumes for the new owner.
     */
    public void publish() {
        requirePhase(Phase.DRAINING, "publish");
        domain.transferOwnership();
        scheduler.releaseAdmission(domain);
        phase = Phase.PUBLISHED;
    }

    /**
     * §9 step 8: the explicit failure recovery — admission releases, the
     * generation bumps anyway (no half-migrated observer keeps authority),
     * and the scope stays with the old owner. Idempotent.
     */
    public void abort() {
        if (phase == Phase.PUBLISHED || phase == Phase.ABORTED) {
            return;
        }
        scheduler.releaseAdmission(domain);
        if (phase != Phase.NOT_STARTED) {
            domain.transferOwnership();
        }
        phase = Phase.ABORTED;
    }

    private void requirePhase(Phase expected, String operation) {
        if (phase != expected) {
            throw new IllegalStateException("Migration step '" + operation
                    + "' refused for '" + domain.name() + "': phase is "
                    + phase + ", expected " + expected);
        }
    }
}
