package net.zaminmc.torch.plugin;

import net.zaminmc.torch.concurrent.AsyncCompute;
import net.zaminmc.torch.concurrent.BlockingIo;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A plugin's managed execution surface (architecture §28.6: Torch-managed
 * bounded lanes with per-plugin quotas, so one plugin cannot monopolize
 * shared capacity and overload stays observable).
 *
 * <p>Every submission routes through the engine's own bounded lanes —
 * the compute pool for pure CPU work (§28.1 category 3), the blocking
 * I/O pool for potentially blocking work (category 4) — with a
 * per-plugin in-flight quota in front. A quota refusal is an operation
 * result (§28.3's own law), not an error: the caller learns it through
 * the empty answer and the {@link #refusedCount()} counter.</p>
 *
 * <p><b>Lifecycle:</b> submissions are refused once the plugin's scope
 * closed (or the disable walk began — §28.5's admissions gate). A
 * submission that was admitted before the close still completes on its
 * lane, but its callbacks are dropped (the late-result invalidation —
 * a running callback is not proof of authority).</p>
 */
public interface PluginTasks {

    /**
     * One pure computation on the engine's bounded compute pool,
     * admitted against the plugin's quota.
     *
     * @return the lifecycle handle, or empty when the plugin's quota is
     *         exhausted or its scope closed (the refusal is the result)
     */
    <I, R> Optional<AsyncCompute.Computation<R>> compute(
            I input, Function<I, R> compute,
            BiConsumer<R, Long> onFresh, Runnable onStale);

    /**
     * One blocking operation on the engine's bounded I/O pool (never a
     * simulation worker — §28.1's fourth category), admitted against
     * the plugin's quota.
     *
     * @return the lifecycle handle, or empty when the quota is
     *         exhausted or the scope closed
     */
    <R> Optional<BlockingIo.Task<R>> io(
            String operation, Callable<R> work,
            BiConsumer<R, Long> onFresh, Runnable onStale);

    /** @return the plugin's in-flight quota (diagnostics, any thread). */
    int maxInFlight();

    /** @return the plugin's admitted-but-undelivered submissions right now. */
    int inFlight();

    /** @return the submissions the quota refused so far. */
    long refusedCount();

    /**
     * @return the admitted submissions whose callbacks were dropped
     * because the plugin's scope closed before delivery (§28.5's
     * late-result invalidation, observable — §28.9's rejection counts).
     */
    long droppedLateCount();
}
