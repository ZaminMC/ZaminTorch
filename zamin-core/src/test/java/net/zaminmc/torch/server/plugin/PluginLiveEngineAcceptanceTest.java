package net.zaminmc.torch.server.plugin;

import net.zaminmc.torch.plugin.PluginHandle;
import net.zaminmc.torch.plugin.PluginState;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The plugin host's live-engine acceptance (Slice 76 — the §28.5/§28.6
 * wiring proof): a plugin through the REAL EngineServer's
 * {@code plugins()} — the real lifecycle, the real event bus (a join
 * observed through the public subscription, owned by the scope), the
 * real compute and blocking-IO lanes (the quota'd submissions landing
 * their results), the disable walk sweeping the owned resources and
 * closing the admissions, and the shutdown's own disable arm.
 */
class PluginLiveEngineAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "pluglive",
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private void await(java.util.function.BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private net.zaminmc.torch.server.player.PlayerSession joinAs(
            EngineServer server, String name) throws InterruptedException {
        var link = new net.zaminmc.torch.server.net.ClientLink() {
            @Override public boolean isActive() {
                return true;
            }

            @Override public void kick(String reason) {
            }
        };
        var accepted = (net.zaminmc.torch.server.net.EngineBridge.Accepted)
                server.joinRequest(link, name,
                        UUID.nameUUIDFromBytes(name.getBytes()));
        net.zaminmc.torch.server.player.PlayerSession session = accepted.session();
        server.ticker().submit(session::markPlaying);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "the session promoted to PLAYING");
        return session;
    }

    @Test
    void thePluginRidesTheRealLanesAndTheRealEventBus() throws Exception {
        EngineServer server = boot();
        try {
            AtomicInteger joins = new AtomicInteger();
            AtomicReference<String> lastJoinName = new AtomicReference<>();
            AtomicReference<Integer> computeResult = new AtomicReference<>();
            AtomicReference<String> ioResult = new AtomicReference<>();
            AtomicBoolean subscriptionSwept = new AtomicBoolean();
            AtomicBoolean customResourceSwept = new AtomicBoolean();

            // The registration through the REAL host (the engine's own
            // compute + blocking lanes ride under the quota).
            PluginHandle plugin = server.plugins().register(
                    new net.zaminmc.torch.plugin.PluginDescriptor(
                            "live", "1.0.0", java.util.List.of("acceptance")),
                    handle -> {
                        // The event subscription through the PUBLIC bus,
                        // OWNED by the plugin's scope (the unsubscribe verb
                        // rides the sweep).
                        var subscription = server.events().on(
                                net.zaminmc.torch.event.player.PlayerJoinEvent.class,
                                net.zaminmc.torch.event.EventPriority.NORMAL,
                                event -> {
                                    joins.incrementAndGet();
                                    lastJoinName.set(event.player().name());
                                });
                        handle.resources().own(() -> {
                            subscription.unsubscribe();
                            subscriptionSwept.set(true);
                        });
                        handle.resources().own(() -> customResourceSwept.set(true));
                        // The quota'd submissions over the engine's REAL
                        // lanes (§28.6 — the results land asynchronously).
                        handle.tasks().compute(21, n -> n * 2,
                                (result, generation) -> computeResult.set(result),
                                () -> { });
                        handle.tasks().io("acceptance-read", () -> "io-done",
                                (result, generation) -> ioResult.set(result),
                                () -> { });
                    });
            assertEquals(PluginState.ENABLED, plugin.state(),
                    "the real host enabled the plugin");
            assertEquals(2, plugin.resources().ownedCount(),
                    "the scope owns the subscription + the custom resource");

            // The real compute lane landed the result.
            await(() -> computeResult.get() != null, "the compute result landed");
            assertEquals(21 * 2, computeResult.get(), "the compute lane's answer");
            // The real blocking lane landed the IO result.
            await(() -> ioResult.get() != null, "the io result landed");
            assertEquals("io-done", ioResult.get(), "the blocking lane's answer");

            // A REAL join fires the plugin's subscription (the public bus).
            joinAs(server, "watched");
            await(() -> joins.get() >= 1, "the join reached the plugin");
            assertEquals("watched", lastJoinName.get(),
                    "the handler read the joining player's name");

            // The disable walk: the sweep releases the owned resources (the
            // subscription leaves first — the registration order) and the
            // admissions close.
            server.plugins().disable(plugin);
            assertEquals(PluginState.DISABLED, plugin.state());
            assertEquals(0, plugin.resources().ownedCount(), "the sweep ran");
            assertTrue(subscriptionSwept.get(), "the subscription's close verb ran");
            assertTrue(customResourceSwept.get(), "the custom resource's close verb ran");
            assertTrue(plugin.tasks().compute(1, n -> n,
                    (r, g) -> { }, () -> { }).isEmpty(),
                    "a task submission after disable is refused (the admissions gate)");

            // The swept subscription no longer fires: a second join never
            // reaches the disabled plugin.
            joinAs(server, "unwatched");
            EngineAwaits.awaitTicks(server.world(), 10, "engine progress (a 500 wall settle)");
            assertEquals(1, joins.get(),
                    "the disabled plugin's subscription never fired again");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theShutdownDisablesTheEnabledPlugins() throws Exception {
        // The §28.5 stop arm: the shutdown's own disable walk — a plugin
        // left ENABLED is sealed and swept before the lanes go down.
        EngineServer server = boot();
        AtomicBoolean swept = new AtomicBoolean();
        PluginHandle plugin = server.plugins().register(
                new net.zaminmc.torch.plugin.PluginDescriptor(
                        "stopper", "1.0.0", java.util.List.of("acceptance")),
                handle -> handle.resources().own(() -> swept.set(true)));
        assertEquals(PluginState.ENABLED, plugin.state());
        server.shutdown(null);
        assertEquals(PluginState.DISABLED, plugin.state(),
                "the shutdown's disable walk landed DISABLED");
        assertTrue(swept.get(), "the stop swept the plugin's resources");
        assertFalse(server.plugins().byId("stopper").isEmpty(),
                "the handle stays queryable after the stop (unload is explicit)");
    }
}
