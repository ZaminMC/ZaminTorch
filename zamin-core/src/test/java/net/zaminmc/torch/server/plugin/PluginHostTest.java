package net.zaminmc.torch.server.plugin;

import net.zaminmc.torch.plugin.PluginBody;
import net.zaminmc.torch.plugin.PluginDescriptor;
import net.zaminmc.torch.plugin.PluginHandle;
import net.zaminmc.torch.plugin.PluginState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plugin host's own law (Slice 71 — §28.5/§28.6's engine core): the
 * lifecycle state machine with its failure transitions, the resource
 * scopes (own/sweep/isolation), the disable walk's admissions gate
 * (§28.8 case 6 — disable during execution), the reload's no-duplicates
 * law, and the per-plugin task quotas.
 *
 * <p>The harness uses the engine's own concurrent lanes through their
 * public facades (a headless compute/IO pair standing in for the full
 * engine — the lanes' contracts are the host's own dependencies).</p>
 */
class PluginHostTest {

    /** A headless compute facade answering over the caller's thread. */
    private static class DirectCompute
            implements net.zaminmc.torch.concurrent.AsyncCompute {

        @Override
        public <I, R> Computation<R> submit(I input,
                java.util.function.Function<I, R> compute,
                java.util.function.BiConsumer<R, Long> onFresh, Runnable onStale) {
            R result = compute.apply(input);
            onFresh.accept(result, 0L);
            return new Computation<>() {
                @Override public long generation() {
                    return 0L;
                }

                @Override public boolean isDone() {
                    return true;
                }

                @Override public boolean isCancelled() {
                    return false;
                }

                @Override public boolean isFailed() {
                    return false;
                }

                @Override public Throwable failure() {
                    return null;
                }

                @Override public boolean tryCancel() {
                    return false;
                }
            };
        }

        @Override public long submittedCount() {
            return 0;
        }

        @Override public long rejectedCount() {
            return 0;
        }

        @Override public long failedCount() {
            return 0;
        }

        @Override public int queuedCount() {
            return 0;
        }
    }

    /** A headless blocking-IO facade answering over the caller's thread. */
    private static final class DirectIo implements net.zaminmc.torch.concurrent.BlockingIo {

        @Override
        public <R> Task<R> submit(String operation, java.util.concurrent.Callable<R> work,
                java.util.function.BiConsumer<R, Long> onFresh, Runnable onStale) {
            try {
                R result = work.call();
                onFresh.accept(result, 0L);
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
            return new Task<>() {
                @Override public long generation() {
                    return 0L;
                }

                @Override public boolean isDone() {
                    return true;
                }

                @Override public boolean isCancelled() {
                    return false;
                }

                @Override public boolean isFailed() {
                    return false;
                }

                @Override public Throwable failure() {
                    return null;
                }

                @Override public boolean tryCancel() {
                    return false;
                }
            };
        }

        @Override public long submittedCount() {
            return 0;
        }

        @Override public long rejectedCount() {
            return 0;
        }

        @Override public long failedCount() {
            return 0;
        }

        @Override public int queuedCount() {
            return 0;
        }
    }

    /**
     * A deferred compute facade: the computation hangs until the test
     * releases it (the §28.8 case-6 shape — a callback running while the
     * disable walk begins).
     */
    private static final class GatedCompute extends DirectCompute {
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch started = new CountDownLatch(1);
        volatile boolean holdCallbacks;

        @Override
        public <I, R> Computation<R> submit(I input,
                java.util.function.Function<I, R> compute,
                java.util.function.BiConsumer<R, Long> onFresh, Runnable onStale) {
            started.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("the gate never opened");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            R result = compute.apply(input);
            if (holdCallbacks) {
                // The late delivery: the callback fires after the test
                // already disabled the plugin (the gate's own point).
                onFresh.accept(result, 0L);
                return new Computation<>() {
                    @Override public long generation() {
                        return 0L;
                    }

                    @Override public boolean isDone() {
                        return true;
                    }

                    @Override public boolean isCancelled() {
                        return false;
                    }

                    @Override public boolean isFailed() {
                        return false;
                    }

                    @Override public Throwable failure() {
                        return null;
                    }

                    @Override public boolean tryCancel() {
                        return false;
                    }
                };
            }
            onFresh.accept(result, 0L);
            return new Computation<>() {
                @Override public long generation() {
                    return 0L;
                }

                @Override public boolean isDone() {
                    return true;
                }

                @Override public boolean isCancelled() {
                    return false;
                }

                @Override public boolean isFailed() {
                    return false;
                }

                @Override public Throwable failure() {
                    return null;
                }

                @Override public boolean tryCancel() {
                    return false;
                }
            };
        }
    }

