package net.zaminmc.torch.server.concurrent;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * The logical authority over a defined set of mutable live state (the design
 * doc's ownership domain — {@code docs/CONCURRENCY_ARCHITECTURE.md} §2). A
 * worker is not an owner: the domain binds <em>dynamically</em> to whatever
 * executor thread runs its task, and every binding carries a monotonic
 * generation so references captured before a transfer are diagnosably stale
 * (the design's invariant 10 and the Folia-evidenced EntityScheduler
 * re-validation, {@code docs/FOLIA_FORENSIC_AUDIT.md} §4-B).
 *
 * <p>Enforcement (the design's §15): a mutation outside the bound context
 * fails with a diagnosable {@link OwnershipViolationException} carrying the
 * domain and the operation, never silent corruption. In DIAGNOSTIC mode the
 * violation logs once per operation and continues — the rollout's fallback
 * if a strict assertion ever changes live behavior.</p>
 *
 * <p>Threading: {@link #enter()} claims the current thread as the domain's
 * bound executor (exactly one at a time); the single-writer tick gate
 * ({@link #tryBeginTick()}) enforces one active simulation task per domain.
 * This class is the Phase 1 primitive — it does not schedule, it validates.</p>
 */
public final class OwnershipDomain {

    private static final Logger LOGGER = Logger.getLogger(OwnershipDomain.class.getName());

    /** What a context violation does: fail loudly, or log and continue. */
    public enum Enforcement {
        /** The violation throws {@link OwnershipViolationException}. Default. */
        STRICT,
        /** The violation logs once per operation and the call proceeds. */
        DIAGNOSTIC
    }

    private final String name;
    private final AtomicLong generation = new AtomicLong(0);
    private final AtomicReference<Thread> boundExecutor = new AtomicReference<>();
    /** The binding depth: nested enters on the bound thread count once. */
    private final java.util.concurrent.atomic.AtomicInteger depth =
            new java.util.concurrent.atomic.AtomicInteger(0);
    /** The single-writer gate: one active simulation task per domain. */
    private final AtomicBoolean ticking = new AtomicBoolean(false);
    private volatile Enforcement enforcement = Enforcement.STRICT;
    /** The DIAGNOSTIC once-per-operation log dampener. */
    private final AtomicReference<java.util.Set<String>> loggedViolations =
            new AtomicReference<>(java.util.Collections.emptySet());

    private OwnershipDomain(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    /** Creates a named domain at generation 0. */
    public static OwnershipDomain create(String name) {
        return new OwnershipDomain(name);
    }

    /** The domain's diagnostic identity ("simulation:world"). */
    public String name() {
        return name;
    }

    /** The current ownership generation; changes only through {@link #transferOwnership()}. */
    public long generation() {
        return generation.get();
    }

    /**
     * The single-writer ownership transfer: bumps the generation and returns
     * the old one. Compute results captured under the old generation are
     * rejected by their tickets (the stale-result rule, design §6-A).
     */
    public long transferOwnership() {
        return generation.getAndIncrement();
    }

    /**
     * Binds the current thread as the domain's executing worker. Exactly one
     * binding at a time — a second concurrent enter fails. Nesting on the
     * same thread (a task stepping into a helper that re-enters) counts once:
     * the binding releases only when the outermost exit pairs it.
     *
     * @throws OwnershipViolationException when another thread holds the binding
     */
    public void enter() {
        Thread self = Thread.currentThread();
        if (boundExecutor.compareAndSet(null, self)) {
            depth.set(1);
            return;
        }
        Thread holder = boundExecutor.get();
        if (holder == self) {
            depth.incrementAndGet(); // nested entry
            return;
        }
        throw new OwnershipViolationException(name, "enter",
                holder == null ? "unbound" : holder.getName(), self.getName());
    }

    /** Releases the binding; the thread must be the one that entered, and
     * the outermost exit pairs the whole nesting. */
    public void exit() {
        Thread self = Thread.currentThread();
        if (boundExecutor.get() != self) {
            Thread holder = boundExecutor.get();
            throw new OwnershipViolationException(name, "exit",
                    holder == null ? "unbound" : holder.getName(), self.getName());
        }
        if (depth.decrementAndGet() == 0) {
            boundExecutor.compareAndSet(self, null);
        }
    }

    /** @return whether the current thread is the domain's bound executor. */
    public boolean inContext() {
        return boundExecutor.get() == Thread.currentThread();
    }

    /**
     * The enforcement assertion for every mutation path: the caller must run
     * in the domain's bound context. STRICT throws; DIAGNOSTIC logs once per
     * operation and proceeds.
     */
    public void checkInContext(String operation) {
        if (inContext()) {
            return;
        }
        Thread self = Thread.currentThread();
        OwnershipViolationException violation = new OwnershipViolationException(
                name, operation,
                boundExecutor.get() == null ? "unbound" : boundExecutor.get().getName(),
                self.getName());
        if (enforcement == Enforcement.STRICT) {
            throw violation;
        }
        logOnce(operation, violation);
    }

    private void logOnce(String operation, OwnershipViolationException violation) {
        java.util.Set<String> current = loggedViolations.get();
        if (current.contains(operation)) {
            return;
        }
        java.util.Set<String> updated = new java.util.HashSet<>(current);
        updated.add(operation);
        loggedViolations.compareAndSet(current, java.util.Collections.unmodifiableSet(updated));
        LOGGER.warning(violation.getMessage());
    }

    /** Switches the enforcement mode (tests and the rollout's fallback switch). */
    public void setEnforcement(Enforcement enforcement) {
        this.enforcement = Objects.requireNonNull(enforcement, "enforcement");
    }

    /** @return the active enforcement mode. */
    public Enforcement enforcement() {
        return enforcement;
    }

    /**
     * The single-writer gate: claims the domain's one active simulation task
     * slot. A second concurrent claim fails — the design's invariant that one
     * domain never executes two simulation tasks at once.
     *
     * @return true when claimed; false when another task holds the slot
     */
    public boolean tryBeginTick() {
        return ticking.compareAndSet(false, true);
    }

    /** Releases the simulation task slot; the caller must hold it. */
    public void endTick() {
        if (!ticking.compareAndSet(true, false)) {
            throw new OwnershipViolationException(name, "endTick",
                    "no active task", Thread.currentThread().getName());
        }
    }

    /** @return whether a simulation task currently holds the slot. */
    public boolean ticking() {
        return ticking.get();
    }

    @Override
    public String toString() {
        return "OwnershipDomain[" + name + " gen=" + generation.get()
                + " bound=" + (boundExecutor.get() == null ? "none" : boundExecutor.get().getName())
                + "]";
    }
}
