package net.zaminmc.torch.server.profiler;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.concurrent.DomainWorkerPool;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAVE P6-IL'S OPERATOR EVIDENCE RUN (the flag's own acceptance — the
 * §22 discipline: the gate retires on this evidence): a real boot under
 * the dense-item load shape, the itemEntities section measured UNARMED
 * (the default sequential walk) and then ARMED (the physics compute
 * lane over the engine's own worker-pool discipline), the relief
 * printed for the record. THE RE-RANK FACE: the same run prints the
 * whole per-tick section table over the KO-F/KO-G relieved tree (the
 * per-chunk collision fetch + the random-tick apply's chunk cache) —
 * the attribution pass's standing demand that the sections re-rank
 * before any further optimization is picked.
 *
 * <p><b>The assertions are structural, the numbers are the evidence</b>
 * (the DenseOwner harness's own law): the lane ENGAGES (the dispatch
 * counter), the population HOLDS (the lane never loses bodies), and
 * the armed window never costs wildly MORE than the sequential window
 * (the dispatch overhead bound — a pathological dispatch cost would
 * show here); the relief itself is a printed line, never a CI pin (a
 * shared runner's dense tail inflates windows — the bucketing seat's
 * own documented noise guard).</p>
 */
class ParallelItemPhysicsEngagementTest {

    @TempDir
    Path dataDir;

    net.zaminmc.torch.server.EngineServer server;
    PlayerSession alice;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "p6il" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
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
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    /** One sampling window: the snapshot + the tick anchor, taken together. */
    private record Window(Map<String, Long> nanos, long ticks) {
    }

    private Window sample() {
        return new Window(server.profilerSectionNanos(), server.world().totalTicks());
    }

    /** Waits for N new ticks, then samples the window. */
    private Window waitForTicks(int ticks, String description) throws InterruptedException {
        long anchor = server.world().totalTicks();
        await(() -> server.world().totalTicks() >= anchor + ticks, description);
        return sample();
    }

    private String tickPrefix() {
        return "root.levels." + server.config().worldName() + ".tick.";
    }

    /** The window's DELTA table: per-tick nanos for every tick-section. */
    private Map<String, Double> perTickDeltas(Window before, Window after) {
        long ticks = Math.max(1, after.ticks() - before.ticks());
        Map<String, Double> deltas = new LinkedHashMap<>();
        for (Map.Entry<String, Long> row : after.nanos().entrySet()) {
            String key = row.getKey();
            if (!key.startsWith(tickPrefix())) {
                continue;
            }
            long delta = row.getValue() - before.nanos().getOrDefault(key, 0L);
            if (delta > 0) {
                deltas.put(key.substring(tickPrefix().length()), delta / (double) ticks);
            }
        }
        return deltas;
    }

    private static double perTick(Map<String, Double> deltas, String section) {
        return deltas.getOrDefault(section, 0.0);
    }

    /** The worklog evidence table: the window's per-tick costs, top rows first. */
    private static void printEvidence(String title, Map<String, Double> deltas) {
        List<Map.Entry<String, Double>> rows = new ArrayList<>(deltas.entrySet());
        rows.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        System.out.println("=== P6-IL EVIDENCE: " + title + " ===");
        double total = rows.stream()
                .filter(e -> !"tick".equals(e.getKey()))
                .mapToDouble(Map.Entry<String, Double>::getValue).sum();
        System.out.printf("tick-section total: %.1f us/tick%n", total / 1000.0);
        int shown = 0;
        for (Map.Entry<String, Double> row : rows) {
            if ("tick".equals(row.getKey()) || shown >= 10) {
                continue;
            }
            System.out.printf("  %-22s %10.1f us/tick  (%5.1f%%)%n",
                    row.getKey(), row.getValue() / 1000.0,
                    total > 0 ? row.getValue() * 100.0 / total : 0.0);
            shown++;
        }
    }

    /** Drops items on a grid of distinct cells (the DenseOwner shape). */
    private void dropItems(int fromIndex, int toIndexExclusive, int gridX, int gridZ, int columns) {
        for (int i = fromIndex; i < toIndexExclusive; i++) {
            int slot = i - fromIndex;
            double x = gridX + (slot % columns) * 2.0;
            double z = gridZ + (slot / columns) * 2.0;
            server.itemEntities().spawnDropAtBlock(
                    new Position(x, 8.0, z),
                    new net.zaminmc.torch.item.ItemStack(
                            net.zaminmc.torch.server.item.BuiltinItems.DIAMOND,
                            1, 0, null, Map.of()),
                    0);
        }
    }

    /** Waits for the item population to SETTLE (the DenseOwner harness's own law). */
    private int awaitSettledPopulation() throws InterruptedException {
        int last = -1;
        long stableSince = -1;
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            int now = server.itemEntities().all().size();
            if (now == last) {
                if (System.currentTimeMillis() - stableSince >= 1_500) {
                    return now;
                }
            } else {
                last = now;
                stableSince = System.currentTimeMillis();
            }
            Thread.sleep(100);
        }
        return last;
    }

    /**
     * THE OPERATOR EVIDENCE RUN: the dense-item load shape (the same
     * grid the Phase-7 harness rides — the owner-demanded scenario the
     * lane exists for), the itemEntities section measured over the
     * settled population UNARMED and then ARMED over the engine's own
     * worker-count discipline, the relief and the re-ranked whole-table
     * printed for the record.
     */
    @Test
    void theArmedLaneMeasuresItsReliefOverTheDenseShape() throws Exception {
        server.chatService().submitChat(alice, "/debug start");
        Window opened = waitForTicks(2, "the profiler opened");
        Window baselineEnd = waitForTicks(40, "the baseline window");

        // The dense population: two waves, disjoint cells (the DenseOwner
        // harness's own shape — ~1500 settled bodies, far over the lane's
        // 128-body dispatch floor).
        server.ticker().submit(() -> dropItems(0, 600, -62, -62, 31));
        int settled600 = awaitSettledPopulation();
        server.ticker().submit(() -> dropItems(600, 2000, -62, -22, 31));
        int settled = awaitSettledPopulation();
        System.out.println("P6-IL POPULATION: settled at " + settled
                + " items (wave-1 held " + settled600 + ")");

        // THE UNARMED WINDOW: the default sequential walk over the
        // settled population (the lane's pool never wired).
        long unarmedDispatchesBefore = server.itemEntities().parallelDispatchTicks();
        Window unarmedStart = waitForTicks(2, "the unarmed window opens");
        Window unarmedEnd = waitForTicks(60, "the unarmed window");

        // THE ARMED WINDOWS: the lane wired at runtime twice — first over
        // the engine's own conservative worker-count discipline (the
        // sizing the boot gate rides: cores/2 capped at 4 — on a small
        // machine that is ONE worker and the dispatch is pure overhead,
        // an honest measurement of the discipline itself), then over a
        // CORES-SIZED pool (the parked-tick-lane shape: the tick thread
        // sleeps at the join, so the workers own the whole machine — the
        // operator's upper bound on real hardware).
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("P6-IL MACHINE: " + cores + " reported cores; "
                + "the conservative discipline sizes "
                + DomainWorkerPool.defaultWorkerCount() + " worker(s)");
        DomainWorkerPool disciplined = new DomainWorkerPool(
                DomainWorkerPool.defaultWorkerCount());
        DomainWorkerPool wide = null;
        try {
            server.ticker().submit(() -> server.itemEntities().setLanePool(disciplined));
            await(() -> server.itemEntities().parallelDispatchTicks()
                    > unarmedDispatchesBefore, "the disciplined lane dispatches");
            Window disciplinedStart = waitForTicks(2, "the disciplined window opens");
            Window disciplinedEnd = waitForTicks(60, "the disciplined window");
            double disciplinedItems = perTick(perTickDeltas(disciplinedStart, disciplinedEnd),
                    "itemEntities");

            double wideItems = disciplinedItems;
            int wideWorkers = disciplined.workerCount();
            if (cores > 1) {
                DomainWorkerPool widePool = new DomainWorkerPool(cores);
                wide = widePool;
                server.ticker().submit(() -> server.itemEntities().setLanePool(widePool));
                Window wideStart = waitForTicks(2, "the wide window opens");
                Window wideEnd = waitForTicks(60, "the wide window");
                wideItems = perTick(perTickDeltas(wideStart, wideEnd), "itemEntities");
                wideWorkers = wide.workerCount();
            }

            Map<String, Double> baselineDeltas = perTickDeltas(opened, baselineEnd);
            Map<String, Double> unarmedDeltas = perTickDeltas(unarmedStart, unarmedEnd);
            printEvidence("the re-ranked table over the KO-F/KO-G relieved tree "
                    + "(unarmed, " + settled + " items)", unarmedDeltas);

            double baselineItems = perTick(baselineDeltas, "itemEntities");
            double unarmedItems = perTick(unarmedDeltas, "itemEntities");
            System.out.printf("P6-IL GATE LINE: itemEntities baseline %.1fus -> "
                            + "unarmed %.1fus (%.2fus/item at %d)%n",
                    baselineItems / 1000.0, unarmedItems / 1000.0,
                    unarmedItems / settled / 1000.0, settled);
            System.out.printf("P6-IL GATE LINE: armed (the disciplined pool, %d worker) "
                            + "%.1fus (%.2fus/item) — %.2fx%n",
                    disciplined.workerCount(), disciplinedItems / 1000.0,
                    disciplinedItems / settled / 1000.0, unarmedItems / disciplinedItems);
            System.out.printf("P6-IL GATE LINE: armed (the cores-sized pool, %d workers) "
                            + "%.1fus (%.2fus/item) — %.2fx (the parked tick lane hands "
                            + "the workers the whole machine)%n",
                    wideWorkers, wideItems / 1000.0,
                    wideItems / settled / 1000.0, unarmedItems / wideItems);
            System.out.printf("P6-IL RE-RANK LINE: randomTicks %.1fus, mobs %.1fus, "
                            + "itemEntities %.1fus per tick (the relieved tree's "
                            + "standing order)%n",
                    perTick(unarmedDeltas, "randomTicks") / 1000.0,
                    perTick(unarmedDeltas, "mobs") / 1000.0,
                    unarmedItems / 1000.0);

            // THE STRUCTURAL PINS (the §22 discipline: the verdict is over
            // the printed evidence; the pins hold the shape only).
            // The dense cost lands in the item system's own section...
            assertTrue(unarmedItems > Math.max(baselineItems * 4.0, 5_000.0),
                    "the dense item region costs the item system real per-tick time ("
                            + baselineItems / 1000.0 + "us -> " + unarmedItems / 1000.0 + "us)");
            // ...the lane ENGAGED (the dispatch counter moved)...
            long dispatches = server.itemEntities().parallelDispatchTicks();
            assertTrue(dispatches > unarmedDispatchesBefore,
                    "the armed lane rode the pool dispatch (" + dispatches + " total)");
            // ...the population HOLDS (the lane never loses bodies)...
            int nowPopulation = server.itemEntities().all().size();
            assertTrue(Math.abs(nowPopulation - settled) <= 2,
                    "the population holds under the armed lane (" + settled + " -> "
                            + nowPopulation + ")");
            // ...and neither armed window costs wildly MORE than the
            // sequential walk (the dispatch overhead bound — the honest
            // ceiling for a pathological dispatch cost; the relief itself
            // is the printed line above, never a pin).
            assertTrue(disciplinedItems < unarmedItems * 1.5,
                    "the disciplined lane's dispatch overhead stays bounded ("
                            + unarmedItems / 1000.0 + "us -> " + disciplinedItems / 1000.0 + "us)");
            assertTrue(wideItems < unarmedItems * 1.5,
                    "the cores-sized lane's dispatch overhead stays bounded ("
                            + unarmedItems / 1000.0 + "us -> " + wideItems / 1000.0 + "us)");
        } finally {
            server.ticker().submit(() -> server.itemEntities().setLanePool(null));
            await(() -> true, "the unwind pacing tick");
            disciplined.shutdown();
            if (wide != null) {
                wide.shutdown();
            }
            server.chatService().submitChat(alice, "/debug stop");
        }
    }
}
