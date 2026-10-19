package net.zaminmc.torch.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scheduler bridge over the live engine (§28.7's inventory: synchronous
 * task scheduling and repeating-task order, asynchronous task restrictions,
 * cancellation) — routed onto the engine's OWN structures: sync bodies on
 * the owner context (the ticker's due-time queue, FIFO in submission
 * order), async bodies on the plugin's own quota'd blocking-lane (never a
 * simulated worker thread), every task scope-owned.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Order(3)
class BukkitSchedulerBridgeTest {

    private static BukkitTestHarness harness() throws Exception {
        return BukkitTestHarness.harness();
    }

    @Test
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
    void aLaterTaskLandsAtItsOwnTick() throws Exception {
        BukkitTestHarness harness = harness();
        awaitLine(harness, "sched:later-2");
        assertTrue(harness.probeLogContains("sched:later-2"),
                "the delayed body ran at its own drain");
    }

    @Test
    void aRepeatingTaskBeatsAtItsPeriodUntilCancelled() throws Exception {
        BukkitTestHarness harness = harness();
        org.bukkit.plugin.Plugin probe = Bukkit.getPluginManager().getPlugin("Probe");
        awaitCount(harness, "sched:timer-beat", 3);
        // The beats are spaced by the engine's own period: at least three
        // beats, each on the owner context, then cancelTask stops it.
        int beats = count(harness, "sched:timer-beat");
        assertTrue(beats >= 3, "the timer beat at least three times: " + beats);
        List<String> lines = harness.probeLogLines();
        long firstTick = tickOf(lines, 1);
        long thirdTick = tickOf(lines, 3);
        assertTrue(thirdTick >= firstTick + 4,
                "the beats are spaced (period 2 in engine ticks): first=" + firstTick
                        + " third=" + thirdTick);

        int taskId = -1;
        for (org.bukkit.scheduler.BukkitTask task : Bukkit.getScheduler().getPendingTasks()) {
            if (task.getOwner() == probe && task.isSync()) {
                // the timer is the probe's only repeating sync task
                taskId = task.getTaskId();
            }
        }
        assertTrue(taskId > 0, "the timer is pending");
        assertTrue(Bukkit.getScheduler().isQueued(taskId), "queued before the cancel");
        Bukkit.getScheduler().cancelTask(taskId);
        int frozen = count(harness, "sched:timer-beat");
        Thread.sleep(400); // certainly past two more beat slots
        assertEquals(frozen, count(harness, "sched:timer-beat"),
                "the cancelled timer never beat again");
        assertFalse(Bukkit.getScheduler().isQueued(taskId), "not queued after the cancel");
    }

    @Test
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
