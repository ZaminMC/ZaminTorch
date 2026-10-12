package net.zaminmc.torch.scheduler;

/**
 * A handle to work submitted through the {@link Scheduler}: the owner's
 * cancellation surface. The handle is safe from any thread.
 */
public interface Task {

    /** @return whether this task was cancelled (or already completed). */
    boolean cancelled();

    /**
     * Cancels the task: a not-yet-run body never runs; a repeating body
     * stops re-arming. Cancelling a completed task is a harmless no-op.
     * Safe from any thread.
     */
    void cancel();
}
