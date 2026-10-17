package net.zaminmc.torch.concurrent;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The cross-owner request surface (§28.2's default nonblocking API —
 * the {@code CrossOwnerRouter}'s public face): a mutation request toward
 * the authoritative owner of a world position travels as an immutable
 * intent, the TARGET validates and applies it in its own context at a
 * tick boundary, and the submitter never blocks.
 *
 * <p><b>The contract (§28.2/§27.6):</b></p>
 * <ul>
 *   <li><b>Never blocks.</b> Submission is safe from any thread and any
 *       execution context; it never waits for the target owner, another
 *       owner, or a simulation worker (§28.2's no-hidden-RPC rule). A
 *       handle is not a synchronization point.</li>
 *   <li><b>The target's authority.</b> The action runs at a tick
 *       boundary inside the target position's owning domain (the
 *       region domain of a partitioned world, the world's own domain
 *       otherwise), in admission order, exactly once per request. The
 *       action may mutate state owned by that target — that is its
 *       purpose.</li>
 *   <li><b>The completion context is explicit.</b> The completion
 *       consumer runs in the SAME target owner context, immediately
 *       after the action (never on an arbitrary owner, never on the
 *       submitting thread — §28.2).</li>
 *   <li><b>Backpressure is loud.</b> A full target queue refuses the
 *       submission with an explicit exception; the handle reports
 *       {@link Request.State#REFUSED REFUSED}. Never a silent drop.</li>
 *   <li><b>Cancellation is cooperative.</b> {@link Request#cancel()}
 *       wins while the request is still queued; a cancelled request is
 *       skipped at the drain without running the action.</li>
 *   <li><b>Deadlines are meaningful.</b> The {@code deadlineTick}
 *       overload names the last target-world tick on which the request
 *       may still apply; a request drained after that tick expires
 *       without running ({@link Request.State#EXPIRED EXPIRED}).</li>
 *   <li><b>Failures are isolated.</b> A throwing action completes
 *       {@link Request.State#FAILED FAILED} with the throwable; one bad
 *       request never kills the drain, the tick, or neighboring
 *       requests.</li>
 *   <li><b>Exactly once per request.</b> Each submission carries a
 *       fresh operation id (the engine's idempotency key). A retry is a
 *       new request by contract — business-level deduplication is the
 *       caller's own concern.</li>
 *   <li><b>Shutdown cancels.</b> At engine shutdown queued requests are
 *       cancelled ({@code CANCELLED}, no completion callback runs) —
 *       outstanding work is invalidated, not delivered, per §28.5.</li>
 * </ul>
 *
 * <p>The engine's own position-aware ownership assertions govern the
 * action's mutations: a request routed to region R may legally mutate
 * R's state; a mutation outside the target's authority fails through
 * the standard ownership enforcement, exactly as it would on the tick
 * lane.</p>
 */
public interface CrossOwner {

    /**
     * Submits a fire-and-forget mutation request (§28.2's one-way
     * command — the default shape: "no answer needs to come back").
     *
     * @param world     the target world (the position's owner applies)
     * @param position  the target position (resolves the owning domain)
     * @param operation the plugin-facing operation label (diagnostics —
     *                  the failure log names it)
     * @param action    the mutation, applied in the target owner's
     *                  context at a tick boundary
     * @return the request handle (cancellable while queued)
     * @throws IllegalStateException when the target queue is full (the
     *         loud refusal) or the router is shut down
     */
    Request<Void> submit(World world, BlockPosition position, String operation,
                         Runnable action);

    /**
     * Submits a request carrying its answer back: the action computes a
     * result in the target owner's context and the completion receives
     * it in the SAME context, immediately after (the documented
     * completion contract — §28.2).
     *
     * @param world      the target world
     * @param position   the target position
     * @param operation  the operation label (diagnostics)
     * @param action     the computation/mutation, applied in the target
     *                   owner's context at a tick boundary
     * @param completion receives {@code (result, null)} on success or
     *                   {@code (null, failure)} when the action threw —
     *                   runs in the target owner's context; it must not
     *                   block
     * @param <R>        the result type (immutable or effectively so —
     *                   it crosses back to the caller's reads)
     * @return the request handle
     * @throws IllegalStateException when the target queue is full or the
     *         router is shut down
     */
    <R> Request<R> submit(World world, BlockPosition position, String operation,
                          Supplier<R> action, BiConsumer<R, Throwable> completion);

    /**
     * The deadline form: the request may still apply on the target
     * world's tick {@code deadlineTick}; a request drained after that
     * tick expires without running the action.
     *
     * @param deadlineTick the last target-world tick on which the
     *                     request applies (read against
     *                     {@link World#totalTicks()} at drain time)
     * @see #submit(World, BlockPosition, String, Supplier, BiConsumer)
     */
    <R> Request<R> submit(World world, BlockPosition position, String operation,
                          Supplier<R> action, BiConsumer<R, Throwable> completion,
                          long deadlineTick);

    /** @return the requests admitted so far (diagnostics, any thread). */
    long submittedCount();

    /** @return the submissions refused by the admission bound so far. */
    long refusedCount();

    /** @return the requests whose action applied successfully so far. */
    long appliedCount();

    /** @return the requests whose action threw so far. */
    long failedCount();

    /** @return the requests cancelled before their drain so far. */
    long cancelledCount();

    /** @return the requests expired past their deadline so far. */
    long expiredCount();

    /**
     * One submitted request's lifecycle handle. Never a blocking
     * promise: the state flips when the target's drain reaches it.
     */
    interface Request<R> {

        /** The terminal outcome of the request. */
        enum State {
            /** Admitted, waiting for the target's drain. */
            QUEUED,
            /** The action applied (the result is visible). */
            APPLIED,
            /** The action threw; the failure carries the throwable. */
            FAILED,
            /** Cancelled before the drain ran it. */
            CANCELLED,
            /** The deadline passed before the drain applied it. */
            EXPIRED,
            /** The admission refused the submission (the loud refusal). */
            REFUSED
        }

        /** @return the current (terminal or in-flight) state. */
        State state();

        /** @return true once the request reached a terminal state. */
        boolean isDone();

        /**
         * Cancels the request while it is still queued. The cancel wins
         * only before the drain runs the action.
         *
         * @return true when this call cancelled the request; false when
         *         it already reached another terminal state
         */
        boolean cancel();

        /**
         * The result peek (never blocks): present once the request
         * applied; empty in every other state.
         */
        Optional<R> resultNow();

        /** @return the action's throwable once FAILED; null otherwise. */
        Throwable failure();
    }
}
