package net.zaminmc.torch.server.concurrent;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.concurrent.CrossOwner;
import net.zaminmc.torch.server.world.EngineWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The public cross-owner view (§28.2's default nonblocking API): the
 * engine's own {@link CrossOwnerRouter} dressed as the {@link CrossOwner}
 * interface. Every submission resolves the target position's owning
 * domain (the region domain of a partitioned world — the same domain the
 * tick-head boundary walk drains; the world's own domain otherwise), wraps
 * the plugin's action in the completion-aware guard (the cancel gate, the
 * deadline gate, the failure isolation), and submits through the router's
 * exactly-once admission. The target's drain applies it in the target's
 * own context — the same walk the engine's internal region intents ride.
 */
public final class CrossOwnerView implements CrossOwner {

    /** The public surface's own source marker (the diagnostics row). */
    private static final String SOURCE_DOMAIN = "cross-owner-api";

    private final CrossOwnerRouter router;
    private final List<QueuedHandle<?>> live = new ArrayList<>();
    private final AtomicLong appliedCount = new AtomicLong();
    private final AtomicLong failedCount = new AtomicLong();
    private final AtomicLong cancelledCount = new AtomicLong();
    private final AtomicLong expiredCount = new AtomicLong();
    private final AtomicLong shutdownWalks = new AtomicLong();
    private final AtomicLong lateRefused = new AtomicLong();

    public CrossOwnerView(CrossOwnerRouter router) {
        this.router = Objects.requireNonNull(router, "router");
    }

    @Override
    public Request<Void> submit(World world, BlockPosition position, String operation,
                                Runnable action) {
        Objects.requireNonNull(action, "action");
        return submit(world, position, operation, () -> {
            action.run();
            return null;
        }, null, Long.MAX_VALUE);
    }

    @Override
    public <R> Request<R> submit(World world, BlockPosition position, String operation,
                                 Supplier<R> action, BiConsumer<R, Throwable> completion) {
        return submit(world, position, operation, action, completion, Long.MAX_VALUE);
    }

    @Override
    public <R> Request<R> submit(World world, BlockPosition position, String operation,
                                 Supplier<R> action, BiConsumer<R, Throwable> completion,
                                 long deadlineTick) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(action, "action");
        if (!(world instanceof EngineWorld engineWorld)) {
            throw new IllegalArgumentException(
                    "The cross-owner surface targets engine worlds (got "
                            + world.getClass().getName() + ")");
        }
        // The routing: the position's region domain (the partitioned
        // world's own lane — the drain the tick-head boundary walk runs),
        // or the unpartitioned world's own domain (the single lane).
        OwnershipDomain target = engineWorld.regionDomainFor(position);
        if (target == null) {
            target = engineWorld.domain();
            if (target == null) {
                throw new IllegalStateException(
                        "The target world '" + engineWorld.name()
                                + "' has no ownership domain wired (pre-boot)");
            }
        }
        QueuedHandle<R> handle = new QueuedHandle<>(this, completion);
        Runnable guard = () -> {
            // The cancel gate: a cancelled request skips without running
            // (the router's in-flight window still clears in the drain's
            // own finally — the skip is harmless and exactly-once holds).
            if (!handle.beginApply()) {
                forget(handle);
                return;
            }
            try {
                // The deadline gate: the target world's own tick clock at
                // drain time — past the deadline the request expires
                // without running (the stale-result law, §28.2).
                if (engineWorld.totalTicks() > deadlineTick) {
                    handle.completeExpired();
                    expiredCount.incrementAndGet();
                    return;
                }
                R result;
                try {
                    result = action.get();
                } catch (Throwable t) {
                    handle.completeFailed(t);
                    return;
                }
                handle.completeApplied(result);
                appliedCount.incrementAndGet();
            } finally {
                forget(handle);
            }
        };
        // Register before admission so a same-instant drain can never
        // outrun the live list (the guard's forget would race a missing
        // entry otherwise).
        register(handle);
        try {
            router.submit(new CrossOwnerRouter.Intent(target, UUID.randomUUID(),
                    SOURCE_DOMAIN, 0L, operation, guard));
        } catch (IllegalStateException refused) {
            handle.completeRefused();
            forget(handle);
            throw refused;
        }
        return handle;
    }

    /** Tracks a live handle for the shutdown cancellation walk. */
    private void register(QueuedHandle<?> handle) {
        synchronized (live) {
            if (shutdownWalks.get() > 0) {
                // The shutdown walk already ran: this late submission dies
                // immediately — the router's own post-shutdown refusal
                // would fire beside it; this is the belt to that.
                if (handle.completeCancelled()) {
                    cancelledCount.incrementAndGet();
                }
                lateRefused.incrementAndGet();
                return;
            }
            live.add(handle);
        }
    }

    /** Retires a terminal handle from the live list (bounded memory). */
    private void forget(QueuedHandle<?> handle) {
        synchronized (live) {
            live.remove(handle);
        }
    }

    /**
     * The engine-shutdown walk (§28.5 — outstanding work is invalidated,
     * not delivered): every still-queued request flips CANCELLED without
     * its completion running. Idempotent; the router's own shutdown
     * clears the queues beside this.
     */
    public void shutdown() {
        List<QueuedHandle<?>> snapshot;
        synchronized (live) {
            if (shutdownWalks.incrementAndGet() != 1) {
                return;
            }
            snapshot = new ArrayList<>(live);
            live.clear();
        }
        for (QueuedHandle<?> handle : snapshot) {
            if (handle.completeCancelled()) {
                cancelledCount.incrementAndGet();
            }
        }
    }

    @Override
    public long submittedCount() {
        return router.submittedCount();
    }

    @Override
    public long refusedCount() {
        return router.refusedCount();
    }

    @Override
    public long failedCount() {
        // The guard isolates every user-action throw before the router
        // sees it — the view's own count is the honest one.
        return failedCount.get();
    }

    @Override
    public long appliedCount() {
        return appliedCount.get();
    }

    @Override
    public long cancelledCount() {
        return cancelledCount.get();
    }

    @Override
    public long expiredCount() {
        return expiredCount.get();
    }

    /**
     * The request handle's state machine: QUEUED → (APPLIED | FAILED |
     * CANCELLED | EXPIRED | REFUSED), with the internal APPLYING phase
     * between QUEUED and the outcome — a request being applied at this
     * instant reports {@code QUEUED} until its outcome publishes (the
     * drain thread is the only writer past APPLYING).
     */
    private static final class QueuedHandle<R> implements Request<R> {

        private enum Phase { QUEUED, APPLYING, APPLIED, FAILED, CANCELLED, EXPIRED, REFUSED }

        private final CrossOwnerView owner;
        private final BiConsumer<R, Throwable> completion;
        private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.QUEUED);
        private volatile R result;
        private volatile Throwable failure;

        QueuedHandle(CrossOwnerView owner, BiConsumer<R, Throwable> completion) {
            this.owner = owner;
            this.completion = completion;
        }

        @Override
        public State state() {
            Phase now = phase.get();
            return switch (now) {
                case QUEUED, APPLYING -> State.QUEUED;
                case APPLIED -> State.APPLIED;
                case FAILED -> State.FAILED;
                case CANCELLED -> State.CANCELLED;
                case EXPIRED -> State.EXPIRED;
                case REFUSED -> State.REFUSED;
            };
        }

        @Override
        public boolean isDone() {
            Phase now = phase.get();
            return now != Phase.QUEUED && now != Phase.APPLYING;
        }

        @Override
        public boolean cancel() {
            if (phase.compareAndSet(Phase.QUEUED, Phase.CANCELLED)) {
                owner.cancelledCount.incrementAndGet();
                return true;
            }
            return false;
        }

        @Override
        public Optional<R> resultNow() {
            return phase.get() == Phase.APPLIED ? Optional.ofNullable(result) : Optional.empty();
        }

        @Override
        public Throwable failure() {
            return phase.get() == Phase.FAILED ? failure : null;
        }

        /**
         * The drain's entry gate: wins only while QUEUED — a cancelled
         * request never runs its action.
         */
        boolean beginApply() {
            return phase.compareAndSet(Phase.QUEUED, Phase.APPLYING);
        }

        void completeApplied(R value) {
            // Publish the result BEFORE the state flip (the volatile
            // write orders the read side — resultNow never sees a stale
            // null over APPLIED).
            result = value;
            phase.set(Phase.APPLIED);
            if (completion != null) {
                completion.accept(value, null);
            }
        }

        void completeFailed(Throwable t) {
            failure = t;
            phase.set(Phase.FAILED);
            owner.failedCount.incrementAndGet();
            if (completion != null) {
                completion.accept(null, t);
            }
        }

        void completeExpired() {
            phase.set(Phase.EXPIRED);
        }

        /** @return true when this call flipped a QUEUED request. */
        boolean completeCancelled() {
            if (phase.compareAndSet(Phase.QUEUED, Phase.CANCELLED)) {
                return true;
            }
            return false;
        }

        void completeRefused() {
            phase.set(Phase.REFUSED);
        }
    }
}
