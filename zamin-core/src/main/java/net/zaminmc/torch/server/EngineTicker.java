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
    /**
     * The keep-up warn law's thresholds, 1:1 the reference's own
     * (MinecraftServer lines 396-401): a >2000ms lateness warns, at most
     * once per 15000ms, with the reference's own line.
     */
    private static final long KEEP_UP_WARN_NANOS = TimeUnit.MILLISECONDS.toNanos(2000);
    private static final long KEEP_UP_CADENCE_NANOS = TimeUnit.MILLISECONDS.toNanos(15000);
    /**
     * The keep-up warn law's cadence anchor (the reference's
     * {@code lastWarnTime}, MinecraftServer line 117): the NEXT-TICK stamp
     * of the last warn; 0 means "never warned" (the origin-independent
     * difference makes the first >2s lateness always warn, the reference's
     * own epoch-0 law).
     */
    private long lastWarnNextTickNanos;
    /**
     * The tick watchdog's heartbeat (ML-013): the nanoTime stamp of the
     * tick currently in flight, published right before the tick body runs
     * (the reference's {@code this.nextTickTime = k} immediately before
     * {@code this.tick()}, MinecraftServer lines 410-412). 0 while no tick
     * has ever started — the watchdog's idle gate. The stamp survives the
     * park window (the reference's own envelope: nextTickTime holds the
     * previous tick's value through the sleep).
     */
    private volatile long currentTickStartNanos;

    private volatile EngineWorld world;
    /**
     * The second dimension (8b-ii): the nether world walks the same tick —
     * its time advances after the overworld's, its mutations assert through
     * its own domain (the boot binds both for the loop's whole run). Null
     * keeps the single-dimension behavior (the historical contract).
     */
    private volatile EngineWorld netherWorld;
    /**
     * The third dimension (Wave FL4): the End world walks the same tick —
     * its clock advances after the nether's (the reference's every-world
     * tickTime; the End's own sky reports a fixed day, the CLOCK still
     * walks). The End's scheduled-tick due math reads it — the pre-FL4
     * engine never advanced it, so every End scheduled row sat undated
     * forever (the End carried no scheduled families, so nothing noticed).
     */
    private volatile EngineWorld endWorld;
    private volatile Runnable tickHandler;
    /**
     * The {@code doDaylightCycle} read (Slice 24a — the shared game-rule
     * table's day-clock gate; both dimensions read the SAME rule, the
     * reference's derived-data delegation). Null keeps the historical
     * always-advance behavior (the default-true rule's own view).
     */
    private volatile java.util.function.BooleanSupplier daylightCycleRead;
    private volatile net.zaminmc.torch.server.concurrent.SimulationScheduler scheduler;
    private volatile net.zaminmc.torch.server.concurrent.OwnershipDomain schedulerDomain;
    private volatile net.zaminmc.torch.server.concurrent.ComputeSubsystem compute;
    private volatile net.zaminmc.torch.server.concurrent.OwnershipDomain computeDomain;
    private volatile net.zaminmc.torch.server.concurrent.DomainWorkerPool domainPool;
    private volatile net.zaminmc.torch.server.concurrent.CrossOwnerRouter crossOwnerRouter;
    private volatile net.zaminmc.torch.server.concurrent.OwnershipDomain routerDomain;
    private volatile net.zaminmc.torch.server.concurrent.BlockingIoLane ioLane;
    private volatile net.zaminmc.torch.server.concurrent.OwnershipDomain ioLaneDomain;
    private long logicalTick;
    /**
     * The completed-tick snapshot the public scheduler reads from any
     * thread to compute due times (§27.3): submissions see the LAST
     * COMPLETED tick, so a delay of N lands at the Nth-next drain.
     */
    private volatile long completedTickView;
    /**
     * The public scheduler's due-time queue: due tick -> tasks, drained at
     * the head of every tick on the owner's context. Submissions from any
     * thread; one throwing body is logged and skipped (the tick's own
     * policy), repeating bodies re-arm past the completed tick.
     */
    private final java.util.concurrent.ConcurrentSkipListMap<Long,
            java.util.concurrent.ConcurrentLinkedQueue<
                    net.zaminmc.torch.server.scheduler.EngineScheduledTask>> scheduledTasks =
            new java.util.concurrent.ConcurrentSkipListMap<>();
    private final long tickIntervalNanos;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Queue<Runnable> pendingWork = new ConcurrentLinkedQueue<>();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private volatile Thread tickThread;
    private volatile long lastTickOverrunNanos;
    // The /tps window (the Paper 1m/5m/15m report): ticks completed per
    // elapsed second, the last fifteen minutes kept so every reported
    // window is real data (ML-004's sibling fix ML-025: the window once
    // held only sixty seconds, so the 5m/15m columns printed the 1m
    // average under Math.min clamps — fiction, now retired).
    private final java.util.concurrent.ConcurrentLinkedQueue<long[]> tpsWindow =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private long tpsSecondStamp;
    private long tpsSecondTicks;
    private static final long TPS_SECOND_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final int TPS_WINDOW_SECONDS = 900;

    EngineTicker(int tickRateHz) {
        this.tickIntervalNanos = TimeUnit.SECONDS.toNanos(1) / tickRateHz;
    }

    /**
     * Attaches the {@code doDaylightCycle} read (the game-rule table's
     * day-clock gate — Slice 24a). Called during boot wiring.
     */
    public void setDaylightCycleRead(java.util.function.BooleanSupplier read) {
        this.daylightCycleRead = java.util.Objects.requireNonNull(read, "read");
    }

    /** Attaches the world this ticker owns. Called once, on the tick thread, before the loop starts. */
    public void attachWorld(EngineWorld world) {
        if (this.world != null) {
            throw new IllegalStateException("World already attached");
        }
        this.world = java.util.Objects.requireNonNull(world, "world");
    }

    /**
     * Attaches the nether dimension (8b-ii). Called once, on the tick
     * thread, before the loop starts — the same binding window as the
     * overworld's attach.
     */
    public void attachNetherWorld(EngineWorld nether) {
        if (this.netherWorld != null) {
            throw new IllegalStateException("Nether world already attached");
        }
        this.netherWorld = java.util.Objects.requireNonNull(nether, "nether");
    }

    /** @return the attached nether dimension, or null when single-dimension. */
    public EngineWorld netherWorld() {
        return netherWorld;
    }

    /**
     * Attaches the End dimension (Wave FL4) — the same binding window as
     * the nether's: once, on the tick thread, before the loop starts.
     */
    public void attachEndWorld(EngineWorld end) {
        if (this.endWorld != null) {
            throw new IllegalStateException("End world already attached");
        }
        this.endWorld = java.util.Objects.requireNonNull(end, "end");
    }

    /** @return the attached End dimension, or null when two-dimension. */
    public EngineWorld endWorld() {
        return endWorld;
    }

    /**
     * Attaches the simulation scheduler (the permanent architecture's Phase
     * 2 wiring): submitted work routes through the bounded, ordered,
     * cancellable substrate and each tick runs its due tasks. The scheduler
     * is optional — a null-attached ticker keeps the legacy queue.
     */
    public void attachScheduler(net.zaminmc.torch.server.concurrent.SimulationScheduler attached,
                                net.zaminmc.torch.server.concurrent.OwnershipDomain domain) {
        this.scheduler = java.util.Objects.requireNonNull(attached, "attached");
        this.schedulerDomain = java.util.Objects.requireNonNull(domain, "domain");
    }

    /**
     * Attaches the compute subsystem (the permanent architecture's Phase 3):
     * each tick drains the completed compute outcomes at the owner's
     * boundary — fresh results apply inside the domain's context, stale
     * results run their registered fallback. Optional; null keeps the
     * legacy no-drain behavior.
     */
    public void attachCompute(net.zaminmc.torch.server.concurrent.ComputeSubsystem attached,
                              net.zaminmc.torch.server.concurrent.OwnershipDomain domain) {
        this.compute = java.util.Objects.requireNonNull(attached, "attached");
        this.computeDomain = java.util.Objects.requireNonNull(domain, "domain");
    }

    /**
     * Attaches the blocking-I/O lane (the §28.1 fourth category — the
     * storage lane): each tick drains the completed blocking tasks at the
     * owner's boundary after the compute drain — fresh completions apply
     * inside the domain's context, stale ones run their registered
     * fallback. Optional; null keeps the no-lane behavior.
     */
    public void attachIoLane(net.zaminmc.torch.server.concurrent.BlockingIoLane attached,
                             net.zaminmc.torch.server.concurrent.OwnershipDomain domain) {
        this.ioLane = java.util.Objects.requireNonNull(attached, "attached");
        this.ioLaneDomain = java.util.Objects.requireNonNull(domain, "domain");
    }

    /**
     * Attaches the domain worker pool (the permanent architecture's Phase 4):
     * the scheduler's due walk dispatches the domains' drains to the pool and
     * the independent domains progress concurrently, joined at the tick edge
     * before the time advance. Optional; null keeps the serial walk (today's
     * single-thread semantics, identical gameplay).
     */
    public void attachDomainPool(net.zaminmc.torch.server.concurrent.DomainWorkerPool pool) {
        this.domainPool = java.util.Objects.requireNonNull(pool, "pool");
    }

    /**
     * Attaches the cross-owner router (the permanent architecture's Phase 4
     * §7 protocol): each tick drains the domain's inbound intents at the
     * owner boundary — the target-side apply the design's sequence names —
     * after the scheduler walk and before the compute drain, so an applied
     * intent is visible to the same tick's authoritative work. Optional;
     * null keeps the no-router behavior.
     */
    public void attachCrossOwnerRouter(net.zaminmc.torch.server.concurrent.CrossOwnerRouter router,
                                       net.zaminmc.torch.server.concurrent.OwnershipDomain domain) {
        this.crossOwnerRouter = java.util.Objects.requireNonNull(router, "router");
        this.routerDomain = java.util.Objects.requireNonNull(domain, "domain");
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
                // The reference's keep-up law, 1:1 (MinecraftServer lines
                // 396-401): a lateness past two full ticks warns — at most
                // once per fifteen seconds — with the reference's own line
                // (the pre-clamp j and j/50). The 2000ms band keeps the
                // boot-warmup norm silent all by itself.
                if (shouldWarnKeepUp(overrun, nextTick)) {
                    long behindMillis = TimeUnit.NANOSECONDS.toMillis(overrun);
                    LOGGER.warning(() -> "Can't keep up! Did the system time change, "
                            + "or is the server overloaded? Running " + behindMillis
                            + "ms behind, skipping " + (behindMillis / 50) + " tick(s)");
                }
                nextTick = now; // snap forward instead of accumulating debt
            }
            currentTickStartNanos = now;
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

    /**
     * Enqueues work to run at the start of the next tick. Safe from any
     * thread. With the scheduler attached the submission rides the bounded
     * admission (a refusal throws — the design's explicit backpressure);
     * otherwise the legacy unbounded queue.
     */
    public void submit(Runnable work) {
        net.zaminmc.torch.server.concurrent.SimulationScheduler attached = scheduler;
        net.zaminmc.torch.server.concurrent.OwnershipDomain domain = schedulerDomain;
        if (attached != null && domain != null) {
            attached.submit(domain, work);
        } else {
            pendingWork.add(work);
        }
    }

    /** One completed tick feeds the /tps rolling window. Tick thread only. */
    private void noteTpsTick() {
        long now = System.nanoTime();
        if (tpsSecondStamp == 0) {
            tpsSecondStamp = now;
        }
        tpsSecondTicks++;
        if (now - tpsSecondStamp >= TPS_SECOND_NANOS) {
            appendTpsSecond(tpsSecondStamp, tpsSecondTicks);
            tpsSecondStamp = now;
            tpsSecondTicks = 0;
        }
    }

    /**
     * Records one completed second (stamp, ticks) into the rolling window,
     * evicting past the retained span. Package-private: the window math's
     * own test drives synthetic seconds through here (ML-025's acceptance —
     * a 900-second history cannot be lived out in a unit test).
     */
    void appendTpsSecond(long stampNanos, long tickCount) {
        tpsWindow.add(new long[]{stampNanos, tickCount});
        while (tpsWindow.size() > TPS_WINDOW_SECONDS) {
            tpsWindow.poll();
        }
    }

    /**
     * @return the number of per-second entries currently retained (bounded
     * by the fifteen-minute window). Package-private for the window math's
     * own retention test.
     */
    int tpsWindowRetainedSeconds() {
        return tpsWindow.size();
    }

    /**
     * @return the average ticks per second over roughly the given window
     * seconds (1, 5 or 15 in the Paper report); 20.0 before enough data.
     * The window itself retains fifteen minutes so every reported span is
     * real history.
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
        // ticks/span is long-over-long — WITHOUT the double cast this
        // truncates to a whole number and every /tps column reads x.0
        // (the second half of ML-025, found by the window math's own test).
        return Math.min(20.0, ticks / (double) span);
    }

    /**
     * Sets the per-tick simulation hook (runs after deferred work, once per tick,
     * on the owning thread). Used for entity systems (§447); the world's time
     * tick remains unconditional.
     */
    public void setTickHandler(Runnable handler) {
        this.tickHandler = Objects.requireNonNull(handler, "handler");
    }

    /**
     * Schedules a body through the public scheduler's due-time queue
     * (§27.3): a delay of N drains from the last completed tick, so the
     * body runs at the Nth-next drain on the owner's context. An interval
     * above 0 re-arms the body every interval drains until cancelled.
     * Safe from any thread.
     *
     * @return the cancellation handle
     */
    public net.zaminmc.torch.scheduler.Task scheduleTask(int delayTicks, int intervalTicks,
                                                          Runnable body) {
        Objects.requireNonNull(body, "body");
        long due = completedTickView + Math.max(1, delayTicks);
        net.zaminmc.torch.server.scheduler.EngineScheduledTask task =
                new net.zaminmc.torch.server.scheduler.EngineScheduledTask(
                        due, intervalTicks, body);
        scheduledTasks.computeIfAbsent(due,
                        key -> new java.util.concurrent.ConcurrentLinkedQueue<>())
                .add(task);
        return task;
    }

    /**
     * The due-time drain: every task whose due tick is at or before the
     * in-flight tick runs once on this (the owner's) context, in due-then-
     * submission order; repeating bodies re-arm past the in-flight tick.
     * One throwing body is logged and skipped — the tick's own policy.
     * Tick thread only.
     */
    private void drainScheduledTasks(long inFlightTick) {
        while (!scheduledTasks.isEmpty() && scheduledTasks.firstKey() <= inFlightTick) {
            java.util.concurrent.ConcurrentLinkedQueue<
                    net.zaminmc.torch.server.scheduler.EngineScheduledTask> due =
                    scheduledTasks.pollFirstEntry().getValue();
            net.zaminmc.torch.server.scheduler.EngineScheduledTask task;
            while ((task = due.poll()) != null) {
                if (task.cancelled()) {
                    continue;
                }
                try {
                    task.run();
                } catch (Throwable t) {
                    LOGGER.log(java.util.logging.Level.SEVERE,
                            "Scheduled task failed; skipping", t);
                }
                if (task.repeating() && !task.cancelled()) {
                    long next = task.rearm(inFlightTick);
                    scheduledTasks.computeIfAbsent(next,
                                    key -> new java.util.concurrent.ConcurrentLinkedQueue<>())
                            .add(task);
                }
            }
        }
    }

    /** Package-private: the test surface drives one tick directly. */
    void tickOnce() {
        try {
            // The public scheduler's drain (§27.3): the tick now starting is
            // the completed view + 1; every task due at or before it runs on
            // this context, a repeating body re-arms past the same anchor.
            drainScheduledTasks(completedTickView + 1);
            Runnable work;
            while ((work = pendingWork.poll()) != null) {
                work.run();
            }
            // The Phase 2 substrate: due delayed tasks move to ready, then
            // every ready task runs — FIFO inside the domain, one active
            // task per domain gate, failures skipping one task (the same
            // policy the legacy drain rides).
            net.zaminmc.torch.server.concurrent.SimulationScheduler attached = scheduler;
            if (attached != null) {
                net.zaminmc.torch.server.concurrent.DomainWorkerPool pool = domainPool;
                if (pool != null) {
                    attached.runDueParallel(logicalTick, pool);
                } else {
                    attached.runDue(logicalTick);
                }
            }
            // The cross-owner drain rides the same owner boundary (the §7
            // protocol's target-side apply: intents validate and apply in
            // the target's context, admission order, exactly-once).
            net.zaminmc.torch.server.concurrent.CrossOwnerRouter router = crossOwnerRouter;
            net.zaminmc.torch.server.concurrent.OwnershipDomain routerOwner = routerDomain;
            if (router != null && routerOwner != null) {
                router.drainIntents(routerOwner);
            }
            // The compute drain rides the same owner boundary (the design's
            // sequence: results validate and apply at the tick edge, the
            // owner never waits on the pool).
            net.zaminmc.torch.server.concurrent.ComputeSubsystem computeAttached = compute;
            net.zaminmc.torch.server.concurrent.OwnershipDomain computeOwner = computeDomain;
            if (computeAttached != null && computeOwner != null) {
                computeAttached.drainResults(computeOwner);
            }
            // The blocking-I/O drain rides the same owner boundary after the
            // compute drain (the §28.1 storage lane's defined handoff — the
            // completions of slow file/network calls apply at the tick edge
            // with their generation validated; the owner never waits for them).
            net.zaminmc.torch.server.concurrent.BlockingIoLane ioLaneAttached = ioLane;
            net.zaminmc.torch.server.concurrent.OwnershipDomain ioOwner = ioLaneDomain;
            if (ioLaneAttached != null && ioOwner != null) {
                ioLaneAttached.drainCompletions(ioOwner);
            }
            logicalTick++;
            completedTickView = logicalTick;
            // The day clock walks only under doDaylightCycle (Slice 24a —
            // ServerWorld.tick lines 190-192); the total clock always
            // walks. One shared read: every dimension's rules delegate to
            // the same table.
            java.util.function.BooleanSupplier daylightRead = daylightCycleRead;
            boolean advanceDaylight = daylightRead == null || daylightRead.getAsBoolean();
            world.tickTime(advanceDaylight);
            EngineWorld nether = netherWorld;
            if (nether != null) {
                nether.tickTime(advanceDaylight);
            }
            EngineWorld end = endWorld;
            if (end != null) {
                end.tickTime(advanceDaylight);
            }
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

    /**
     * The §28.7 main-thread check's engine-side answer (the §27.6
     * integration interface the Bukkit/Spigot/Paper compatibility adapter
     * consumes): whether the current thread is the simulation loop's own
     * tick thread — the thread a legacy {@code isPrimaryThread}-style
     * assumption is really asking about. The answer is a THREAD fact, not
     * an ownership claim: a domain worker running an owner's due work
     * holds the owner's authority (§6.1) but is NOT this thread, and the
     * adapter reports that honestly. Safe from any thread; never waits.
     */
    public boolean isOwnerThread() {
        Thread owner = tickThread;
        return owner != null && owner == Thread.currentThread();
    }

    long lastTickOverrunNanos() {
        return lastTickOverrunNanos;
    }

    /**
     * The tick watchdog's heartbeat read (ML-013): the nanoTime stamp of
     * the tick currently in flight, or 0 while no tick has ever started
     * (the idle gate — the reference's watchdog reads the same window
     * through {@code getNextTickTime()}).
     */
    long currentTickStartNanos() {
        return currentTickStartNanos;
    }

    /**
     * Whether the loop is still running — the watchdog's own loop
    * condition (the reference's {@code this.server.isRunning()}).
     */
    boolean isLoopRunning() {
        return running.get();
    }

    /**
     * The tick interval in milliseconds (the watchdog's "should be max"
     * line — the reference's hardcoded 0.05 at the 20 TPS default).
     */
    double tickIntervalMillis() {
        return tickIntervalNanos / 1_000_000.0;
    }

    /**
     * The keep-up warn decision, the reference's law 1:1 (MinecraftServer
     * lines 396-401): {@code j > 2000L && nextTickTime - lastWarnTime >=
     * 15000L} — a lateness past two full ticks warns, at most once per
     * fifteen seconds (the anchor is the lateness's own next-tick stamp,
     * exactly the reference's {@code this.lastWarnTime = this.nextTickTime}
     * update, line 401). The first warn is always allowed: the anchor starts
     * at 0, the reference's own epoch-0 law. Tick thread only (the decision
     * both reads and updates the cadence anchor).
     *
     * @param overrunNanos  the lateness measured against the next-tick
     *                      deadline (the reference's j)
     * @param nextTickNanos the next-tick deadline the lateness was measured
     *                      against (the reference's nextTickTime)
     * @return whether the warn line fires for this lateness
     */
    boolean shouldWarnKeepUp(long overrunNanos, long nextTickNanos) {
        if (overrunNanos > KEEP_UP_WARN_NANOS
                && nextTickNanos - lastWarnNextTickNanos >= KEEP_UP_CADENCE_NANOS) {
            lastWarnNextTickNanos = nextTickNanos;
            return true;
        }
        return false;
    }

    /**
     * The last completed tick's number (the watchdog's crash-report row —
     * the reference's populateCrashReport system details carry the same
     * shape).
     */
    long completedTicks() {
        return completedTickView;
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
