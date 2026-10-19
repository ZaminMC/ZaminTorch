package net.zaminmc.torch.server.entity.ai.pathing;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE PATHFINDING WORKLOAD'S §22 GATE (Phase 5's own evidence bar): the
 * immutable-input contract, the behavioral equivalence, and the before/
 * after measurements. THE CONTRACT: {@link PathRegion} — the reachable
 * box's material grid captured on the owning lane, one flat byte array;
 * the off-lane search reads only it (§6.5's explicit inputs, §6.4's no
 * live references). THE EQUIVALENCE: the snapshot search and the live
 * search agree node-for-node — reachable targets, the closest-node
 * fallback (the unreachable case that explores the whole reachable set,
 * the bound's own stress test), and both of the engine's follow ranges
 * (the passive 8 and the hostile 16). THE MEASUREMENTS: the capture cost
 * vs the search cost, logged for the ledger's verdict — the numbers that
 * decide whether the offload lane is worth wiring.
 */
class PathRegionContractTest {

    // ------------------------------------------------ the synthetic world

    /** A cell-map world: explicit materials over a solid ground level. */
    private static final class TestWorld implements PathWorld {
        private final Map<Long, CellMaterial> cells = new HashMap<>();
        private int groundLevel = Integer.MIN_VALUE; // y <= groundLevel is solid

        TestWorld put(int x, int y, int z, CellMaterial material) {
            this.cells.put(key(x, y, z), material);
            return this;
        }

        TestWorld ground(int level) {
            this.groundLevel = level;
            return this;
        }

        private static long key(int x, int y, int z) {
            return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
        }

        @Override
        public CellMaterial materialAt(int x, int y, int z) {
            CellMaterial explicit = cells.get(key(x, y, z));
            if (explicit != null) {
                return explicit;
            }
            return y <= groundLevel ? CellMaterial.SOLID : CellMaterial.AIR;
        }
    }

    private static MobView mob(double x, double y, double z) {
        return new MobView() {
            @Override public double x() { return x; }
            @Override public double y() { return y; }
            @Override public double z() { return z; }
            @Override public double width() { return 0.6; }
            @Override public double height() { return 1.8; }
            @Override public boolean onGround() { return true; }
            @Override public boolean inWater() { return false; }
            @Override public boolean inLava() { return false; }
        };
    }

    private static PathFinder finder() {
        return new PathFinder(new WalkNodeEvaluator());
    }

    /**
     * The benchmark scene: a wall with a two-cell gap (the detour the A*
     * must find), a fence line (the 1.5-tall barrier class), a water pool
     * (the swimmable class), and open ground — the material families the
     * evaluator branches on, all inside the range-8 reachable box.
     */
    private static TestWorld scene() {
        TestWorld world = new TestWorld().ground(4);
        // the wall: x = 6, z from -6..6, with a gap at z = 2..3
        for (int z = -6; z <= 6; z++) {
            if (z != 2 && z != 3) {
                for (int y = 5; y <= 7; y++) {
                    world.put(6, y, z, CellMaterial.SOLID);
                }
            }
        }
        // the fence line west of the mob (the -3 class)
        for (int x = -4; x <= 2; x++) {
            world.put(x, 5, -4, CellMaterial.FENCE);
        }
        // the water pool east of the wall (the swimmable class)
        for (int x = 7; x <= 10; x++) {
            for (int z = 4; z <= 7; z++) {
                world.put(x, 5, z, CellMaterial.WATER);
                world.put(x, 4, z, CellMaterial.SOLID);
            }
        }
        return world;
    }

    // ------------------------------------------------ the contract tests

    @Test
    void theSnapshotSearchMatchesTheLiveSearchNodeForNode() {
        for (float range : new float[] {8.0f, 16.0f}) {
            TestWorld world = scene();
            MobView walker = mob(0.5, 5.0, 0.5);

            // The reachable target: the range-8 band reaches the direct
            // south-east cell (the cap 2r = 16 covers the 8-cost diagonal);
            // the range-16 band reaches through the wall's gap and around
            // the pool (the ~22-cost detour, inside the cap 32) — the
            // material families the evaluator branches on, both bands.
            double targetX = range >= 16 ? 10.5 : 4.5;
            double targetZ = range >= 16 ? 8.5 : 4.5;
            Path live = finder().findPath(world, walker, targetX, 5.0, targetZ, range);
            assertNotNull(live, "the live search found a route at range " + range);
            PathRegion region = PathRegion.capture(world, walker.x(), walker.y(), walker.z(),
                    range);
            Path snapshot = finder().findPath(region, walker, targetX, 5.0, targetZ, range);
            assertNotNull(snapshot, "the snapshot search found a route at range " + range);
            assertTrue(live.equals(snapshot),
                    "the reachable-target route agrees node-for-node at range " + range);
            assertEquals(live.length(), snapshot.length(), "the route lengths agree");

            // The unreachable target behind a full wall: the closest-node
            // fallback explores the whole reachable set — the box bound's
            // own stress test (a too-small box would diverge here). The
            // wall spans the whole reachable box at BOTH ranges and sits
            // between the mob and the in-range target (the vanilla neighbor
            // gate only proposes nodes within the range of the target, so
            // the target itself must sit inside the band).
            TestWorld walled = scene();
            for (int z = -40; z <= 40; z++) {
                for (int y = 5; y <= 8; y++) {
                    walled.put(4, y, z, CellMaterial.SOLID);
                }
            }
            Path liveFallback = finder().findPath(walled, walker, 5.5, 5.0, 0.5, range);
            assertNotNull(liveFallback,
                    "the fallback search left the start at range " + range);
            PathRegion walledRegion = PathRegion.capture(walled, walker.x(), walker.y(),
                    walker.z(), range);
            Path snapshotFallback = finder().findPath(walledRegion, walker, 5.5, 5.0, 0.5,
                    range);
            assertTrue(liveFallback.equals(snapshotFallback),
                    "the unreachable-target fallback agrees node-for-node at range " + range);

            // The out-of-reach target (beyond the range band): both refuse.
            TestWorld far = scene();
            assertNull(finder().findPath(far, walker, 200.5, 5.0, 200.5, range),
                    "the beyond-range search answers null at range " + range);
        }
    }

    @Test
    void theRegionAnswersAirOutsideTheBoxAndTheMaterialsInside() {
        TestWorld world = scene();
        PathRegion region = PathRegion.capture(world, 0.5, 5.0, 0.5, 8.0f);
        // Inside the box: the wall's solid, the gap's air, the fence, the water
        assertEquals(CellMaterial.SOLID, region.materialAt(6, 6, 0), "the wall reads solid");
        assertEquals(CellMaterial.AIR, region.materialAt(6, 6, 2), "the gap reads air");
        assertEquals(CellMaterial.FENCE, region.materialAt(0, 5, -4), "the fence reads fence");
        assertEquals(CellMaterial.WATER, region.materialAt(8, 5, 5), "the pool reads water");
        assertEquals(CellMaterial.SOLID, region.materialAt(8, 4, 5), "the pool bed reads solid");
        // Outside the box: the AIR answer (the unreachable cells)
        assertEquals(CellMaterial.AIR,
                region.materialAt(region.minX() - 1, 5, 0), "west of the box answers air");
        assertEquals(CellMaterial.AIR,
                region.materialAt(0, region.minY() - 1, 0), "below the box answers air");
        assertEquals(CellMaterial.AIR,
                region.materialAt(0, 5, region.minZ() - 1), "north of the box answers air");
        // The box bounds themselves: the capture spans the reachable set
        // (2r + 2 per axis) around the mob — the AIR-outside answer's own
        // soundness margin
        int reach = (int) Math.ceil(8.0f * 2) + 2;
        assertTrue(region.minX() <= 0 - 2 * 8 && region.minX() + region.sizeX() - 1 >= 2 * 8,
                "the box covers the reachable X band");
        assertEquals(2 * reach + 2, region.sizeX(), "the box spans the margin-banded X set");
        // The footprint: the snapshot's own cell count (the memory contract)
        assertTrue(region.cellCount() > 0 && region.cellCount() < 200_000,
                "the region's footprint stays bounded: " + region.cellCount() + " cells");
    }

    // ------------------------------------------------ the measurement gate

    /**
     * THE MEASUREMENTS (the §22 gate's before/after bar, logged for the
     * ledger): the live search's cost vs the capture's cost vs the
     * snapshot search's cost, over the engine's two follow ranges. The
     * numbers decide the offload verdict — the lane is worth wiring only
     * where the capture + the parallel search beat the on-lane search.
     */
    @Test
    void theMeasurementHarnessRecordsTheCosts() {
        for (float range : new float[] {8.0f, 16.0f}) {
            TestWorld world = scene();
            MobView walker = mob(0.5, 5.0, 0.5);
            double targetX = range >= 16 ? 10.5 : 4.5;
            double targetZ = range >= 16 ? 8.5 : 4.5;

            // warmup both paths (the JIT's own band)
            for (int i = 0; i < 50; i++) {
                finder().findPath(world, walker, targetX, 5.0, targetZ, range);
                PathRegion warm = PathRegion.capture(world, walker.x(), walker.y(),
                        walker.z(), range);
                finder().findPath(warm, walker, targetX, 5.0, targetZ, range);
            }

            final int runs = 200;
            long liveNanos = timedMedian(runs, () ->
                    finder().findPath(world, walker, targetX, 5.0, targetZ, range));
            long captureNanos = timedMedian(runs, () ->
                    PathRegion.capture(world, walker.x(), walker.y(), walker.z(), range));
            PathRegion region = PathRegion.capture(world, walker.x(), walker.y(),
                    walker.z(), range);
            long snapshotNanos = timedMedian(runs, () ->
                    finder().findPath(region, walker, targetX, 5.0, targetZ, range));

            System.out.printf(
                    "[path-region] range %.0f: live search %,d ns | capture %,d ns"
                            + " | snapshot search %,d ns | region %,d cells%n",
                    range, liveNanos, captureNanos, snapshotNanos, region.cellCount());

            // The sanity bar (the measurement's own guard): the snapshot
            // search and the live search still agree after the timing runs.
            assertTrue(finder().findPath(world, walker, targetX, 5.0, targetZ, range)
                    .equals(finder().findPath(region, walker, targetX, 5.0, targetZ, range)),
                    "the equivalence holds at range " + range);
        }
    }

    /** The median of N timed runs (the outlier-resistant central cost). */
    private static long timedMedian(int runs, Runnable action) {
        long[] samples = new long[runs];
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            action.run();
            samples[i] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(samples);
        return samples[runs / 2];
    }
}
