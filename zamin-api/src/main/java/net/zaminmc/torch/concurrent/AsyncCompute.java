package net.zaminmc.torch.concurrent;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The safe asynchronous-computation surface (§27.3's compute family,
 * the §27.6/§28.3 contract): CPU-bound pure calculations run on the
 * engine's BOUNDED compute pool over immutable inputs, and their
 * results return to the authoritative simulation owner for validation
 * and publication — never applied on the pool's own thread.
 *
 * <p><b>The contract (§27.3's parallel-compute rules):</b></p>
 * <ul>
 *   <li>The input must be immutable (or effectively so) — the pool
 *       never receives live authoritative state.</li>
 *   <li>The compute function must not touch engine state: no world
 *       reads, no entity mutation — it is a pure calculation over the
 *       input.</li>
 *   <li>The result delivers at the head of a near tick on the
 *       simulation owner's validated context: {@code onFresh} runs
 *       there (the documented owner path — mutations inside it are
 *       legal), with the generation the computation was submitted
 *       against.</li>
 *   <li>A result computed against superseded state never applies:
 *       {@code onStale} runs instead (same context) — the generation
 *       validation is the engine's own.</li>
 *   <li>Admission is bounded (backpressure): a full queue refuses the
 *       submission with an explicit exception rather than growing
 *       unboundedly.</li>
 * </ul>
 *
 * <p>Submissions are safe from any thread. A handle is not a
 * synchronization point: never block on it (§28.2 — the drain owns
 * delivery).</p>
 */
public interface AsyncCompute {

    /**
     * Submits one pure computation.
     *
     * @param input    the immutable input (must not carry live engine state)
     * @param compute  the pure function (runs on the bounded pool)
     * @param onFresh  receives (result, generation) on the simulation
     *                 owner's context when the result is still fresh —
     *                 mutations are legal inside it
     * @param onStale  runs on the same context when the result was
     *                 computed against superseded state — it must not
     *                 apply anything
     * @param <I>      the input type
     * @param <R>      the result type
     * @return the lifecycle handle (cancellable while queued; never a
     *         blocking promise)
     */
    <I, R> Computation<R> submit(I input, Function<I, R> compute,
                                 BiConsumer<R, Long> onFresh, Runnable onStale);

    /** @return the submissions admitted so far (diagnostics, any thread). */
    long submittedCount();

    /** @return the submissions refused by the admission bound so far. */
    long rejectedCount();

    /** @return the computations that threw on the pool so far. */
    long failedCount();

    /** @return the queued-but-not-yet-picked computations right now. */
    int queuedCount();

    /** One submitted computation's lifecycle handle (§27.3). */
    interface Computation<R> {

        /** The simulation-owner generation the computation was submitted against. */
        long generation();

        /** @return true once the computation reached a terminal state. */
        boolean isDone();

        /** @return true when the computation was cancelled before running. */
        boolean isCancelled();

        /** @return true when the compute function threw (the failure never applies). */
        boolean isFailed();

        /** The failure a failed computation threw (diagnostics; never applied). */
        Throwable failure();

        /**
         * Cancels a queued computation. A running computation cannot be
         * cancelled — it completes and its result flows through the usual
         * fresh/stale validation at the owner's drain.
         *
         * @return true when the computation will never run
         */
        boolean tryCancel();
    }
}
