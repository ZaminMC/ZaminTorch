package net.zaminmc.torch.server;

import net.zaminmc.torch.World;

import net.zaminmc.torch.server.world.EngineWorld;

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
    // The /tps window (the Paper 1m/5m/15m report): ticks completed per
    // elapsed second, the last sixty seconds kept for the 1-minute average.
    private final java.util.concurrent.ConcurrentLinkedQueue<long[]> tpsWindow =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private long tpsSecondStamp;
    private long tpsSecondTicks;
    private static final long TPS_SECOND_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final int TPS_WINDOW_SECONDS = 60;

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
            noteTpsTick();
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

    /** One completed tick feeds the /tps rolling window. Tick thread only. */
    private void noteTpsTick() {
        long now = System.nanoTime();
        if (tpsSecondStamp == 0) {
            tpsSecondStamp = now;
        }
        tpsSecondTicks++;
        if (now - tpsSecondStamp >= TPS_SECOND_NANOS) {
            tpsWindow.add(new long[]{tpsSecondStamp, tpsSecondTicks});
            tpsSecondStamp = now;
            tpsSecondTicks = 0;
            while (tpsWindow.size() > TPS_WINDOW_SECONDS) {
                tpsWindow.poll();
            }
        }
    }

    /**
     * @return the average ticks per second over roughly the given window
     * seconds (1, 5 or 15 in the Paper report); 20.0 before enough data.
     */
    public double averageTps(int seconds) {
        long now = System.nanoTime();
        long from = now - TimeUnit.SECONDS.toNanos(seconds);
        long ticks = 0;
        long span = 0;
        for (long[] second : tpsWindow) {
            if (second[0] >= from) {
                ticks += second[1];
                span++;
            }
        }
        if (tpsSecondStamp >= from && tpsSecondStamp != 0) {
            ticks += tpsSecondTicks;
            span += (now - tpsSecondStamp) / (double) TPS_SECOND_NANOS;
        }
        if (span <= 0) {
            return 20.0;
        }
        return Math.min(20.0, ticks / span);
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
