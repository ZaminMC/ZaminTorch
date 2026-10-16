package net.zaminmc.torch.server.plugin;

import net.zaminmc.torch.concurrent.AsyncCompute;
import net.zaminmc.torch.concurrent.BlockingIo;
import net.zaminmc.torch.plugin.PluginBody;
import net.zaminmc.torch.plugin.PluginDescriptor;
import net.zaminmc.torch.plugin.PluginHandle;
import net.zaminmc.torch.plugin.PluginHost;
import net.zaminmc.torch.plugin.PluginState;
import net.zaminmc.torch.plugin.PluginTasks;
import net.zaminmc.torch.plugin.ResourceScope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The plugin host's engine core (§28.5/§28.6): the lifecycle state
 * machine, the per-plugin resource scopes, and the managed task quotas
 * over the engine's own bounded lanes.
 *
 * <p><b>The disable walk's own law (§28.8 case 6):</b> the admissions
 * gate closes the moment the walk begins (the scope SEALs at DISABLING
 * — new work is refused while {@code onDisable} still runs), the
 * {@code onDisable} callback then runs (ordered cleanup), the scope
 * SWEEPS every owned resource (each close verb isolated — one throwing
 * cleanup never shields the rest), and every not-yet-delivered async
 * callback is dropped by the generation check the wrappers carry. A
 * running computation still completes on its lane (the engine's law —
 * running work cannot be cancelled), but its result never reaches the
 * disabled plugin: cancellation is not proof a callback stopped, and
 * the late-result invalidation is the proof it cannot mutate.</p>
 *
 * <p><b>Threading:</b> the lifecycle verbs run under one control lock
 * (the boot/console context); they must not be called from inside a
 * lifecycle callback (documented reentrancy limit — §28.4's reentrancy
 * law: rejected with a diagnostic, never accidental). The state and
 * scope reads are safe from any thread.</p>
 */
public final class PluginManager implements PluginHost {

    private static final Logger LOGGER = Logger.getLogger(PluginManager.class.getName());

    /** The default per-plugin in-flight quota (§28.6). */
    public static final int DEFAULT_MAX_IN_FLIGHT = 16;

    private final AsyncCompute compute;
    private final BlockingIo io;
    private final int maxInFlight;
    private final Object lifecycleLock = new Object();
    private final Map<String, Plugin> byId = new LinkedHashMap<>();
    private final AtomicLong scopeGenerations = new AtomicLong();

    public PluginManager(AsyncCompute compute, BlockingIo io) {
        this(compute, io, DEFAULT_MAX_IN_FLIGHT);
    }

    public PluginManager(AsyncCompute compute, BlockingIo io, int maxInFlight) {
        this.compute = Objects.requireNonNull(compute, "compute");
        this.io = Objects.requireNonNull(io, "io");
        this.maxInFlight = maxInFlight;
    }

    // ------------------------------------------------------------------ host

    @Override
    public PluginHandle register(PluginDescriptor descriptor, PluginBody body) {
        return registerWithLoader(descriptor, body, null);
    }

