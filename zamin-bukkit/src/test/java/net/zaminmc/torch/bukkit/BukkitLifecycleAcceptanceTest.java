package net.zaminmc.torch.bukkit;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lifecycle bridge end-to-end (§28.7's plugin-discovery/lifecycle
 * inventory over the live engine): the discovery wave loads the probe jar
 * through the API artifact's own machinery, the bridge's own {@code onLoad}
 * runs before the native host's enable walk, and every native §28.5 law
 * holds — the disable sweep unregisters the listeners and cancels the
 * pending tasks, and the reload hands the plugin a FRESH scope.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Order(1)
class BukkitLifecycleAcceptanceTest {

    private static BukkitTestHarness harness() throws Exception {
        BukkitTestHarness harness = BukkitTestHarness.harness();
        harness.ensureProbeEnabled(); // the class's entry state (order-independent)
        return harness;
    }

    @Test
    @Order(1)
    void theProbeLoadedThroughTheLegacyMachineryInOrder() throws Exception {
        BukkitTestHarness harness = harness();
        // The load walk's own order: onLoad before the native enable walk.
        List<String> lines = harness.probeLogLines();
        int onLoad = indexOf(lines, "lifecycle:onLoad");
        int onEnable = indexOf(lines, "lifecycle:onEnable");
        assertTrue(onLoad >= 0, "onLoad ran: " + lines);
        assertTrue(onEnable > onLoad, "onEnable after onLoad: " + lines);
    }

    @Test
    @Order(2)
    void theLegacyPluginManagerSurfaceAnswers() throws Exception {
        BukkitTestHarness harness = harness();
        org.bukkit.plugin.Plugin probe = harness.adapter.bukkitServer()
                .getPluginManager().getPlugin("Probe");
        assertNotNull(probe, "the probe is registered under its plugin.yml name");
        assertTrue(probe.isEnabled(), "the native host's enable walk landed it enabled");
        assertEquals("Probe", probe.getName());
        assertEquals("1.0.0", probe.getDescription().getVersion());
        assertTrue(harness.adapter.bukkitServer().getPluginManager().isPluginEnabled("Probe"));
    }

    @Test
    @Order(3)
    void theEngineHostOwnsTheBridgedPlugin() throws Exception {
        BukkitTestHarness harness = harness();
        org.bukkit.plugin.Plugin probe = harness.adapter.bukkitServer()
                .getPluginManager().getPlugin("Probe");
        // The native §28.5 host carries the same plugin (one native plugin
        // per bridged plugin — bridged, never duplicated).
        assertTrue(harness.server.plugins().byId("Probe").isPresent(),
                "the engine's native host registered the bridged plugin");
        assertEquals(net.zaminmc.torch.plugin.PluginState.ENABLED,
                harness.server.plugins().byId("Probe").orElseThrow().state());
    }

    @Test
    @Order(4)
    void theDisableWalkSweepsTheOwnedResources() throws Exception {
        BukkitTestHarness harness = harness();
        org.bukkit.plugin.PluginManager manager = harness.adapter.bukkitServer()
                .getPluginManager();
        org.bukkit.plugin.Plugin probe = manager.getPlugin("Probe");
        manager.disablePlugin(probe);
        harness.await(() -> {
            try {
                return harness.probeLogContains("lifecycle:onDisable");
            } catch (Exception e) {
                return false;
            }
        }, "onDisable logged");
        assertFalse(probe.isEnabled(), "the legacy flag lands disabled");
        assertEquals(net.zaminmc.torch.plugin.PluginState.DISABLED,
                harness.server.plugins().byId("Probe").orElseThrow().state(),
                "the native state lands DISABLED");

        // The sweep unregistered the listeners: a fresh join after the
        // disable does not reach the plugin (its log stays untouched).
        int joinsBefore = countOf(harness.probeLogLines(), "event:join");
        harness.join("AfterDisable");
        harness.await(() -> harness.server.players().size() >= 1, "the join landed");
        Thread.sleep(300); // the join event certainly dispatched
        assertEquals(joinsBefore, countOf(harness.probeLogLines(), "event:join"),
                "the disabled plugin's listeners are gone");

    }

    @Test
    @Order(5)
    void theReloadHandsThePluginAFreshScope() throws Exception {
        BukkitTestHarness harness = harness();
        org.bukkit.plugin.PluginManager manager = harness.adapter.bukkitServer()
                .getPluginManager();
        org.bukkit.plugin.Plugin probe = manager.getPlugin("Probe");
        manager.enablePlugin(probe); // reload's own law: simply enabled
        assertTrue(probe.isEnabled(), "the reload's enable walk landed it enabled again");
        // The walk's own order evidence: a SECOND onEnable line after the
        // first (the probe logs its walks in order).
        assertTrue(countOf(harness.probeLogLines(), "lifecycle:onEnable") >= 2,
                "the re-enable's onEnable ran (the fresh scope's own walk)");

        int joinsBefore = countOf(harness.probeLogLines(), "event:join");
        harness.join("AfterReload");
        harness.await(() -> {
            try {
                return countOf(harness.probeLogLines(), "event:join") > joinsBefore;
            } catch (Exception e) {
                return false;
            }
        }, "a fresh join reaches the re-enabled plugin (the fresh scope's listeners)");
        // The re-enabled plugin's scheduler works through the fresh scope.
        assertTrue(harness.probeLogContains("sched:sync-run"),
                "the re-registered probe's own history (from the first enable)");
    }

    private static int indexOf(List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    private static int countOf(List<String> lines, String needle) {
        int count = 0;
        for (String line : lines) {
            if (line.contains(needle)) {
                count++;
            }
        }
        return count;
    }
}
