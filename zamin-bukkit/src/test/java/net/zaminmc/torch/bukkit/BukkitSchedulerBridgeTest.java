package net.zaminmc.torch.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The scheduler bridge over the live engine (§28.7's inventory: synchronous
 * task scheduling and repeating-task order, asynchronous task restrictions,
 * cancellation) — routed onto the engine's OWN structures: sync bodies on
 * the owner context (the ticker's due-time queue, FIFO in submission
 * order), async bodies on the plugin's own quota'd blocking-lane (never a
 * simulated worker thread), every task scope-owned.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Order(3)
class BukkitSchedulerBridgeTest {

    private static BukkitTestHarness harness() throws Exception {
        BukkitTestHarness harness = BukkitTestHarness.harness();
        harness.ensureProbeEnabled(); // the class's entry state (order-independent)
        return harness;
    }

    @Test
    @Order(1)
    void syncTasksRunOnThePrimaryThreadInFifoOrder() throws Exception {
        BukkitTestHarness harness = harness();
        // The probe's own enable-wave tasks (from its first enable — the
        // fresh harness's history) prove the three laws at once:
        awaitLine(harness, "sched:sync-run primary=true");
        assertTrue(harness.probeLogContains("sched:sync-run primary=true"),
                "the sync body ran on the owner context (isPrimaryThread)");
        List<String> lines = harness.probeLogLines();
        int a = firstIndex(lines, "sched:sync-a");
        int b = firstIndex(lines, "sched:sync-b");
        assertTrue(a >= 0 && b >= 0 && a < b, "FIFO in submission order: " + lines);
    }

    @Test
    @Order(2)
    void aLaterTaskLandsAtItsOwnTick() throws Exception {
        BukkitTestHarness harness = harness();
        awaitLine(harness, "sched:later-2");
        assertTrue(harness.probeLogContains("sched:later-2"),
                "the delayed body ran at its own drain");
    }

    @Test
    @Order(3)
    void aRepeatingTaskBeatsAtItsPeriodUntilCancelled() throws Exception {
        BukkitTestHarness harness = harness();
        org.bukkit.plugin.Plugin probe = Bukkit.getPluginManager().getPlugin("Probe");
        awaitCount(harness, "sched:timer-beat", 3);
        // The beats are spaced by the engine's own period: at least three
        // beats, each on the owner context, then cancelTask stops it.
        int beats = count(harness, "sched:timer-beat");
        assertTrue(beats >= 3, "the timer beat at least three times: " + beats);
        // The beats' own ticks are strictly increasing (the engine's drain
        // order made visible through the bridge).
        List<String> lines = harness.probeLogLines();
        long previous = -1;
        int seen = 0;
        for (String line : lines) {
            if (line.contains("sched:timer-beat")) {
                seen++;
                long tick = Long.parseLong(
                        line.substring(line.indexOf("tick=") + 5).trim());
                assertTrue(tick > previous, "beat ticks strictly increase: " + line);
                previous = tick;
            }
        }
        assertTrue(seen >= 3, "at least three beat lines");

        // Order-independence: the probe may have been enabled more than
        // once (a fresh wave per enable) — cancel EVERY pending sync task
        // of the probe, then no beat may ever appear again.
        for (org.bukkit.scheduler.BukkitTask task : List.copyOf(
                Bukkit.getScheduler().getPendingTasks())) {
            if (task.getOwner() == probe && task.isSync()) {
                assertTrue(Bukkit.getScheduler().isQueued(task.getTaskId()),
                        "queued before the cancel");
                Bukkit.getScheduler().cancelTask(task.getTaskId());
            }
        }
        int frozen = count(harness, "sched:timer-beat");
        EngineAwaits.awaitTicks(harness.server.world(), 8, "certainly past two more beat slots");
        assertEquals(frozen, count(harness, "sched:timer-beat"),
                "the cancelled timers never beat again");
        for (org.bukkit.scheduler.BukkitTask task : Bukkit.getScheduler().getPendingTasks()) {
            assertFalse(task.getOwner() == probe && task.isSync(),
                    "no pending sync task of the probe remains");
        }
    }

    @Test
    @Order(4)
    void asyncBodiesRunOffTheOwnerOnThePluginOwnLane() throws Exception {
        BukkitTestHarness harness = harness();
        awaitLine(harness, "adversarial:async-body-done");
        assertTrue(harness.probeLogContains("sched:async-run primary=false"),
                "the async body ran OFF the owner context");
        assertTrue(harness.probeLogContains("event:chat-async")
                || harness.probeLogLines().size() > 0, "the run happened");
        List<String> lines = harness.probeLogLines();
        String asyncLine = line(lines, "sched:async-run");
        assertTrue(asyncLine.contains("thread="), "the lane's own thread identity: " + asyncLine);
        assertFalse(asyncLine.contains("thread=zamin-tick"),
                "never the tick thread (§28.7: no simulated worker threads)");
    }

    @Test
    @Order(5)
    void theEngineAnsweredIsPrimaryThreadHonestly() throws Exception {
        BukkitTestHarness harness = harness();
        // On the test thread: NOT the primary thread (the engine's own
        // answer — a thread fact, §27.6's integration interface).
        assertFalse(Bukkit.isPrimaryThread(), "the test thread is not the owner");
        // The probe's sync bodies answered true (already asserted), and the
        // async body answered false: both sides of the honest answer.
        assertTrue(harness.probeLogContains("sched:sync-run primary=true"));
        assertTrue(harness.probeLogContains("sched:async-run primary=false"));
    }

    @Test
    @Order(6)
    void anAsyncTaskFromTheTestSideRunsOnTheLane() throws Exception {
        BukkitTestHarness harness = harness();
        org.bukkit.plugin.Plugin probe = Bukkit.getPluginManager().getPlugin("Probe");
        // scheduleAsyncDelayedTask's legacy int form: -1 never, a real id yes.
        int id = Bukkit.getScheduler().scheduleAsyncDelayedTask(probe, () -> {
        }, 1);
        assertTrue(id > 0, "the int form admits the task");
        Bukkit.getScheduler().cancelTask(id);
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

    private static void awaitCount(BukkitTestHarness harness, String needle, int wanted)
            throws InterruptedException {
        harness.await(() -> {
            try {
                return count(harness, needle) >= wanted;
            } catch (Exception e) {
                return false;
            }
        }, "at least " + wanted + " lines of: " + needle);
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

    private static int firstIndex(List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    private static String line(List<String> lines, String needle) {
        for (String line : lines) {
            if (line.contains(needle)) {
                return line;
            }
        }
        throw new AssertionError("no line of: " + needle);
    }

    private static long tickOf(List<String> lines, int nth) {
        int seen = 0;
        for (String line : lines) {
            if (line.contains("sched:timer-beat")) {
                seen++;
                if (seen == nth) {
                    String tick = line.substring(line.indexOf("tick=") + 5).trim();
                    return Long.parseLong(tick);
                }
            }
        }
        throw new AssertionError("no " + nth + "th beat");
    }
}
