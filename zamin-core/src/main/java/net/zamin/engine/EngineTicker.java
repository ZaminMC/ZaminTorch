package net.zamin.engine;

import net.zamin.engine.world.EngineWorld;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * The simulation loop. Slice #1 policy (deliberate):
 *
 * <ul>
 *   <li>Fixed-rate ticks; no burst catch-up. If the server falls behind, missed
 *       ticks are skipped and the backlog is logged — executing thousands of
 *       overdue ticks instantly is never the answer.</li>
 *   <li>World state is owned by this thread (enforced by the world itself).</li>
 *   <li>Engine-initiated deferred work can be enqueued from any thread through
 *       the work queue; it runs at the start of the next tick.</li>
 * </ul>
 */
public final class EngineTicker {

    private static final Logger LOGGER = Logger.getLogger(EngineTicker.class.getName());
    /** Overruns below this stay silent: they are the boot-warmup norm. */
    private static final long WARN_OVERRUN_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

    private volatile EngineWorld world;
    private volatile Runnable tickHandler;
    private final long tickIntervalNanos;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Queue<Runnable> pendingWork = new ConcurrentLinkedQueue<>();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private volatile Thread tickThread;
    private volatile long lastTickOverrunNanos;
    private boolean tickedOnce;

    EngineTicker(int tickRateHz) {
        this.tickIntervalNanos = TimeUnit.SECONDS.toNanos(1) / tickRateHz;
    }

    /** Attaches the world this ticker owns. Called once, on the tick thread, before the loop starts. */
    public void attachWorld(EngineWorld world) {
        if (this.world != null) {
            throw new IllegalStateException("World already attached");
        }
        this.world = java.util.Objects.requireNonNull(world, "world");
    }

    /** Starts ticking; the current thread becomes the world's owner. */
    void runLoop() {
        Thread.currentThread().setName("zamin-tick");
        tickThread = Thread.currentThread();
        running.set(true);
        LOGGER.fine("Simulation loop started");
        long nextTick = System.nanoTime();
        while (running.get()) {
            long now = System.nanoTime();
            if (now + tickIntervalNanos / 2 < nextTick) {
                // Sleep until slightly before the next tick to avoid drift-by-schedule.
                parkUntil(nextTick - tickIntervalNanos / 2);
                continue;
            }
            if (now - nextTick > tickIntervalNanos) {
                final long overrun = now - nextTick;
                lastTickOverrunNanos = overrun;
                // Sub-quarter-second overruns are the boot-warmup norm (class
                // loading, world pregeneration): warning on them spams the
                // console without telling the operator anything actionable.
                if (tickedOnce && overrun > WARN_OVERRUN_NANOS) {
                    LOGGER.warning(() -> "Simulation fell behind by "
                            + TimeUnit.NANOSECONDS.toMillis(overrun)
                            + " ms; skipping missed ticks (no burst catch-up)");
                }
                nextTick = now; // snap forward instead of accumulating debt
            }
            tickedOnce = true;
            tickOnce();
            nextTick += tickIntervalNanos;
        }
        LOGGER.fine("Simulation loop stopped");
        stopped.countDown();
    }

    void stop() {
        running.set(false);
    }

    boolean awaitStop(long timeoutMillis) throws InterruptedException {
        return stopped.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /** Enqueues work to run at the start of the next tick. Safe from any thread. */
    public void submit(Runnable work) {
        pendingWork.add(work);
    }

    /**
     * Sets the per-tick simulation hook (runs after deferred work, once per tick,
     * on the owning thread). Used for entity systems (§447); the world's time
     * tick remains unconditional.
     */
    public void setTickHandler(Runnable handler) {
        this.tickHandler = Objects.requireNonNull(handler, "handler");
    }

    private void tickOnce() {
        try {
            Runnable work;
            while ((work = pendingWork.poll()) != null) {
                work.run();
            }
            world.tickTime();
            Runnable handler = tickHandler;
            if (handler != null) {
                handler.run();
            }
        } catch (Throwable t) {
            // One failing tick must never kill the simulation (§126): log loudly,
            // keep the loop alive, and continue with the next tick.
            LOGGER.log(java.util.logging.Level.SEVERE, "Tick execution failed; skipping tick", t);
        }
    }

    Thread ownerThread() {
        return tickThread;
    }

    long lastTickOverrunNanos() {
        return lastTickOverrunNanos;
    }

    private static void parkUntil(long deadlineNanos) {
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0) {
            return;
        }
        try {
            TimeUnit.NANOSECONDS.sleep(remaining);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