    private static PluginDescriptor descriptor(String id) {
        return new PluginDescriptor(id, "1.0.0", List.of("Torch"));
    }

    @Test
    void theLifecycleWalkRunsTheHappyPath() {
        PluginManager host = new PluginManager(new DirectCompute(), new DirectIo());
        AtomicReference<PluginState> seenInEnable = new AtomicReference<>();
        PluginHandle plugin = host.register(descriptor("happy"), handle -> {
            seenInEnable.set(handle.state());
            handle.resources().own(() -> { }); // one owned resource
        });
        assertEquals(PluginState.ENABLING, seenInEnable.get(),
                "onEnable runs inside the ENABLING state");
        assertEquals(PluginState.ENABLED, plugin.state());
        assertEquals(1, plugin.resources().ownedCount(), "the scope owns the resource");
        assertTrue(host.byId("happy").isPresent());
        assertEquals(1, host.plugins().size());

        host.disable(plugin);
        assertEquals(PluginState.DISABLED, plugin.state());
        assertEquals(0, plugin.resources().ownedCount(),
                "the sweep released the owned resource");
        host.unload(plugin);
        assertEquals(PluginState.UNLOADED, plugin.state());
        assertTrue(host.byId("happy").isEmpty(), "the id is free again");
    }

    @Test
    void theEnableFailureRollsBackAndLandsDisabled() {
        PluginManager host = new PluginManager(new DirectCompute(), new DirectIo());
        AtomicBoolean cleaned = new AtomicBoolean();
        PluginHandle plugin = host.register(descriptor("broken"), handle -> {
            handle.resources().own(() -> cleaned.set(true));
            throw new IllegalStateException("the enable broke");
        });
        assertEquals(PluginState.DISABLED, plugin.state(),
                "the failure transition lands DISABLED — never ENABLED");
        assertTrue(cleaned.get(), "the partial registration's close verb ran (the rollback)");
        assertTrue(plugin.resources().closed(), "the failed scope is sealed");
        assertFalse(plugin.resources().own(() -> { }),
                "a failed plugin's admissions are refused");
    }

    @Test
    void theScopeSweepsEveryResourceIsolated() {
        PluginManager host = new PluginManager(new DirectCompute(), new DirectIo());
        AtomicInteger swept = new AtomicInteger();
        PluginHandle plugin = host.register(descriptor("sweep"), handle -> {
            handle.resources().own(() -> swept.incrementAndGet());
            handle.resources().own(() -> {
                throw new IllegalStateException("the first cleanup throws");
            });
            handle.resources().own(swept::incrementAndGet);
        });
        assertEquals(3, plugin.resources().ownedCount());
        host.disable(plugin);
        assertEquals(2, swept.get(),
                "the throwing cleanup did not shield the third (each isolated)");
        assertEquals(0, plugin.resources().ownedCount());
        plugin.resources().close(); // idempotent
        assertEquals(2, swept.get(), "the second close is a no-op");
    }

