package net.zaminmc.torch.server.scheduler;

import net.zaminmc.torch.scheduler.Task;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One scheduled body inside the ticker's due-time queue (§27.3's task
 * controls): the due tick, the repeat interval (0 = one-shot), the
 * cancellation flag and the body. Instances are handed to the ticker's
 * queue from any thread and drained on the tick thread.
 */
public final class EngineScheduledTask implements Task {

    private final Runnable body;
    /** 0 = one-shot; otherwise the re-arm stride in ticks. */
    private final int intervalTicks;
    /** The drain this body is queued for (the queue's own key). */
    private long dueTick;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public EngineScheduledTask(long dueTick, int intervalTicks, Runnable body) {
        this.dueTick = dueTick;
        this.intervalTicks = Math.max(0, intervalTicks);
        this.body = body;
    }

    long dueTick() {
        return dueTick;
    }

    /** The re-arm's next due drain (only meaningful when repeating). */
    public long rearm(long completedTick) {
        this.dueTick = completedTick + Math.max(1, intervalTicks);
        return dueTick;
    }

    public boolean repeating() {
        return intervalTicks > 0;
    }

    /** Runs the body; the ticker's own failure policy wraps the call. */
    public void run() {
        body.run();
    }

    @Override
    public boolean cancelled() {
        return cancelled.get();
    }

    @Override
    public void cancel() {
        cancelled.set(true);
    }
}
