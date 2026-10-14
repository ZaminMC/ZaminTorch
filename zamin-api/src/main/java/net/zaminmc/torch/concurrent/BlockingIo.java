package net.zaminmc.torch.concurrent;

import java.util.concurrent.Callable;

/**
 * The blocking-I/O execution surface (§28.1's fourth category — the
 * "storage lane"): database, filesystem, HTTP, and other potentially
 * blocking work runs on the engine's own BOUNDED I/O pool — never on a
 * simulation worker, never on the compute pool — and every completion
 * returns to the authoritative simulation owner through a defined
 * handoff (§28.2: no hidden blocking wait anywhere; the owner never parks
 * for the lane).
 *
 * <p><b>The contract (§28.1 category 4 / §28.3):</b></p>
 * <ul>
 *   <li>The work runs OUTSIDE the simulation workers: a reference is not
 *       permission — the callable must not mutate engine state; it may
 *       only read immutable inputs it closed over and produce a
 *       result.</li>
 *   <li>The completion delivers at the head of a near tick on the
 *       simulation owner's validated context: {@code onFresh} runs there
 *       (the documented owner path — mutations inside it are legal),
 *       with the generation the task was submitted against.</li>
 *   <li>A result computed against superseded state never applies:
 *       {@code onStale} runs instead (same context) — the generation
 *       validation is the engine's own.</li>
 *   <li>Admission is bounded (backpressure): a full queue refuses the
 *       submission with an explicit exception rather than growing
 *       unboundedly.</li>
 *   <li>The lane asks blocking work to be interruptible: a shutdown
 *       interrupts in-flight calls; a task that swallows interruption
 *       simply completes and flows through the drain.</li>
 * </ul>
 *
 * <p>Submissions are safe from any thread. A handle is not a
 * synchronization point: never block on it (§28.2 — the drain owns
 * delivery).</p>
 */
public interface BlockingIo {

    /**
     * Submits one blocking task.
     *
     * @param operation the diagnostic name ("save-chunk 12,34")
     * @param work      the blocking call (runs on the bounded I/O pool;
     *                  interruptible by contract)
     * @param onFresh   receives (result, generation) on the simulation
     *                  owner's context when the result is still fresh —
     *                  mutations are legal inside it
     * @param onStale   runs on the same context when the result was
     *                  computed against superseded state — it must not
     *                  apply anything
     * @param <R>       the result type
     * @return the lifecycle handle (cancellable while queued; never a
     *         blocking promise)
     */
    <R> Task<R> submit(String operation, Callable<R> work,
                       java.util.function.BiConsumer<R, Long> onFresh, Runnable onStale);

    /** @return the tasks admitted so far (diagnostics, any thread). */
    long submittedCount();

    /** @return the tasks refused by the admission bound so far. */
    long rejectedCount();

    /** @return the tasks that threw on the pool so far. */
    long failedCount();

    /** @return the queued-but-not-yet-picked tasks right now. */
    int queuedCount();

    /** One submitted blocking task's lifecycle handle (§28.1 category 4). */
    interface Task<R> {

        /** The simulation-owner generation the task was submitted against. */
        long generation();

        /** @return true once the task reached a terminal state. */
        boolean isDone();

        /** @return true when the task was cancelled before running. */
        boolean isCancelled();

        /** @return true when the work call threw (the failure never applies). */
        boolean isFailed();

        /** The failure a failed task threw (diagnostics; never applied). */
        Throwable failure();

        /**
         * Cancels a queued task. A running task cannot be cancelled — it
         * completes (or fails) and its result flows through the usual
         * fresh/stale validation at the owner's drain.
         *
         * @return true when the task will never run
         */
        boolean tryCancel();
    }
}
