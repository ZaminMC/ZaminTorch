package net.zaminmc.torch.bukkit.scheduler;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One bridged task's handle: the legacy identity over the engine's own
 * cancellation surface. Safe from any thread; cancelling a completed task
 * is the harmless no-op (the engine's own law).
 */
final class TorchBukkitTask implements BukkitTask {

    private final int id;
    private final Plugin owner;
    private final boolean sync;
    /** The sync driver / the async beat driver on the engine scheduler. */
    private final net.zaminmc.torch.scheduler.Task driver;
    /** The async body's blocking-lane submission (null for sync tasks). */
    private final TorchSchedulerBridge.AtomicIoSubmission submission;
    /** The sync body's is-currently-running window. */
    private final AtomicBoolean syncRunningNow;
    private final Runnable onTerminal;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean removed = new AtomicBoolean(false);

    TorchBukkitTask(int id, Plugin owner, boolean sync,
            net.zaminmc.torch.scheduler.Task driver,
            TorchSchedulerBridge.AtomicIoSubmission submission,
            AtomicBoolean syncRunningNow, Runnable onTerminal) {
        this.id = id;
        this.owner = owner;
        this.sync = sync;
        this.driver = driver;
        this.submission = submission;
        this.syncRunningNow = syncRunningNow;
        this.onTerminal = onTerminal;
    }

    @Override
    public int getTaskId() {
        return id;
    }

    @Override
    public Plugin getOwner() {
        return owner;
    }

    @Override
    public boolean isSync() {
        return sync;
    }

    @Override
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            if (driver != null) {
                driver.cancel(); // the engine's own handle (queued never runs)
            }
            if (submission != null) {
                submission.tryCancel(); // while still queued on the lane
            }
            if (removed.compareAndSet(false, true)) {
                onTerminal.run();
            }
        }
    }

    boolean cancelled() {
        return cancelled.get();
    }

    /** The honest running window: the sync body on the owner context, the async body on the lane. */
    boolean isRunningNow() {
        if (syncRunningNow != null) {
            return syncRunningNow.get();
        }
        return submission != null && submission.running().get();
    }
}
