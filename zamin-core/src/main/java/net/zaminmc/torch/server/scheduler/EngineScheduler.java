package net.zaminmc.torch.server.scheduler;

import net.zaminmc.torch.scheduler.Scheduler;
import net.zaminmc.torch.scheduler.Task;
import net.zaminmc.torch.server.EngineTicker;

import java.util.Objects;

/**
 * The public {@link Scheduler} view over the ticker's due-time queue
 * (§27.3's task controls): every submission — immediate, delayed or
 * repeating — rides the same due-time queue drained at the head of
 * every tick on the owner's context, in due-then-submission order.
 * (The engine's own internal submissions keep their separate admission
 * lane; {@code World.execute} remains the documented immediate path.)
 */
public final class EngineScheduler implements Scheduler {

    private final EngineTicker ticker;

    public EngineScheduler(EngineTicker ticker) {
        this.ticker = Objects.requireNonNull(ticker, "ticker");
    }

    @Override
    public Task runNextTick(Runnable work) {
        return runLater(1, work);
    }

    @Override
    public Task runLater(int delayTicks, Runnable work) {
        Objects.requireNonNull(work, "work");
        return ticker.scheduleTask(Math.max(1, delayTicks), 0, work);
    }

    @Override
    public Task runRepeating(int delayTicks, int intervalTicks, Runnable work) {
        Objects.requireNonNull(work, "work");
        return ticker.scheduleTask(Math.max(1, delayTicks), Math.max(1, intervalTicks), work);
    }
}
