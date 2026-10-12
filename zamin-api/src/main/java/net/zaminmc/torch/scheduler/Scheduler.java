package net.zaminmc.torch.scheduler;

/**
 * The public task controls (§27.3's scheduler family): the time dimension
 * over the engine's owner-context execution.
 *
 * <p>Every task body runs on the simulation owner's context — the same
 * lane the engine's own gameplay work rides (the documented owner path
 * is {@code World.execute(runnable)} for immediate submission; this
 * interface adds the delay and the repeat). Submissions are safe from
 * any thread; a task that becomes due runs exactly once per due tick,
 * before the world's simulation step, in submission order inside the
 * same due tick.</p>
 *
 * <p>Failure isolation follows the engine's own tick policy: one
 * throwing body is logged and skipped, the loop never dies, and a
 * repeating task still re-arms.</p>
 */
public interface Scheduler {

    /**
     * Runs the body once at the start of the next tick, on the owner's
     * context. Equivalent to {@code runLater(1, work)}.
     *
     * @return the cancellation handle
     */
    Task runNextTick(Runnable work);

    /**
     * Runs the body once, {@code delayTicks} drains from now (a delay of
     * 1 is the next tick; 0 behaves as 1).
     *
     * @return the cancellation handle
     */
    Task runLater(int delayTicks, Runnable work);

    /**
     * Runs the body first after {@code delayTicks} drains, then every
     * {@code intervalTicks} drains, until cancelled. An interval below 1
     * behaves as 1.
     *
     * @return the cancellation handle
     */
    Task runRepeating(int delayTicks, int intervalTicks, Runnable work);
}
