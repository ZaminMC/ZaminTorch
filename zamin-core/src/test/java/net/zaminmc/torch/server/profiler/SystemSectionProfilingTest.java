package net.zaminmc.torch.server.profiler;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Phase-7 profiling opener's structural evidence (§22's gate face —
 * "profiling demonstrates a dense-owner bottleneck" needs the per-system
 * nanos separable first): every gameplay system in the tick handler rides
 * its own profiler section, and the measurement surface
 * ({@link EngineProfiler#sectionNanosSnapshot()} / EngineServer's
 * {@code profilerSectionNanos()}) answers ABSOLUTE, UNDECAYED nanos —
 * the same table the /debug report walks as percentages, but frozen for
 * a harness's before/after differencing (the reference's *999/1000 decay
 * belongs to the report walk alone, 1:1).
 *
 * <p><b>The section census:</b> the whole system fan must appear — the
 * census bar is what makes a later dense-owner measurement trustworthy
 * (a missing section would silently hide that system's cost). The stack
 * stays balanced across ticks (the try/finally pairing inside the
 * profiled arm — the engine's §126 addition over the reference's bare
 * calls), so the section set is IDENTICAL across two sampling windows:
 * a torn section stack would nest or drift the names between windows.</p>
 */
class SystemSectionProfilingTest {

    @TempDir
    Path dataDir;

    net.zaminmc.torch.server.EngineServer server;
    PlayerSession alice;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "p7" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        server = new net.zaminmc.torch.server.EngineServer(config);
        server.start();
        var accepted = (net.zaminmc.torch.server.net.EngineBridge.Accepted)
                server.joinRequest(new SilentLink(), "Alice",
                        UUID.nameUUIDFromBytes("OfflinePlayer:Alice".getBytes()));
        alice = accepted.session();
        server.ticker().submit(alice::markPlaying);
        await(() -> alice.state() == net.zaminmc.torch.entity.PlayerState.PLAYING, "playing");
        server.ticker().submit(() -> alice.setOpLevel(3));
        await(() -> alice.opLevel() >= 3, "Alice opped");
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private static final class SilentLink implements net.zaminmc.torch.server.net.ClientLink {
        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }
    }

    private void await(java.util.function.BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    @Test
    void theSnapshotIsAbsoluteAndNeverDecayed() {
        EngineProfiler profiler = new EngineProfiler();
        profiler.start();
        profiler.push("root");
        profiler.push("solo");
        spinMicros(200);
        profiler.pop();
        profiler.push("solo");
        spinMicros(100);
        profiler.pop();
        profiler.pop();
        Map<String, Long> first = profiler.sectionNanosSnapshot();
        long soloFirst = first.getOrDefault("root.solo", 0L);
        assertTrue(soloFirst >= 300_000L, "the solo section carries absolute nanos ("
                + soloFirst + "ns for a >=300us spin)");
        // The snapshot is a PURE READ: taking it never touches the table,
        // so two snapshots with no push/pop between them agree exactly —
        // the differencing method a measurement harness depends on.
        Map<String, Long> second = profiler.sectionNanosSnapshot();
        assertEquals(first, second, "the snapshot is a pure read");
        // The REPORT walk decays the table in place (the reference's own
        // *999/1000, 1:1) — that is the report's behavior, not the
        // snapshot's: a harness that never walks the report reads a
        // merge-only, never-shrinking table.
        assertNotNull(profiler.getResults("root"), "the report walk ran");
        Map<String, Long> afterReport = profiler.sectionNanosSnapshot();
        assertTrue(afterReport.getOrDefault("root.solo", 0L) < soloFirst,
                "the report walk's decay is the vanilla 1:1 behavior ("
                        + soloFirst + " -> " + afterReport.get("root.solo") + ")");
        // An empty table answers an empty map, never null.
        EngineProfiler fresh = new EngineProfiler();
        assertTrue(fresh.sectionNanosSnapshot().isEmpty(), "no sections before any collection");
        profiler.stop();
    }

    @Test
    void theSystemFanRecordsEverySection() throws Exception {
        server.chatService().submitChat(alice, "/debug start");
        long startTicks = server.world().totalTicks();
        await(() -> server.world().totalTicks() >= startTicks + 10, "ten profiled ticks");
        Map<String, Long> firstWindow = server.profilerSectionNanos();

        // The census: the whole system fan must appear under the live
        // world's tick section. A missing row would silently hide that
        // system's cost from any dense-owner measurement.
        String tickPrefix = "root.levels." + server.config().worldName() + ".tick.";
        // regionIntents rides BEFORE the levels swap (tickWorlds' own jobs
        // section, the cross-owner drains inside) — the reference's own
        // placement, kept.
        String jobsPrefix = "root.jobs.";
        List<String> census = List.of(
                "blockUpdates", "redstone", "pistons", "hoppers",
                "daylightDetectors", "dispensers", "tripwire", "tnt", "mining",
                // (The fluids' own section retired with the Wave VI fluids
                // migration: the flow ticks ride the scheduledBlockTicks
                // pass — the reference's own doScheduledTicks surface.)
                "fallingBlocks", "projectiles", "vehicles", "vehicleRiders",
                "mobRiders", "fishing", "furnaces", "chests", "signs",
                "commandBlocks", "spawners", "itemEntities", "experienceOrbs", "mobs",
                "weather", "lightning", "naturalSpawns", "netherSpawns",
                "generationAttachments", "mobPotions", "leashes", "decorations",
                "randomTicks", "brewingStands", "beacons",
                "furnaceViewers", "brewingViewers", "beaconViewers", "enchantingViewers",
                "chestViewers", "hopperViewers", "dispenserViewers", "playerBodies",
                "portalForcers", "relightTransport");
        assertTrue(firstWindow.containsKey(jobsPrefix + "regionIntents"),
                "the region-intents drain rides the jobs section");
        assertTrue(firstWindow.get(jobsPrefix + "regionIntents") > 0L,
                "the region-intents row collected nanos");
        for (String section : census) {
            assertTrue(firstWindow.containsKey(tickPrefix + section),
                    "the section census carries '" + section + "'");
        }
        // Every census row actually collected time (a zero row would mean
        // the section was pushed but the system never ran).
        for (String section : census) {
            assertTrue(firstWindow.get(tickPrefix + section) > 0L,
                    "'" + section + "' collected nanos");
        }

        // The stack stays balanced across windows: no census row can be
        // LOST (a torn stack would swallow a section), no census section
        // can GAIN a child (a torn pop would nest the next system inside
        // the previous section — every profiled section is childless by
        // construction), and conditional sections (the %20 timeSync) are
        // free to appear — the try/finally pairing is what the balance
        // proves.
        await(() -> server.world().totalTicks() >= startTicks + 20, "ten more ticks");
        Map<String, Long> secondWindow = server.profilerSectionNanos();
        for (String section : census) {
            assertTrue(secondWindow.containsKey(tickPrefix + section),
                    "'" + section + "' survived into the second window");
            assertTrue(secondWindow.keySet().stream().noneMatch(key ->
                            key.startsWith(tickPrefix + section + ".")),
                    "'" + section + "' never gains a child (the stack stays balanced)");
        }

        // The per-system rows accumulate (weakly-consistent reads aside, a
        // live system's row cannot shrink — the table is merge-only).
        for (String section : census) {
            assertTrue(secondWindow.get(tickPrefix + section)
                            >= firstWindow.get(tickPrefix + section),
                    "'" + section + "' is merge-only (never shrinks)");
        }
        server.chatService().submitChat(alice, "/debug stop");
    }

    /** Spins roughly the given microseconds (System.nanoTime granularity). */
    private static void spinMicros(int micros) {
        long deadline = System.nanoTime() + micros * 1_000L;
        long sink = 0;
        while (System.nanoTime() < deadline) {
            sink++;
        }
        if (sink < 0) {
            throw new AssertionError("impossible");
        }
    }
}
