package net.zaminmc.torch.bukkit;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.concurrent.BlockingIo;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The §28.8 adversarial subset's first wave over the live engine (the
 * roadmap's named four):
 * <ol>
 *   <li><b>Wrong-owner mutation (case 3):</b> the plugin's async context
 *       attempts a world write — through the legacy API the adapter ROUTES
 *       it by the owner (a cross-owner intent, applied at a tick boundary);
 *       around the engine directly the engine's OWN assertion rejects it
 *       (the adapter weakened nothing — §28.10's final rule).</li>
 *   <li><b>Exception isolation (case 8):</b> covered on the live chain by
 *       the event bridge suite (the throwing NORMAL-priority listener with
 *       the whole chain and the tick surviving); this class re-asserts the
 *       engine-side law through the direct route.</li>
 *   <li><b>Disable during execution (case 6):</b> the disable walk seals the
 *       admissions, the sweep cancels the queued tasks, a running async body
 *       completes (the engine's law) but its late completion is DROPPED
 *       (never delivered — the native scope's own invalidation).</li>
 *   <li><b>Reentrant event dispatch (case 7):</b> covered on the live chain
 *       by the event bridge suite (the nested PlayerJoinEvent between the
 *       listener's own lines, same owner context, no deadlock).</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Order(4)
class BukkitAdversarialTest {

    private static BukkitTestHarness harness() throws Exception {
        BukkitTestHarness harness = BukkitTestHarness.harness();
        harness.ensureProbeEnabled(); // the class's entry state (order-independent)
        return harness;
    }

    @Test
    @Order(1)
    void anOffOwnerLegacyWriteIsRoutedByTheOwnerNeverRaced() throws Exception {
        BukkitTestHarness harness = harness();
        // From the probe's own enable wave: the async body (primary=false)
        // wrote GLOWSTONE at (2,6,2) THROUGH the legacy API; the write
        // applied (the verifier logged it) — routed, applied at a tick
        // boundary on the owner, never a silent off-owner race.
        awaitLine(harness, "adversarial:applied=true primary=true");
        assertTrue(harness.probeLogContains("adversarial:async-submitted"),
                "the async context submitted the write");
        // The engine's own world carries the routed result.
        assertEquals("minecraft:glowstone",
                harness.server.world().getBlock(new BlockPosition(2, 6, 2)).identifier()
                        .toString(),
                "the routed write applied in the engine's world");
    }

    @Test
    @Order(2)
    void theEngineDirectRouteStillRejectsOffOwnerWrites() throws Exception {
        BukkitTestHarness harness = harness();
        // The routed write from the wave must be in place before this test
        // probes the cell (order-independence: await it, never assume it).
        harness.await(() -> "minecraft:glowstone".equals(
                harness.server.world().getBlock(new BlockPosition(2, 6, 2)).identifier()
                        .toString()), "the routed write applied");
        // The engine's OWN enforcement, unweakened by the adapter (§28.10's
        // final rule): a direct engine write from the blocking lane throws
        // the ownership violation — the adapter never routed around it.
        CompletableFuture<String> verdict = new CompletableFuture<>();
        BlockingIo.Task<String> task = harness.server.blockingIo().submit(
                "adversarial-direct-write",
                () -> {
                    try {
                        harness.server.world().setBlock(new BlockPosition(2, 6, 2),
                                harness.server.blocks().byKey("minecraft:stone").orElseThrow());
                        return "NOT-REJECTED";
                    } catch (net.zaminmc.torch.server.concurrent.OwnershipViolationException
                            rejected) {
                        return "REJECTED";
                    }
                },
                (result, generation) -> verdict.complete(result),
                () -> verdict.complete("STALE"));
        harness.await(verdict::isDone, "the direct write's verdict landed");
        assertEquals("REJECTED", verdict.get(),
                "the engine's ownership assertion rejected the off-owner write");
        assertEquals("minecraft:glowstone",
                harness.server.world().getBlock(new BlockPosition(2, 6, 2)).identifier()
                        .toString(),
                "the rejected write never mutated the cell");
    }

    @Test
    @Order(3)
    void disableDuringExecutionInvalidatesTheUnfinishedWork() throws Exception {
        BukkitTestHarness harness = harness();
        Plugin probe = Bukkit.getPluginManager().getPlugin("Probe");
        // The probe must be live for its §28.5 laws to be under test.
        assertTrue(probe.isEnabled(), "the probe is live before the drill");

        // A queued delayed task (must NEVER run after the disable) and a
        // genuinely IN-FLIGHT async body (started BEFORE the disable —
        // running work completes, the engine's law; its late completion
        // dropped, never delivered).
        int queuedBefore = count(harness, "disable-drill:queued-ran");
        long droppedLateBefore = droppedLateCount(harness);
        Bukkit.getScheduler().runTaskLater(probe, () -> append(harness,
                "disable-drill:queued-ran"), 4);
        Bukkit.getScheduler().runTaskAsynchronously(probe, () -> {
            append(harness, "disable-drill:async-started"); // the in-flight marker
            try {
                EngineAwaits.awaitTicks(harness.server.world(), 5, "running when the disable walk runs");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            append(harness, "disable-drill:async-done");
        });
        // The body must be RUNNING before the disable lands — a body still
        // QUEUED at the sweep is cancelled (§28.5's own law for outstanding
        // work), which is a different (also correct) outcome than this
        // drill's target.
        harness.await(() -> {
            try {
                return count(harness, "disable-drill:async-started") > 0;
            } catch (Exception e) {
                return false;
            }
        }, "the async body is running before the disable");

        // The disable walk DURING execution: seal, onDisable, sweep.
        Bukkit.getPluginManager().disablePlugin(probe);
        harness.await(() -> {
            try {
                return harness.probeLogContains("lifecycle:onDisable");
            } catch (Exception e) {
                return false;
            }
        }, "the probe's onDisable ran");
        assertFalse(probe.isEnabled());
        assertEquals(net.zaminmc.torch.plugin.PluginState.DISABLED,
                harness.server.plugins().byId("Probe").orElseThrow().state());

        // The in-flight body completed on the lane (the engine's law:
        // running work cannot be cancelled — the sweep's cancel verb only
        // wins while the submission is still queued). Its late COMPLETION
        // is dropped by the native scope (§28.5: never delivered).
        harness.await(() -> {
            try {
                return count(harness, "disable-drill:async-done") > 0;
            } catch (Exception e) {
                return false;
            }
        }, "the in-flight async body completed");
        harness.await(() -> droppedLateCount(harness) > droppedLateBefore,
                "the late completion was DROPPED (the native scope's own invalidation)");

        // The queued task never ran — past four beat slots.
        EngineAwaits.awaitTicks(harness.server.world(), 10, "engine progress (a 500 wall settle)");
        assertEquals(queuedBefore, count(harness, "disable-drill:queued-ran"),
                "the queued delayed task never ran after the disable");
        assertTrue(droppedLateCount(harness) > droppedLateBefore,
                "droppedLate=" + droppedLateCount(harness) + " (before " + droppedLateBefore + ")");
    }

    // ------------------------------------------------------------------ helpers

    private static void awaitLine(BukkitTestHarness harness, String needle)
            throws InterruptedException {
        harness.await(() -> {
            try {
                return harness.probeLogContains(needle);
            } catch (Exception e) {
                return false;
            }
        }, "the probe logged: " + needle);
    }

    private static int count(BukkitTestHarness harness, String needle) throws Exception {
        int count = 0;
        for (String line : harness.probeLogLines()) {
            if (line.contains(needle)) {
                count++;
            }
        }
        return count;
    }

    private static long droppedLateCount(BukkitTestHarness harness) {
        return harness.server.plugins().byId("Probe").orElseThrow().tasks()
                .droppedLateCount();
    }

    /** Test-side task bodies write the same cross-classloader log. */
    private static void append(BukkitTestHarness harness, String line) {
        try {
            java.nio.file.Files.writeString(harness.probeLog, line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
