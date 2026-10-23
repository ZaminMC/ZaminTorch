package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ML-007 — the attribution load harness (§22's evidence bar: the cycle-3
 * TPS observations — horde alone 18.0, composite 12.0 — carry NO per-
 * subsystem attribution; "the bottleneck attribution (entity AI vs chunk
 * re-stream vs respawn walk) is unknown until B-7's section table exists").
 *
 * <p><b>The load shapes are the cycle-3 observations, reproduced in-process
 * over the real wire</b> (a headless session cannot see the re-stream: the
 * wire writers short-circuit on a dead channel — only a real socket carries
 * the serialization cost): two scripted 1.8.8 clients standing where the
 * engine placed them (the spawn area — real generated ground, scanned never
 * assumed), a 100-zombie horde around them ("+100 zombies with active AI"),
 * and the respawn-kill storm at the observed ~3 Hz cadence (the horde killing
 * the standing player, one death+respawn cycle every ~333 ms). The code-level
 * ground for the storm's re-stream: V18Connection's respawnAt does
 * sent.clear() + sendInitial(center) — EVERY respawn re-sends the FULL
 * view-distance chunk set to the respawned player, wherever they stand —
 * cycle-3's "full chunk re-stream per cycle" (finding B-6) reproduced by
 * construction, not by a distance trick.</p>
 *
 * <p><b>The capture</b>: per-section nanos over window edges (the absolute,
 * undecayed {@code profilerSectionNanos()} face — the same discipline as
 * the Phase-7 dense-owner harness), the whole root fan (the storm's cost
 * may land outside the world's own tick section), the window's wall clock
 * and true TPS, GC counts+time from the MXBeans, heap occupancy, and a
 * best-effort JFR ObjectAllocationSample run aggregated by class. The
 * assertions are STRUCTURAL only (windows produced, the horde's cost lands
 * in a mob section); every number that matters is printed for the
 * attribution report — the gate verdict stays a human decision over the
 * printed evidence, per the §22 discipline.</p>
 */
class AttributionLoadTest extends ProtocolTestBase {

    /** No keep-alive kicks inside the harness window (recorded in the
     * methodology; the scripted clients' keep-alive cost is not part of
     * the measured loads). */
    @Override
    protected long keepAliveIntervalMs() {
        return 10_000_000;
    }

    /** One sampling window: the whole-fan snapshot + the anchors. */
    private record Window(Map<String, Long> nanos, long ticks, long wallMillis,
                          long gcCount, long gcTimeMs, long heapUsed) {
    }

    private Window sample() {
        long gcCount = 0;
        long gcTime = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0, bean.getCollectionCount());
            gcTime += Math.max(0, bean.getCollectionTime());
        }
        Runtime rt = Runtime.getRuntime();
        return new Window(server.profilerSectionNanos(), server.world().totalTicks(),
                System.currentTimeMillis(), gcCount, gcTime,
                rt.totalMemory() - rt.freeMemory());
    }

    /** The harness's own await: the load windows run past the base's 5s
     * cap (the composite at cycle-3's pace was 12 TPS — 120 ticks = 10s). */
    private void awaitLong(java.util.function.BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    /** Waits for N new ticks, then samples. */
    private Window waitForTicks(int ticks, String description) throws InterruptedException {
        long anchor = server.world().totalTicks();
        awaitLong(() -> server.world().totalTicks() >= anchor + ticks, description);
        return sample();
    }

    /** The window's per-tick delta table over the WHOLE root fan (the
     * storm's re-stream may land outside the world's tick section). */
    private Map<String, Double> perTickDeltas(Window before, Window after) {
        long ticks = Math.max(1, after.ticks() - before.ticks());
        Map<String, Double> deltas = new LinkedHashMap<>();
        for (Map.Entry<String, Long> row : after.nanos().entrySet()) {
            String key = row.getKey();
            if ("root".equals(key)) {
                continue; // the nesting parent — its children are the story
            }
            long delta = row.getValue() - before.nanos().getOrDefault(key, 0L);
            if (delta > 0) {
                deltas.put(key.substring("root.".length()), delta / (double) ticks);
            }
        }
        return deltas;
    }

    /** The evidence table for the report: the window's own TPS/GC/heap
     * lines, then the FULL fan (every section with a per-tick delta — the
     * re-stream's cost may sit well below the top rows). */
    private void printEvidence(String title, Window before, Window after,
                               Map<String, Double> deltas) {
        long ticks = Math.max(1, after.ticks() - before.ticks());
        double wall = Math.max(0.001, (after.wallMillis - before.wallMillis) / 1000.0);
        System.out.println("=== ML-007 EVIDENCE: " + title + " ===");
        System.out.printf("window: %d ticks over %.2fs = %.1f TPS (the engine's own pace)%n",
                ticks, wall, ticks / wall);
        System.out.printf("GC: +%d collections, +%d ms; heap used %.1f -> %.1f MB%n",
                after.gcCount - before.gcCount, after.gcTimeMs - before.gcTimeMs,
                before.heapUsed / 1048576.0, after.heapUsed / 1048576.0);
        List<Map.Entry<String, Double>> rows = new ArrayList<>(deltas.entrySet());
        rows.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        for (Map.Entry<String, Double> row : rows) {
            System.out.printf("  %-46s %10.1f us/tick%n", row.getKey(), row.getValue() / 1000.0);
        }
    }

    /** The first non-air block's column height at (x, z) — the ground the
     * load stands on, scanned from the generated chunks (never assumed). */
    private int groundAt(int x, int z) {
        int y = 200;
        while (y > 1) {
            var block = server.world().getBlock(
                    new net.zaminmc.torch.block.BlockPosition(x, y, z));
            if (block != null && !"minecraft:air".equals(block.identifier().value())) {
                return y + 1; // standing on top of the first non-air
            }
            y--;
        }
        return 2;
    }

    /** The hostile-only population (the clear gates and the titles — the
     * never-despawning worldgen animals would drown an all()-count). */
    private long hostileCount() {
        return server.mobs().all().stream().filter(m -> m.type().hostile).count();
    }

    // ---- the respawn-kill storm -------------------------------------------

    private volatile boolean stormArmed = false;
    private volatile boolean stormStop = false;
    private Thread stormDriver;

    /** The storm's completed death+respawn cycles (the evidence line). */
    private final java.util.concurrent.atomic.AtomicInteger stormCycles =
            new java.util.concurrent.atomic.AtomicInteger();

    private void startStormDriver() {
        stormStop = false;
        stormArmed = false;
        stormCycles.set(0);
        stormDriver = new Thread(() -> {
            long lastCycle = System.currentTimeMillis();
            while (!stormStop) {
                try {
                    if (stormArmed && System.currentTimeMillis() - lastCycle >= 333) {
                        PlayerSession victim = server.playerRegistry().byName("Alice").orElse(null);
                        if (victim != null && !victim.dead()) {
                            server.damage(victim, 1000.0f);
                            long cap = System.currentTimeMillis() + 500;
                            while (!victim.dead() && System.currentTimeMillis() < cap) {
                                Thread.sleep(2);
                            }
                            if (victim.dead()) {
                                server.performRespawn(victim);
                                stormCycles.incrementAndGet();
                            }
                        }
                        lastCycle = System.currentTimeMillis();
                    }
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "attribution-storm");
        stormDriver.setDaemon(true);
        stormDriver.start();
    }

    private void stopStormDriver() throws InterruptedException {
        stormArmed = false;
        stormStop = true;
        stormDriver.join(2000);
    }

    /** The wire drain: a real client reads its socket; without the drain
     * the OS buffers fill and the harness stops being honest about the
     * write path. Discards everything; the drain threads end with the
     * sockets (reads throw once the clients close). */
    private Thread drain(TestClient18 client) {
        Thread t = new Thread(() -> {
            try {
                client.setReadTimeout(500);
            } catch (Exception e) {
                return;
            }
            while (true) {
                try {
                    client.readPacket();
                } catch (Exception timeoutOrClosed) {
                    // a 500ms timeout just loops; a closed socket returns
                }
            }
        }, "attribution-drain");
        t.setDaemon(true);
        return t;
    }

    @Test
    void theAttributionPass() throws Exception {
        jdk.jfr.Recording allocation = startAllocationCapture();
        try (TestClient18 alice = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 bob = new TestClient18("127.0.0.1", adapter.boundPort())) {
            alice.sendHandshake(47, 2);
            alice.sendLoginStart("Alice");
            alice.readLoginSuccess();
            alice.readUntilPositionAndLook();
            bob.sendHandshake(47, 2);
            bob.sendLoginStart("Bob");
            bob.readLoginSuccess();
            bob.readUntilPositionAndLook();
            awaitPlayers("Alice", "Bob");
            Thread aliceDrain = drain(alice);
            Thread bobDrain = drain(bob);
            aliceDrain.start();
            bobDrain.start();

            PlayerSession aliceSession = server.playerRegistry().byName("Alice").orElseThrow();
            // The profiler's own gate: op Alice for /debug (level 3).
            server.ticker().submit(() -> aliceSession.setOpLevel(3));
            awaitLong(() -> aliceSession.opLevel() >= 3, "Alice opped");
            // THE ANCHOR: Alice's actual standing spot (the join placement
            // is seed-dependent — the second run anchored the grid on world
            // spawn, outside the players' 32-block no-despawn ring, and the
            // despawn walk removed the whole horde inside the settle: zero
            // at window open, no drops anywhere). The bed-spawn pin: every
            // storm cycle's respawn destination is her standing spot (she
            // stays put; the re-stream happens regardless — respawnAt
            // clears and re-sends the FULL set wherever the destination is).
            Position anchor = aliceSession.position();
            server.ticker().submit(() -> aliceSession.setBedSpawn(anchor));
            waitForTicks(2, "the bed-spawn pin");
            int lx = anchor.toBlockPosition().x();
            int lz = anchor.toBlockPosition().z();
            waitForTicks(60, "the join flush");
            // THE NIGHT: the run's world-clock decision. The first run's own
            // finding — an above-ground zombie horde in daylight is not a
            // load, it is a burnout (the historical sunlight burn consumed
            // all 100 bodies inside the window; the mobs section read the
            // animal baseline). The whole harness runs at night: the burn
            // is off, the natural cycle's ambient-darkness gate is open, and
            // the caps (HOSTILE_CAP 6) bound the cycle's strays while the
            // direct-spawned horde bypasses admission (the cap gates the
            // cycle, never the population — the despawn walk needs 128+.
            server.chatService().submitChat(aliceSession, "/time set night");
            waitForTicks(2, "the night set");

            server.chatService().submitChat(aliceSession, "/debug start");
            Window opened = waitForTicks(2, "the profiler opened");

            // ---- W0: the idle baseline -------------------------------------
            Window baselineEnd = waitForTicks(40, "the baseline window");
            Map<String, Double> baseline = perTickDeltas(opened, baselineEnd);
            printEvidence("W0 baseline at night (2 real-wire players, "
                    + hostileCount() + " hostile strays, no horde)",
                    opened, baselineEnd, baseline);

            // ---- W1: the horde ----------------------------------------------
            spawnHorde(lx, lz);
            awaitLong(() -> hostileCount() >= 100, "the horde landed");
            waitForTicks(40, "the horde settles (target acquisition + the first walk)");
            // The engagement record: where the horde's front stand at the
            // window's open vs where they drifted by its close.
            long hostilesAtOpen = hostileCount();
            Map<Integer, Position> zombiesAtOpen = zombiePositions(5);
            Window w1Start = sample();
            Window hordeEnd = waitForTicks(60, "the horde window");
            Map<String, Double> horde = perTickDeltas(w1Start, hordeEnd);
            printEvidence("W1 horde (hostiles " + hostilesAtOpen + " -> "
                    + hostileCount() + " over the window, 2 observers)",
                    w1Start, hordeEnd, horde);
            printZombieDrift(zombiesAtOpen);

            // Clear the horde. Strays from the natural cycle may persist
            // (the night keeps the ambient-darkness gate open); the gate is
            // hostile-only below twenty (the never-despawning animals stay).
            server.ticker().submit(() -> server.mobs().removeHostiles());
            awaitLong(() -> hostileCount() < 20,
                    "the horde cleared below strays");
            waitForTicks(40, "the post-horde settle (unmeasured)");
            Window w1bStart = sample();
            Window clearedEnd = waitForTicks(40, "the relief window");
            Map<String, Double> postHorde = perTickDeltas(w1bStart, clearedEnd);
            printEvidence("W1b post-horde settle (the relief window, "
                    + hostileCount() + " hostile strays)", w1bStart, clearedEnd, postHorde);

            // ---- W2: the respawn-kill storm ---------------------------------
            startStormDriver();
            stormArmed = true;
            Window w2Start = sample();
            Window stormEnd = waitForTicks(120, "the storm window");
            stormArmed = false;
            stopStormDriver();
            Map<String, Double> storm = perTickDeltas(w2Start, stormEnd);
            printEvidence("W2 respawn-kill storm (" + stormCycles.get()
                    + " cycles at ~3 Hz, full re-stream each, "
                    + hostileCount() + " hostile strays)",
                    w2Start, stormEnd, storm);

            waitForTicks(40, "the post-storm settle (unmeasured)");
            Window w2bStart = sample();
            Window settleEnd = waitForTicks(40, "the post-storm relief window");
            Map<String, Double> postStorm = perTickDeltas(w2bStart, settleEnd);
            printEvidence("W2b post-storm settle", w2bStart, settleEnd, postStorm);

            // ---- W3: the composite (cycle-3's worst case) --------------------
            spawnHorde(lx, lz);
            awaitLong(() -> hostileCount() >= 100, "the second horde landed");
            waitForTicks(40, "the second horde settles");
            startStormDriver();
            stormArmed = true;
            Window w3Start = sample();
            Window compositeEnd = waitForTicks(120, "the composite window");
            stormArmed = false;
            stopStormDriver();
            Map<String, Double> composite = perTickDeltas(w3Start, compositeEnd);
            printEvidence("W3 composite (horde + storm — cycle-3's 12-TPS shape, "
                    + stormCycles.get() + " cycles)", w3Start, compositeEnd, composite);

            // The teardown: clear, stop the profiler (its console table is the
            // whole-run undecayed view — the ML-006 face), close the wires.
            server.ticker().submit(() -> server.mobs().removeHostiles());
            awaitLong(() -> hostileCount() < 20,
                    "the second horde cleared below strays");
            server.chatService().submitChat(aliceSession, "/debug stop");
            waitForTicks(2, "the profiler stopped");

            // Structural pins ONLY (the §22 discipline: the verdict is the
            // report's, not a CI assertion).
            assertTrue2(!baseline.isEmpty(), "the baseline produced sections");
            assertTrue2(horde.entrySet().stream().anyMatch(e -> e.getKey().endsWith(".mobs")
                            && e.getValue() > 0),
                    "the horde's cost landed in a mob section");
            assertTrue2(!storm.isEmpty(), "the storm produced sections");

            stopAllocationCapture(allocation);
        } finally {
            if (allocation != null) {
                try {
                    allocation.close();
                } catch (Exception closeQuiet) {
                    // best-effort only
                }
            }
        }
        awaitEmptyServer();
    }

    /** 100 zombies on a 10x10 grid centered on the load position, each on
     * its own column's ground — the cycle-3 horde shape (pathfinding
     * bodies around the standing player). */
    private void spawnHorde(int lx, int lz) {
        server.ticker().submit(() -> {
            for (int i = 0; i < 100; i++) {
                int x = lx + (int) (((i % 10) - 4.5) * 3.0);
                int z = lz + (int) (((i / 10) - 4.5) * 3.0);
                int ground = groundAt(x, z);
                server.mobs().spawnAt(net.zaminmc.torch.server.entity.MobType.ZOMBIE,
                        new Position(x + 0.5, ground, z + 0.5));
            }
        });
    }

    /** The first N zombies' positions (the engagement record's open). */
    private Map<Integer, Position> zombiePositions(int n) {
        Map<Integer, Position> at = new LinkedHashMap<>();
        for (var mob : server.mobs().all()) {
            if (mob.type().hostile && at.size() < n) {
                at.put(mob.entityId(), mob.position());
            }
        }
        return at;
    }

    /** The engagement record's close: how far the sampled front drifted
     * over the window — the honest note for the report's reader (an idle
     * horde and a hunting horde cost the tick differently). */
    private void printZombieDrift(Map<Integer, Position> atOpen) {
        System.out.println("ML-007 ENGAGEMENT: the horde's front drift over the window");
        for (Map.Entry<Integer, Position> e : atOpen.entrySet()) {
            var mob = server.mobs().byId(e.getKey());
            String state = mob == null
                    ? "gone"
                    : String.format("%.1f blocks",
                            Math.sqrt(mob.position().distanceSquared(e.getValue())));
            System.out.printf("  zombie #%d: %s%n", e.getKey(), state);
        }
    }

    // ---- the JFR allocation capture (best-effort, honestly recorded) ----

    private jdk.jfr.Recording startAllocationCapture() {
        try {
            jdk.jfr.Recording rec = new jdk.jfr.Recording();
            rec.enable("jdk.ObjectAllocationSample");
            rec.start();
            System.out.println("ML-007 ALLOCATION: JFR ObjectAllocationSample capture started");
            return rec;
        } catch (Throwable unavailable) {
            System.out.println("ML-007 ALLOCATION: JFR unavailable on this JVM ("
                    + unavailable + ") — the report records GC+heap only");
            return null;
        }
    }

    private void stopAllocationCapture(jdk.jfr.Recording rec) {
        if (rec == null) {
            return;
        }
        try {
            rec.stop();
            Path dump = dataDir.resolve("allocation.jfr");
            rec.dump(dump);
            Map<String, Long> byClass = new LinkedHashMap<>();
            long total = 0;
            String sizeField = null;
            try (jdk.jfr.consumer.RecordingFile file = new jdk.jfr.consumer.RecordingFile(dump)) {
                while (file.hasMoreEvents()) {
                    var event = file.readEvent();
                    if (!"jdk.ObjectAllocationSample".equals(event.getEventType().getName())) {
                        continue;
                    }
                    if (sizeField == null) {
                        // The JDK's own field name for the sample's size —
                        // introspected once (never assumed: this run's first
                        // attempt at "allocationSize" threw).
                        for (var field : event.getEventType().getFields()) {
                            if ("weight".equals(field.getName())
                                    || "allocationSize".equals(field.getName())
                                    || "size".equals(field.getName())) {
                                sizeField = field.getName();
                                break;
                            }
                        }
                        if (sizeField == null) {
                            System.out.println("ML-007 ALLOCATION: unknown size field — fields: "
                                    + event.getEventType().getFields());
                            return;
                        }
                    }
                    long size = event.getLong(sizeField);
                    String cls = event.getClass("objectClass").getName();
                    byClass.merge(cls, size, Long::sum);
                    total += size;
                }
            }
            System.out.println("=== ML-007 ALLOCATION (ObjectAllocationSample, whole run) ===");
            System.out.printf("sampled total: %.1f MB across %d classes (field: %s)%n",
                    total / 1048576.0, byClass.size(), sizeField);
            byClass.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(15)
                    .forEach(e -> System.out.printf("  %-58s %10.1f MB%n",
                            e.getKey(), e.getValue() / 1048576.0));
            Files.deleteIfExists(dump);
        } catch (Throwable failed) {
            System.out.println("ML-007 ALLOCATION: capture failed ("
                    + failed + ") — recorded as unavailable in the report");
        }
    }

    private static void assertTrue2(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
