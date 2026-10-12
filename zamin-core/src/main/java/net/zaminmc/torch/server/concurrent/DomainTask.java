package net.zaminmc.torch.server.concurrent;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A simulation task bound to one {@link OwnershipDomain}: the run acquires
 * the domain's single-writer slot and the executor binding, executes, and
 * releases both — the domain's one-active-task invariant is enforced by the
 * gate, not by discipline (the design's §5 scheduling model and §15
 * enforcement).
 *
 * <p>Cancellation (the design's §11 test list): a cancelled task never
 * resurrects — its {@link #run()} is a no-op after {@link #cancel()}, and a
 * cancellation cannot interrupt a run already past its start (the in-flight
 * task completes to its scheduling boundary; {@link #cancel()} refuses).
 * This is the primitive the later scheduler phases build delayed and
 * repeating work on.</p>
 */
public final class DomainTask implements Runnable {

    private enum State { READY, RUNNING, DONE, CANCELLED }

    private final OwnershipDomain domain;
    private final Runnable delegate;
    private final AtomicReference<State> state = new AtomicReference<>(State.READY);

    public DomainTask(OwnershipDomain domain, Runnable delegate) {
        this.domain = Objects.requireNonNull(domain, "domain");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /** @return the domain this task mutates within. */
    public OwnershipDomain domain() {
        return domain;
    }

    /**
     * Cancels the task while it is still queued. A task already running or
     * finished cannot be cancelled (the in-flight run completes; a done task
     * has nothing to cancel).
     *
     * @return true when the task will never run
     */
    public boolean cancel() {
        return state.compareAndSet(State.READY, State.CANCELLED);
    }

    /** @return READY before a run, RUNNING while executing, DONE/CANCELLED after. */
    public boolean isCancelled() {
        return state.get() == State.CANCELLED;
    }

    /** @return whether the task completed its run (not cancelled). */
    public boolean isDone() {
        State current = state.get();
        return current == State.DONE || current == State.CANCELLED;
    }

    /**
     * Runs the delegated work inside the domain's context and single-writer
     * slot. A cancelled task no-ops (no resurrection). The slot acquisition
     * failure (another task running) surfaces as
     * {@link OwnershipViolationException} — the caller's scheduling layer
     * decides retry or drop; this class never queues.
     */
    @Override
    public void run() {
        if (isCancelled()) {
            return; // a cancelled task never resurrects
        }
        if (!state.compareAndSet(State.READY, State.RUNNING)) {
            return; // already running or finished: not re-entrant
        }
        try {
            if (!domain.tryBeginTick()) {
                throw new OwnershipViolationException(domain.name(),
                        "begin simulation task", "one active task",
                        Thread.currentThread().getName());
            }
            try {
                domain.enter();
                try {
                    delegate.run();
                } finally {
                    domain.exit();
                }
            } finally {
                domain.endTick();
            }
        } finally {
            state.set(State.DONE);
        }
    }
}