    /**
     * The registration the discovery wave rides (Slice 79): the loader's
     * own close verb attaches to the plugin's UNLOAD — the last lifecycle
     * state, after the disable sweep (§28.5's classloading law: the
     * loader stays open across enable/disable cycles, the reload reuses
     * the classes, and only the unload releases them).
     */
    public PluginHandle registerWithLoader(PluginDescriptor descriptor, PluginBody body,
            Runnable closeLoader) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(body, "body");
        Plugin plugin;
        synchronized (lifecycleLock) {
            if (insideLifecycleCallback.get()) {
                throw new IllegalStateException(
                        "a lifecycle callback must not register plugins (§28.4 reentrancy)");
            }
            if (byId.containsKey(descriptor.id())) {
                throw new IllegalStateException("plugin id already registered: "
                        + descriptor.id());
            }
            plugin = new Plugin(descriptor, body);
            if (closeLoader != null) {
                plugin.unloadVerbs.add(closeLoader);
            }
            byId.put(descriptor.id(), plugin);
        }
        enableWalk(plugin);
        return plugin;
    }

    @Override
    public void disable(PluginHandle plugin) {
        Objects.requireNonNull(plugin, "plugin");
        if (!(plugin instanceof Plugin own)) {
            return; // a foreign handle: the documented no-op
        }
        synchronized (lifecycleLock) {
            if (insideLifecycleCallback.get()) {
                throw new IllegalStateException(
                        "a lifecycle callback must not drive the lifecycle (§28.4 reentrancy)");
            }
            if (own.state() != PluginState.ENABLED) {
                return; // the documented no-op
            }
            disableWalk(own);
        }
    }

    @Override
    public void reload(PluginHandle plugin) {
        Objects.requireNonNull(plugin, "plugin");
        if (!(plugin instanceof Plugin own)) {
            return;
        }
        synchronized (lifecycleLock) {
            if (insideLifecycleCallback.get()) {
                throw new IllegalStateException(
                        "a lifecycle callback must not drive the lifecycle (§28.4 reentrancy)");
            }
            if (own.state() == PluginState.ENABLED) {
                disableWalk(own);
            }
            if (own.state() == PluginState.DISABLED) {
                enableWalk(own); // a fresh scope, a fresh generation
            }
        }
    }

    @Override
    public void unload(PluginHandle plugin) {
        Objects.requireNonNull(plugin, "plugin");
        if (!(plugin instanceof Plugin own)) {
            return;
        }
        synchronized (lifecycleLock) {
            if (insideLifecycleCallback.get()) {
                throw new IllegalStateException(
                        "a lifecycle callback must not drive the lifecycle (§28.4 reentrancy)");
            }
            if (own.state() == PluginState.ENABLED) {
                disableWalk(own);
            }
            if (own.state() == PluginState.DISABLED) {
                // The unload's own verbs (Slice 79 — the discovery's loader
                // close): each isolated, a throwing verb never shields the
                // rest, and the classloader's best-effort law is the
                // documented one (§28.5).
                for (Runnable unloadVerb : own.unloadVerbs) {
                    try {
                        unloadVerb.run();
                    } catch (RuntimeException | Error error) {
                        LOGGER.log(Level.SEVERE, "A plugin's unload verb threw — "
                                + "the unload continues", error);
                    }
                }
                own.state.set(PluginState.UNLOADED);
                byId.remove(own.descriptor().id());
            }
        }
    }

    @Override
    public Collection<PluginHandle> plugins() {
        synchronized (lifecycleLock) {
            return List.copyOf(byId.values());
        }
    }

    @Override
    public Optional<PluginHandle> byId(String id) {
        Objects.requireNonNull(id, "id");
        synchronized (lifecycleLock) {
            return Optional.ofNullable(byId.get(id));
        }
    }

    // ------------------------------------------------------------------ walks

    /** The reentrancy guard: a lifecycle callback must not drive the lifecycle. */
    private final AtomicBoolean insideLifecycleCallback = new AtomicBoolean(false);

    /** The enable walk: a FRESH scope, then onEnable (a throw rolls back). */
    private void enableWalk(Plugin plugin) {
        plugin.openScope(new Scope(scopeGenerations.incrementAndGet()));
        plugin.state.set(PluginState.ENABLING);
        boolean enabled = false;
        insideLifecycleCallback.set(true);
        try {
            plugin.body.onEnable(plugin);
            enabled = true;
        } catch (RuntimeException | Error error) {
            LOGGER.log(Level.SEVERE, "Plugin '" + plugin.descriptor().id()
                    + "' failed its enable walk — rolling back to DISABLED", error);
        } finally {
            insideLifecycleCallback.set(false);
        }
        if (enabled) {
            plugin.state.set(PluginState.ENABLED);
        } else {
            // The failure transition: the partial registrations sweep.
            plugin.scope.seal();
            plugin.scope.sweep();
            plugin.state.set(PluginState.DISABLED);
        }
    }

    /** The disable walk: seal (admissions close NOW), onDisable, sweep. */
    private void disableWalk(Plugin plugin) {
        plugin.state.set(PluginState.DISABLING);
        plugin.scope.seal();
        insideLifecycleCallback.set(true);
        try {
            plugin.body.onDisable(plugin);
        } catch (RuntimeException | Error error) {
            LOGGER.log(Level.SEVERE, "Plugin '" + plugin.descriptor().id()
                    + "' threw in its disable walk — the scope sweep continues", error);
        } finally {
            insideLifecycleCallback.set(false);
        }
        plugin.scope.sweep();
        plugin.state.set(PluginState.DISABLED);
    }

    // ------------------------------------------------------------------ plugin

    /** The engine-side plugin: the handle IS the plugin (§27.5's own pattern). */
    private final class Plugin implements PluginHandle {

        final PluginDescriptor descriptor;
        final PluginBody body;
        final Tasks tasks = new Tasks();
        final List<Runnable> unloadVerbs = new ArrayList<>();
        final AtomicReference<PluginState> state = new AtomicReference<>(PluginState.DISCOVERED);
        volatile Scope scope;

        Plugin(PluginDescriptor descriptor, PluginBody body) {
            this.descriptor = descriptor;
            this.body = body;
            this.state.set(PluginState.LOADED);
            this.tasks.bind(this); // safe: this plugin is not published yet
        }

        void openScope(Scope fresh) {
            this.scope = fresh;
        }

        @Override public PluginDescriptor descriptor() {
            return descriptor;
        }

        @Override public PluginState state() {
            return state.get();
        }

        @Override public ResourceScope resources() {
            Scope current = scope;
            if (current == null) {
                // A plugin that never enabled: a sealed empty scope (all
                // admissions refused — the honest answer for DISABLED).
                return Scope.sealedEmpty();
            }
            return current;
        }

        @Override public PluginTasks tasks() {
            return tasks;
        }
    }

    // ------------------------------------------------------------------ scope

    /**
     * One plugin generation's owned-resource ledger: SEAL refuses new
     * admissions (the disable walk's first act), SWEEP runs every close
     * verb (each isolated).
     */
    static final class Scope implements ResourceScope {

        private static final Scope SEALED_EMPTY = buildSealedEmpty();

        private static Scope buildSealedEmpty() {
            Scope scope = new Scope(-1);
            scope.seal();
            scope.sweep();
            return scope;
        }

        static Scope sealedEmpty() {
            return SEALED_EMPTY;
        }

        private final long generation;
        private final List<Runnable> closeVerbs = new ArrayList<>();
        private boolean sealed;
        private boolean swept;
        private int owned;

        Scope(long generation) {
            this.generation = generation;
        }

        @Override public boolean closed() {
            return sealed;
        }

        @Override public int ownedCount() {
            return owned;
        }

        @Override public long generation() {
            return generation;
        }

        @Override public synchronized boolean own(Runnable closeVerb) {
            Objects.requireNonNull(closeVerb, "closeVerb");
            if (sealed) {
                return false; // the admissions gate
            }
            closeVerbs.add(closeVerb);
            owned++;
            return true;
        }

        /** Closes admissions (the disable walk's first act). */
        synchronized void seal() {
            sealed = true;
        }

        /** Runs every close verb (each isolated); idempotent. */
        synchronized void sweep() {
            if (swept) {
                return;
            }
            swept = true;
            for (Runnable closeVerb : closeVerbs) {
                try {
                    closeVerb.run();
                } catch (RuntimeException | Error error) {
                    LOGGER.log(Level.SEVERE,
                            "A plugin resource's cleanup threw — the sweep continues", error);
                }
            }
            owned = 0;
        }

        @Override public void close() {
            seal();
            sweep();
        }
    }

    // ------------------------------------------------------------------ tasks

    /**
     * The plugin's quota'd lanes: the engine's compute/IO pools with a
     * per-plugin in-flight quota in front, the callbacks wrapped with
     * the scope-generation check (the late-result invalidation).
     */
    private final class Tasks implements PluginTasks {

        /** One in-flight submission's tracker (released exactly once). */
        private record Tracking(AtomicBoolean released) {
        }

        private final java.util.concurrent.ConcurrentLinkedDeque<Tracking> inFlightTrackers =
                new java.util.concurrent.ConcurrentLinkedDeque<>();
        private final AtomicLong refused = new AtomicLong();
        private final AtomicLong droppedLate = new AtomicLong();
        private volatile Plugin plugin;

        void bind(Plugin plugin) {
            this.plugin = plugin;
        }

        @Override public int maxInFlight() {
            return maxInFlight;
        }

        @Override public int inFlight() {
            return (int) inFlightTrackers.stream().filter(t -> !t.released().get()).count();
        }

        @Override public long refusedCount() {
            return refused.get();
        }

        @Override public long droppedLateCount() {
            return droppedLate.get();
        }

        @Override
        public <I, R> Optional<AsyncCompute.Computation<R>> compute(
                I input, Function<I, R> compute, BiConsumer<R, Long> onFresh, Runnable onStale) {
            Plugin owner = plugin;
            if (owner == null) {
                return Optional.empty();
            }
            Scope scope = owner.scope;
            if (scope == null || scope.closed() || !admit()) {
                refused.incrementAndGet();
                return Optional.empty();
            }
            Tracking tracking = new Tracking(new AtomicBoolean(false));
            inFlightTrackers.add(tracking);
            BiConsumer<R, Long> freshGate = (result, generation) -> {
                release(tracking);
                if (scope.closed()) {
                    droppedLate.incrementAndGet();
                    return;
                }
                onFresh.accept(result, generation);
            };
            Runnable staleGate = () -> {
                release(tracking);
                if (scope.closed()) {
                    droppedLate.incrementAndGet();
                    return;
                }
                if (onStale != null) {
                    onStale.run();
                }
            };
            return Optional.of(PluginManager.this.compute.submit(input, compute,
                    freshGate, staleGate));
        }

        @Override
        public <R> Optional<BlockingIo.Task<R>> io(
                String operation, Callable<R> work, BiConsumer<R, Long> onFresh, Runnable onStale) {
            Plugin owner = plugin;
            if (owner == null) {
                return Optional.empty();
            }
            Scope scope = owner.scope;
            if (scope == null || scope.closed() || !admit()) {
                refused.incrementAndGet();
                return Optional.empty();
            }
            Tracking tracking = new Tracking(new AtomicBoolean(false));
            inFlightTrackers.add(tracking);
            BiConsumer<R, Long> freshGate = (result, generation) -> {
                release(tracking);
                if (scope.closed()) {
                    droppedLate.incrementAndGet();
                    return;
                }
                onFresh.accept(result, generation);
            };
            Runnable staleGate = () -> {
                release(tracking);
                if (scope.closed()) {
                    droppedLate.incrementAndGet();
                    return;
                }
                if (onStale != null) {
                    onStale.run();
                }
            };
            return Optional.of(PluginManager.this.io.submit(operation, work,
                    freshGate, staleGate));
        }

        /** The admission gate: in-flight under the quota (reconciled live). */
        private boolean admit() {
            int live = 0;
            for (Tracking tracking : inFlightTrackers) {
                if (!tracking.released().get()) {
                    live++;
                }
            }
            return live < maxInFlight;
        }

        /** Releases one tracker (exactly once — the callbacks and the sweep). */
        private void release(Tracking tracking) {
            if (tracking.released().compareAndSet(false, true)) {
                inFlightTrackers.remove(tracking);
            }
        }
    }
}
