package net.zaminmc.torch.bukkit.scheduler;

import net.zaminmc.torch.bukkit.BukkitAdapter;
import net.zaminmc.torch.plugin.PluginHandle;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitWorker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * The scheduler bridge (§28.7's inventory: synchronous task scheduling and
 * repeating-task order, asynchronous task restrictions, plugin disable
 * cancellation and task cleanup) — routed onto the engine's OWN structures,
 * never a second scheduler:
 *
 * <ul>
 *   <li><b>Synchronous tasks (runTask / runTaskLater / runTaskTimer):</b>
 *       the engine's public §27.3 scheduler — the ticker's own due-time
 *       queue drained at the head of every tick on the owner's context.
 *       The legacy ordering is the engine's: FIFO inside one due tick, in
 *       submission order, stable periods (§28.7's "traditional ordering
 *       semantics" preserved by construction).</li>
 *   <li><b>Asynchronous tasks (runTaskAsynchronously and variants):</b>
 *       the owning plugin's §28.6 managed lane — the per-plugin quota'd
 *       blocking-I/O view over the engine's own bounded lane (never a
 *       simulated worker thread — §28.1's fourth category; the body owns
 *       no mutation authority, §28.3). Delayed/periodic async bodies ride
 *       a tick-aligned driver on the engine scheduler whose beats submit
 *       to the lane: the period comes from the engine's owner clock (the
 *       §28.7 documented translation — the legacy wall-clock pool is not
 *       reproduced, deliberately).</li>
 *   <li><b>Every task is scope-owned (§28.5):</b> the cancel verb is
 *       admitted into the owning plugin's CURRENT engine resource scope —
 *       the disable sweep cancels every pending task and invalidates every
 *       late result, however the plugin ends.</li>
 * </ul>
 *
 * <p><b>The honest arms:</b> {@link #getActiveWorkers()} answers an empty
 * list (the engine's blocking-I/O lane owns its threads; the adapter never
 * fabricates worker identities — §28.7), and {@link #isCurrentlyRunning}
 * reports the live wrapper's in-flight flag (a sync body is "running" only
 * inside its own callback on the owner context — the honest window).</p>
 */
public final class TorchSchedulerBridge implements BukkitScheduler {

    private final BukkitAdapter adapter;
    private final AtomicInteger nextId = new AtomicInteger(1);
    /** The registry: every task the bridge admitted (any thread). */
    private final Map<Integer, TorchBukkitTask> tasks = new ConcurrentHashMap<>();

    public TorchSchedulerBridge(BukkitAdapter adapter) {
        this.adapter = adapter;
    }

    // ------------------------------------------------------------------ the runTask family (BukkitTask handles)

    @Override
    public BukkitTask runTask(Plugin plugin, Runnable task) throws IllegalArgumentException {
        return scheduleSync(plugin, 1, 0, task);
    }

    @Override
    public BukkitTask runTask(Plugin plugin, BukkitRunnable task) throws IllegalArgumentException {
        return runTask(plugin, (Runnable) task);
    }

    @Override
    public BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay)
            throws IllegalArgumentException {
        return scheduleSync(plugin, Math.max(1, delay), 0, task);
    }

    @Override
    public BukkitTask runTaskLater(Plugin plugin, BukkitRunnable task, long delay)
            throws IllegalArgumentException {
        return runTaskLater(plugin, (Runnable) task, delay);
    }

    @Override
    public BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period)
            throws IllegalArgumentException {
        if (period < 1) {
            throw new IllegalArgumentException("period must be at least 1 tick");
        }
        return scheduleSync(plugin, Math.max(1, delay), period, task);
    }

    @Override
    public BukkitTask runTaskTimer(Plugin plugin, BukkitRunnable task, long delay, long period)
            throws IllegalArgumentException {
        return runTaskTimer(plugin, (Runnable) task, delay, period);
    }

    // ------------------------------------------------------------------ the async family

    @Override
    public BukkitTask runTaskAsynchronously(Plugin plugin, Runnable task)
            throws IllegalArgumentException {
        return scheduleAsyncImmediate(plugin, task);
    }

    @Override
    public BukkitTask runTaskAsynchronously(Plugin plugin, BukkitRunnable task)
            throws IllegalArgumentException {
        return runTaskAsynchronously(plugin, (Runnable) task);
    }

    @Override
    public BukkitTask runTaskLaterAsynchronously(Plugin plugin, Runnable task, long delay)
            throws IllegalArgumentException {
        return scheduleAsyncDelayed(plugin, Math.max(1, delay), task);
    }

    @Override
    public BukkitTask runTaskLaterAsynchronously(Plugin plugin, BukkitRunnable task, long delay)
            throws IllegalArgumentException {
        return runTaskLaterAsynchronously(plugin, (Runnable) task, delay);
    }

    @Override
    public BukkitTask runTaskTimerAsynchronously(Plugin plugin, Runnable task, long delay,
            long period) throws IllegalArgumentException {
        if (period < 1) {
            throw new IllegalArgumentException("period must be at least 1 tick");
        }
        return scheduleAsyncRepeating(plugin, Math.max(1, delay), period, task);
    }

    @Override
    public BukkitTask runTaskTimerAsynchronously(Plugin plugin, BukkitRunnable task, long delay,
            long period) throws IllegalArgumentException {
        return runTaskTimerAsynchronously(plugin, (Runnable) task, delay, period);
    }

    // ------------------------------------------------------------------ the legacy int forms (CraftBukkit's -1 contract)

    @Override
    public int scheduleSyncDelayedTask(Plugin plugin, Runnable task, long delay) {
        return idOrNull(runTaskLater(plugin, task, delay));
    }

    @Override
    public int scheduleSyncDelayedTask(Plugin plugin, BukkitRunnable task, long delay) {
        return idOrNull(runTaskLater(plugin, task, delay));
    }

    @Override
    public int scheduleSyncDelayedTask(Plugin plugin, Runnable task) {
        return idOrNull(runTask(plugin, task));
    }

    @Override
    public int scheduleSyncDelayedTask(Plugin plugin, BukkitRunnable task) {
        return idOrNull(runTask(plugin, task));
    }

    @Override
    public int scheduleSyncRepeatingTask(Plugin plugin, Runnable task, long delay, long period) {
        return idOrNull(runTaskTimer(plugin, task, delay, period));
    }

    @Override
    public int scheduleSyncRepeatingTask(Plugin plugin, BukkitRunnable task, long delay,
            long period) {
        return idOrNull(runTaskTimer(plugin, task, delay, period));
    }

    @Override
    public int scheduleAsyncDelayedTask(Plugin plugin, Runnable task, long delay) {
        return idOrNull(runTaskLaterAsynchronously(plugin, task, delay));
    }

    @Override
    public int scheduleAsyncDelayedTask(Plugin plugin, Runnable task) {
        return idOrNull(runTaskAsynchronously(plugin, task));
    }

    @Override
    public int scheduleAsyncRepeatingTask(Plugin plugin, Runnable task, long delay, long period) {
        return idOrNull(runTaskTimerAsynchronously(plugin, task, delay, period));
    }

    @Override
    public <T> Future<T> callSyncMethod(Plugin plugin, Callable<T> task) {
        // The §28.5 admission law: a disabled plugin submits nothing — the
        // future fails loudly instead of hanging (§28.2's no-hidden-wait).
        PluginHandle handle = nativeHandle(plugin);
        if (handle == null || handle.resources().closed()) {
            CompletableFuture<T> refused = new CompletableFuture<>();
            refused.completeExceptionally(new IllegalStateException(
                    "plugin " + plugin.getName() + " is disabled — no sync method admitted"));
            return refused;
        }
        CompletableFuture<T> answer = new CompletableFuture<>();
        net.zaminmc.torch.scheduler.Task driver = adapter.engine().scheduler().runNextTick(
                () -> {
                    try {
                        answer.complete(task.call());
                    } catch (Exception failure) {
                        answer.completeExceptionally(failure);
                    }
                });
        ownCancel(handle, driver::cancel);
        return answer;
    }

    // ------------------------------------------------------------------ the sync core

    private TorchBukkitTask scheduleSync(Plugin plugin, long delay, long period, Runnable body) {
        requireLive(plugin, body);
        PluginHandle handle = nativeHandle(plugin);
        AtomicBoolean runningNow = new AtomicBoolean(false);
        Runnable wrapped = () -> {
            // The isCurrentlyRunning window: set for exactly the body's own
            // execution on the owner context (the honest window — §28.7).
            runningNow.set(true);
            try {
                body.run();
            } finally {
                runningNow.set(false);
            }
        };
        net.zaminmc.torch.scheduler.Task engineTask = period > 0
                ? adapter.engine().scheduler().runRepeating((int) delay, (int) period, wrapped)
                : adapter.engine().scheduler().runLater((int) delay, wrapped);
        return register(plugin, handle, engineTask, null, true, runningNow);
    }

    // ------------------------------------------------------------------ the async core

    private TorchBukkitTask scheduleAsyncImmediate(Plugin plugin, Runnable body) {
        requireLive(plugin, body);
        PluginHandle handle = nativeHandle(plugin);
        AtomicIoSubmission submission = new AtomicIoSubmission();
        submitToLane(plugin, handle, body, submission);
        return register(plugin, handle, null, submission, false, null);
    }

    private TorchBukkitTask scheduleAsyncDelayed(Plugin plugin, long delay, Runnable body) {
        requireLive(plugin, body);
        PluginHandle handle = nativeHandle(plugin);
        AtomicIoSubmission submission = new AtomicIoSubmission();
        // The tick-aligned driver: the engine's own scheduler paces the
        // delay; the beat's whole body is one blocking-lane submission.
        net.zaminmc.torch.scheduler.Task driver =
                adapter.engine().scheduler().runLater((int) delay, () ->
                        submitToLane(plugin, handle, body, submission));
        return register(plugin, handle, driver, submission, false, null);
    }

    private TorchBukkitTask scheduleAsyncRepeating(Plugin plugin, long delay, long period,
            Runnable body) {
        requireLive(plugin, body);
        PluginHandle handle = nativeHandle(plugin);
        AtomicIoSubmission submission = new AtomicIoSubmission();
        net.zaminmc.torch.scheduler.Task driver =
                adapter.engine().scheduler().runRepeating((int) delay, (int) period, () ->
                        submitToLane(plugin, handle, body, submission));
        return register(plugin, handle, driver, submission, false, null);
    }

    /**
     * One blocking-lane submission under the plugin's own §28.6 quota: the
     * per-plugin in-flight gate, the scope's late-result invalidation, the
     * engine's bounded lane — never a private thread (§28.1 category 4).
     */
    private void submitToLane(Plugin plugin, PluginHandle handle, Runnable body,
            AtomicIoSubmission submission) {
        if (handle == null || handle.resources().closed()) {
            return; // disabled mid-flight: the admission gate's own refusal
        }
        handle.tasks().io("bukkit-async-task", () -> {
            submission.running().set(true);
            try {
                body.run();
            } finally {
                submission.running().set(false);
                submission.done().set(true);
            }
            return null;
        }, (ignored, generation) -> {
            // The fresh completion applied on the owner context — nothing
            // to carry (the body already ran on the lane).
        }, () -> {
            // Stale (the plugin was disabled while the body ran): the §28.5
            // law — the late result never reaches the plugin.
        }).ifPresent(submission::bind);
    }

    // ------------------------------------------------------------------ the registry + controls

    private TorchBukkitTask register(Plugin plugin, PluginHandle handle,
            net.zaminmc.torch.scheduler.Task driver, AtomicIoSubmission submission,
            boolean sync, AtomicBoolean syncRunningNow) {
        int id = nextId.getAndIncrement();
        TorchBukkitTask task = new TorchBukkitTask(id, plugin, sync, driver, submission,
                syncRunningNow, () -> tasks.remove(id));
        tasks.put(id, task);
        if (handle != null) {
            // §28.5: the task's cancel verb is owned by the plugin's current
            // engine scope — the disable sweep cancels it automatically.
            handle.resources().own(task::cancel);
        }
        return task;
    }

    @Override
    public void cancelTask(int taskId) {
        TorchBukkitTask task = tasks.get(taskId);
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public void cancelTasks(Plugin plugin) {
        for (TorchBukkitTask task : tasks.values()) {
            if (task.getOwner() == plugin) {
                task.cancel();
            }
        }
    }

    @Override
    public void cancelAllTasks() {
        // The bridge's own registry only — never the engine's internal
        // submissions (the adapter owns its tasks, §27.2's boundary).
        for (TorchBukkitTask task : List.copyOf(tasks.values())) {
            task.cancel();
        }
    }

    @Override
    public boolean isCurrentlyRunning(int taskId) {
        TorchBukkitTask task = tasks.get(taskId);
        return task != null && task.isRunningNow();
    }

    @Override
    public boolean isQueued(int taskId) {
        TorchBukkitTask task = tasks.get(taskId);
        return task != null && !task.cancelled();
    }

    @Override
    public List<BukkitWorker> getActiveWorkers() {
        // The honest empty list: the engine's blocking-I/O lane owns its
        // threads and the adapter never fabricates worker identities (§28.7).
        return List.of();
    }

    @Override
    public List<BukkitTask> getPendingTasks() {
        List<BukkitTask> pending = new ArrayList<>();
        for (TorchBukkitTask task : tasks.values()) {
            if (!task.cancelled()) {
                pending.add(task);
            }
        }
        return pending;
    }

    // ------------------------------------------------------------------ helpers

    private int idOrNull(BukkitTask task) {
        return task == null ? -1 : task.getTaskId();
    }

    private void requireLive(Plugin plugin, Runnable task) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin cannot be null");
        }
        if (task == null) {
            throw new IllegalArgumentException("task cannot be null");
        }
        if (!plugin.isEnabled()) {
            throw new IllegalArgumentException(
                    "plugin " + plugin.getName() + " is disabled — no task admitted (§28.5)");
        }
    }

    private PluginHandle nativeHandle(Plugin plugin) {
        return adapter.pluginBridge().nativeHandleOf(plugin);
    }

    private void ownCancel(PluginHandle handle, Runnable cancelVerb) {
        if (handle != null) {
            handle.resources().own(cancelVerb);
        }
    }

    // ------------------------------------------------------------------ the task record

    /** One admitted task's live record: the sync driver and/or the lane submission. */
    static final class AtomicIoSubmission {
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final AtomicBoolean done = new AtomicBoolean(false);
        private volatile java.util.Optional<net.zaminmc.torch.concurrent.BlockingIo.Task<Object>> handle =
                java.util.Optional.empty();

        void bind(net.zaminmc.torch.concurrent.BlockingIo.Task<Object> bound) {
            handle = java.util.Optional.of(bound);
        }

        AtomicBoolean running() {
            return running;
        }

        AtomicBoolean done() {
            return done;
        }

        boolean tryCancel() {
            return handle.map(net.zaminmc.torch.concurrent.BlockingIo.Task::tryCancel)
                    .orElse(false);
        }
    }
}