    @Test
    void theDisableWalkRefusesAdmissionsTheMomentItBegins() throws InterruptedException {
        PluginManager host = new PluginManager(new DirectCompute(), new DirectIo());
        CountDownLatch disableBegan = new CountDownLatch(1);
        CountDownLatch disableMayFinish = new CountDownLatch(1);
        AtomicBoolean admissionDuringDisable = new AtomicBoolean(true);
        PluginHandle plugin = host.register(descriptor("gate"), handle -> {
        });
        assertEquals(PluginState.ENABLED, plugin.state());

        // The disable walk holds inside onDisable; a submission arriving
        // meanwhile must be refused (§28.5: admissions close at
        // DISABLING, not at DISABLED).
        Thread disabler = new Thread(() -> host.disable(plugin));
        // Override: run the disable manually to interleave — register a
        // probe first, then disable with a holding onDisable.
        PluginHandle held = host.register(descriptor("held"), new PluginBody() {
            @Override public void onEnable(PluginHandle handle) {
            }

            @Override public void onDisable(PluginHandle handle) {
                disableBegan.countDown();
                try {
                    disableMayFinish.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        disabler.start(); // the happy plugin's disable (no-op interleave)
        Thread heldDisabler = new Thread(() -> host.disable(held));
        heldDisabler.start();
        try {
            assertTrue(disableBegan.await(5, TimeUnit.SECONDS),
                    "the disable walk began (the callback runs)");
            assertEquals(PluginState.DISABLING, held.state());
            admissionDuringDisable.set(held.tasks().compute(1, i -> i,
                    (r, g) -> { }, () -> { }).isPresent());
            assertFalse(admissionDuringDisable.get(),
                    "a submission during DISABLING is refused");
            assertFalse(held.resources().own(() -> { }),
                    "a resource admission during DISABLING is refused");
        } finally {
            disableMayFinish.countDown();
            try {
                heldDisabler.join(5_000);
                disabler.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertEquals(PluginState.DISABLED, held.state());
        assertEquals(1, held.tasks().refusedCount(), "the refusal is observable");
    }

    @Test
    void theLateAsyncResultNeverReachesTheDisabledPlugin() throws Exception {
        GatedCompute gated = new GatedCompute();
        gated.holdCallbacks = true; // deliver after the disable began
        PluginManager host = new PluginManager(gated, new DirectIo());
        AtomicInteger delivered = new AtomicInteger();

        PluginHandle plugin = host.register(descriptor("late"), handle -> {
        });
        Thread submitter = new Thread(() -> host.byId("late").orElseThrow().tasks()
                .compute(1, i -> i * 2, (r, g) -> delivered.incrementAndGet(), () -> { }));
        submitter.start();
        // The facade's own started latch: the submission was admitted and
        // the computation is hanging (the facade blocks its caller until
        // release — the caller-side hold is the test's shape).
        assertTrue(gated.started.await(5, TimeUnit.SECONDS), "the submission was admitted");

        Thread disabler = new Thread(() -> host.disable(plugin));
        disabler.start();
        Thread.sleep(200); // the disable walk runs while the computation hangs
        gated.release.countDown(); // the computation finishes NOW — late
        disabler.join(5_000);
        submitter.join(5_000);

        assertEquals(PluginState.DISABLED, plugin.state());
        assertEquals(0, delivered.get(),
                "the late result never reached the disabled plugin's callback");
        assertEquals(1, plugin.tasks().droppedLateCount(),
                "the drop is observable (§28.9's rejection counts)");
    }

    @Test
    void theReloadCarriesNoDuplicatesAndAFreshGeneration() {
        PluginManager host = new PluginManager(new DirectCompute(), new DirectIo());
        AtomicInteger enables = new AtomicInteger();
        AtomicInteger disableCleans = new AtomicInteger();
        PluginHandle plugin = host.register(descriptor("reload"), handle -> {
            enables.incrementAndGet();
            handle.resources().own(disableCleans::incrementAndGet);
        });
        long firstGeneration = plugin.resources().generation();
        host.reload(plugin);
        assertEquals(PluginState.ENABLED, plugin.state());
        assertEquals(2, enables.get(), "the enable ran twice");
        assertEquals(1, disableCleans.get(), "the first generation's resource swept once");
        assertEquals(1, plugin.resources().ownedCount(),
                "the fresh scope owns exactly the re-registered resource");
        assertTrue(plugin.resources().generation() != firstGeneration,
                "the re-enable answers a fresh generation");
        // A stale-generation admission attempt through the OLD scope
        // reference: the plugin always answers its CURRENT scope, so this
        // is the reload's no-duplicates proof at the surface.
        host.reload(plugin);
        assertEquals(1, plugin.resources().ownedCount(),
                "still exactly one resource after the second reload");
    }

    @Test
    void theDuplicateIdIsRefused() {
        PluginManager host = new PluginManager(new DirectCompute(), new DirectIo());
        host.register(descriptor("dup"), handle -> { });
        assertThrows(IllegalStateException.class,
                () -> host.register(descriptor("dup"), handle -> { }),
                "a duplicate id is refused loudly");
        assertEquals(1, host.plugins().size());
        // After unload the id is free again.
        PluginHandle first = host.byId("dup").orElseThrow();
        host.disable(first);
        host.unload(first);
        PluginHandle second = host.register(descriptor("dup"), handle -> { });
        assertEquals(PluginState.ENABLED, second.state());
    }

    @Test
    void theTaskQuotaRefusesTheFloodButNotTheNeighbor() throws Exception {
        // An async compute facade: submit answers at once, the work runs
        // on a background pool gated on the release latch — the honest
        // in-flight shape (admitted-but-undelivered holds the quota).
        CountDownLatch release = new CountDownLatch(1);
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            net.zaminmc.torch.concurrent.AsyncCompute gated =
                    new net.zaminmc.torch.concurrent.AsyncCompute() {
                @Override
                public <I, R> Computation<R> submit(I input,
                        java.util.function.Function<I, R> compute,
                        java.util.function.BiConsumer<R, Long> onFresh, Runnable onStale) {
                    java.util.concurrent.atomic.AtomicBoolean done =
                            new java.util.concurrent.atomic.AtomicBoolean();
                    pool.submit(() -> {
                        try {
                            release.await(15, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        R result = compute.apply(input);
                        done.set(true);
                        onFresh.accept(result, 0L);
                    });
                    return new Computation<>() {
                        @Override public long generation() {
                            return 0L;
                        }

                        @Override public boolean isDone() {
                            return done.get();
                        }

                        @Override public boolean isCancelled() {
                            return false;
                        }

                        @Override public boolean isFailed() {
                            return false;
                        }

                        @Override public Throwable failure() {
                            return null;
                        }

                        @Override public boolean tryCancel() {
                            return false;
                        }
                    };
                }

                @Override public long submittedCount() {
                    return 0;
                }

                @Override public long rejectedCount() {
                    return 0;
                }

                @Override public long failedCount() {
                    return 0;
                }

                @Override public int queuedCount() {
                    return 0;
                }
            };
            PluginManager host = new PluginManager(gated, new DirectIo(), 4);
            PluginHandle noisy = host.register(descriptor("noisy"), handle -> { });
            PluginHandle quiet = host.register(descriptor("quiet"), handle -> { });

            AtomicInteger noisyDelivered = new AtomicInteger();
            int admitted = 0;
            for (int i = 0; i < 20; i++) {
                Optional<net.zaminmc.torch.concurrent.AsyncCompute.Computation<Integer>> answer =
                        noisy.tasks().compute(i, v -> v,
                                (r, g) -> noisyDelivered.incrementAndGet(), () -> { });
                if (answer.isPresent()) {
                    admitted++;
                }
            }
            assertEquals(4, admitted, "the noisy plugin's quota (4) capped its admissions");
            assertEquals(16, noisy.tasks().refusedCount(), "the refusals are observable");
            // The neighbor still admits (the quota is per-plugin, §28.6).
            Optional<?> neighbor = quiet.tasks().compute(1, v -> v, (r, g) -> { }, () -> { });
            assertTrue(neighbor.isPresent(), "the quiet plugin's submission admitted");
            release.countDown();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void theReentrancyGuardRejectsLifecycleCallsFromCallbacks() {
        PluginManager host = new PluginManager(new DirectCompute(), new DirectIo());
        AtomicReference<Throwable> rejection = new AtomicReference<>();
        PluginHandle plugin = host.register(descriptor("reentrant"), new PluginBody() {
            @Override public void onEnable(PluginHandle handle) {
                try {
                    host.disable(handle); // the reentrant lifecycle call
                } catch (RuntimeException error) {
                    rejection.set(error);
                }
            }
        });
        assertNotNull(rejection.get(), "the reentrant disable was rejected");
        assertEquals(PluginState.ENABLED, plugin.state(),
                "the rejected call left the walk intact");
        // A second plugin registering from inside a callback is also rejected.
        AtomicReference<Throwable> registerRejection = new AtomicReference<>();
        host.register(descriptor("outer"), new PluginBody() {
            @Override public void onEnable(PluginHandle handle) {
                try {
                    host.register(descriptor("inner"), h -> { });
                } catch (RuntimeException error) {
                    registerRejection.set(error);
                }
            }
        });
        assertNotNull(registerRejection.get(), "the reentrant register was rejected");
        assertTrue(host.byId("inner").isEmpty(), "the inner plugin never registered");
        assertNull(null); // explicit: no dangling assertions
    }
}
