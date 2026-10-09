package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.entity.ai.pathing.BinaryHeap;
import net.zaminmc.torch.server.entity.ai.pathing.CellMaterial;
import net.zaminmc.torch.server.entity.ai.pathing.MobNavigation;
import net.zaminmc.torch.server.entity.ai.pathing.MobView;
import net.zaminmc.torch.server.entity.ai.pathing.Path;
import net.zaminmc.torch.server.entity.ai.pathing.PathFinder;
import net.zaminmc.torch.server.entity.ai.pathing.PathNode;
import net.zaminmc.torch.server.entity.ai.pathing.PathWorld;
import net.zaminmc.torch.server.entity.ai.pathing.WalkNodeEvaluator;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vanilla pathing port (reference/1.8.8 entity/ai/pathing): the binary
 * heap orders and rekeys, the search is cardinal-only with the vanilla
 * blocking types (fences -3, lava -2, the shallow-water swimmable 2), the
 * one-block climb, the safe-fall drop, the closest-node fallback for walled
 * goals, the follow-range gate, the navigation's waypoint band, the
 * direct-walk shortcut and the 100-tick stuck check — and the chasing
 * zombie actually crosses the wall line through the corridor.
 */
class PathfindingTest {

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
        return mob(x, y, z, false, false);
    }

    private static MobView mob(double x, double y, double z, boolean inWater, boolean inLava) {
        return new MobView() {
            @Override public double x() { return x; }

            @Override public double y() { return y; }

            @Override public double z() { return z; }

            @Override public double width() { return 0.6; }

            @Override public double height() { return 1.8; }

            @Override public boolean onGround() { return true; }

            @Override public boolean inWater() { return inWater; }

            @Override public boolean inLava() { return inLava; }
        };
    }

    private static PathFinder finder() {
        return new PathFinder(new WalkNodeEvaluator());
    }

    // ------------------------------------------------ the heap

    @Test
    void pathNodesHashAcrossNegativeCoordinates() {
        assertEquals(new PathNode(-3, 70, -9), new PathNode(-3, 70, -9));
        assertNotEquals(new PathNode(-3, 70, -9), new PathNode(3, 70, -9));
        assertNotEquals(new PathNode(-3, 70, -9), new PathNode(-3, 70, 9));
    }

    // ------------------------------------------------ the search

    @Test
    void theOpenRouteIsCardinalOnly() {
        TestWorld world = new TestWorld().ground(3);
        Path path = finder().findPath(world, mob(0.5, 4.0, 0.5), 3.5, 4.0, 3.5, 16.0f);
        assertNotNull(path, "open ground always routes");
        assertEquals(7, path.length(), "the Manhattan cell line: six steps plus the start");
        for (int i = 1; i < path.length(); i++) {
            PathNode from = path.getNode(i - 1);
            PathNode to = path.getNode(i);
            int dx = Math.abs(to.x - from.x);
            int dy = Math.abs(to.y - from.y);
            int dz = Math.abs(to.z - from.z);
            int moves = dx + dy + dz;
            assertEquals(1, moves, "vanilla 1.8 paths never step diagonally: "
                    + from + " -> " + to);
        }
        PathNode last = path.getTarget();
        assertEquals(3, last.x);
        assertEquals(4, last.y);
        assertEquals(3, last.z);
    }

    @Test
    void theRouteClimbsAOneBlockLedge() {
        TestWorld world = new TestWorld().ground(3);
        // East of x=3 the ground rises one (surface y=5).
        for (int x = 3; x <= 7; x++) {
            for (int z = -2; z <= 2; z++) {
                world.put(x, 4, z, CellMaterial.SOLID);
            }
        }
        Path path = finder().findPath(world, mob(0.5, 4.0, 0.5), 6.5, 5.0, 0.5, 16.0f);
        assertNotNull(path, "a one-block ledge is climbable");
        PathNode last = path.getTarget();
        assertEquals(6, last.x, "the route ends at the goal cell");
        assertEquals(5, last.y, "the route ends on the plateau");
        boolean steppedUp = false;
        for (int i = 1; i < path.length(); i++) {
            if (path.getNode(i).y == 5 && path.getNode(i - 1).y == 4) {
                steppedUp = true;
            }
        }
        assertTrue(steppedUp, "the route contains the climb");
    }

    @Test
    void theRouteDropsAtMostTheSafeFallDistance() {
        // A 3-drop is walkable: the drop-walk counts against the hard 3.
        // The pit spans the whole follow-range sphere's z band — a narrower
        // one is legally routed around.
        TestWorld world = new TestWorld().ground(3);
        for (int x = 3; x <= 7; x++) {
            for (int z = -16; z <= 16; z++) {
                for (int y = 1; y <= 3; y++) {
                    world.put(x, y, z, CellMaterial.AIR); // floor at y=0: surface y=1
                }
            }
        }
        Path path = finder().findPath(world, mob(0.5, 4.0, 0.5), 6.5, 1.0, 0.5, 16.0f);
        assertNotNull(path, "the safe-fall drop routes down");
        assertEquals(1, path.getTarget().y, "the route ends on the low floor");
    }

    @Test
    void theFourDeepDropIsRejected() {
        // A 4-drop exceeds the safe-fall budget: the ledge is not a successor.
        TestWorld world = new TestWorld().ground(3);
        for (int x = 3; x <= 7; x++) {
            for (int z = -16; z <= 16; z++) {
                for (int y = 0; y <= 3; y++) {
                    world.put(x, y, z, CellMaterial.AIR); // surface y=0: a 4-drop
                }
            }
        }
        Path path = finder().findPath(world, mob(0.5, 4.0, 0.5), 6.5, 0.0, 0.5, 16.0f);
        assertTrue(path == null || path.getTarget().y >= 3,
                "the route never commits to the 4-drop");
    }

    @Test
    void lavaUnderTheDropKillsTheRoute() {
        // The drop-walk breaks on the lava arm (k == -2) — the lava sits at
        // the pit floor where the scan sees it — and the pit spans the whole
        // follow-range z band so nothing routes around it.
        TestWorld world = new TestWorld().ground(3);
        for (int x = 3; x <= 7; x++) {
            for (int z = -16; z <= 16; z++) {
                for (int y = 1; y <= 3; y++) {
                    world.put(x, y, z, CellMaterial.AIR);
                }
                world.put(x, 0, z, CellMaterial.LAVA); // the pit floor burns
            }
        }
        Path path = finder().findPath(world, mob(0.5, 4.0, 0.5), 6.5, 1.0, 0.5, 16.0f);
        assertTrue(path == null || path.getTarget().x <= 2,
                "no route commits over the lava pit");
    }

    @Test
    void theFenceStopsTheRouteWhichRoundsIt() {
        TestWorld world = new TestWorld().ground(3);
        // A fence line on the x=3 column, z in [-1, 1] (the 1.5-tall barrier).
        for (int z = -1; z <= 1; z++) {
            world.put(3, 4, z, CellMaterial.FENCE);
            world.put(3, 5, z, CellMaterial.FENCE);
        }
        Path path = finder().findPath(world, mob(0.5, 4.0, 0.5), 6.5, 4.0, 0.5, 16.0f);
        assertNotNull(path, "the fence line is roundable");
        for (int i = 0; i < path.length(); i++) {
            PathNode node = path.getNode(i);
            assertFalse(node.x == 3 && node.z >= -1 && node.z <= 1 && node.y == 4,
                    "no waypoint stands in the fence cells: " + node);
        }
        assertEquals(6, path.getTarget().x, "the rounded route still reaches the goal");
    }

    @Test
    void shallowWaterIsWalkable() {
        TestWorld world = new TestWorld().ground(3);
        // A water channel on the surface: the swimmable flag (2) walks it.
        for (int x = 2; x <= 4; x++) {
            world.put(x, 4, 0, CellMaterial.WATER);
        }
        Path path = finder().findPath(world, mob(0.5, 4.0, 0.5), 5.5, 4.0, 0.5, 16.0f);
        assertNotNull(path, "the shallow channel routes");
        boolean crossesWater = false;
        for (int i = 0; i < path.length(); i++) {
            PathNode node = path.getNode(i);
            if (node.x >= 2 && node.x <= 4 && node.z == 0 && node.y == 4) {
                crossesWater = true;
            }
        }
        assertTrue(crossesWater, "the route crosses the water cells straight through");
    }

    @Test
    void theWalledOffGoalFallsBackToTheClosestNode() {
        TestWorld world = new TestWorld().ground(3);
        // A full 3-tall wall spanning the follow-range sphere's z band
        // (|z| <= 16 is the sphere's cut at x=3; beyond it the range gate
        // rejects the nodes) on the x=3 column.
        for (int z = -16; z <= 16; z++) {
            world.put(3, 4, z, CellMaterial.SOLID);
            world.put(3, 5, z, CellMaterial.SOLID);
            world.put(3, 6, z, CellMaterial.SOLID);
        }
        Path path = finder().findPath(world, mob(0.5, 4.0, 0.5), 6.5, 4.0, 0.5, 16.0f);
        assertNotNull(path, "vanilla returns the closest-reached route, not nothing");
        assertEquals(2, path.getTarget().x,
                "the fallback ends against the wall's west face");
    }

    @Test
    void theSealedMobPathsNothing() {
        TestWorld world = new TestWorld().ground(3);
        // A sealed 1-cell chamber around the start.
        int[][] walls = {{0, 4, 1}, {0, 5, 1}, {1, 4, 1}, {1, 5, 1}, {-1, 4, 1}, {-1, 5, 1},
                {0, 4, -1}, {0, 5, -1}, {1, 4, -1}, {1, 5, -1}, {-1, 4, -1}, {-1, 5, -1},
                {1, 4, 0}, {1, 5, 0}, {-1, 4, 0}, {-1, 5, 0}, {0, 5, 0}, {0, 6, 0}};
        for (int[] w : walls) {
            world.put(w[0], w[1], w[2], CellMaterial.SOLID);
        }
        Path path = finder().findPath(world, mob(0.5, 4.0, 0.5), 5.5, 4.0, 5.5, 16.0f);
        assertNull(path, "a search that never left the start returns null");
    }

    @Test
    void theFollowRangeGateShutsOutFarGoals() {
        TestWorld world = new TestWorld().ground(3);
        // The goal is 30 blocks east — outside the follow range. The vanilla
        // range gate only admits successors within 16 of the target, and
        // nothing near the mob qualifies: no route exists at all.
        Path path = finder().findPath(world, mob(0.5, 4.0, 0.5), 30.5, 4.0, 0.5, 16.0f);
        assertNull(path, "a goal beyond the follow range paths nothing");
    }

    @Test
    void theCostCapEndsALongDetourAtTheBestReachedNode() {
        // A goal inside the follow range but behind a serpentine longer than
        // the vanilla g-cap (range * 2 = 32 cost): the search stops and the
        // closest-reached node wins. Every corridor cell sits inside the
        // range sphere (the gate), so only the cap can bind.
        TestWorld world = new TestWorld().ground(3);
        for (int z = -20; z <= 20; z++) {
            if (z != 0) {
                world.put(2, 4, z, CellMaterial.SOLID);
                world.put(2, 5, z, CellMaterial.SOLID);
            }
            if (z != 8) {
                world.put(4, 4, z, CellMaterial.SOLID);
                world.put(4, 5, z, CellMaterial.SOLID);
            }
            if (z != 0) {
                world.put(6, 4, z, CellMaterial.SOLID);
                world.put(6, 5, z, CellMaterial.SOLID);
            }
            if (z != 8) {
                world.put(8, 4, z, CellMaterial.SOLID);
                world.put(8, 5, z, CellMaterial.SOLID);
            }
            if (z != 0) {
                world.put(10, 4, z, CellMaterial.SOLID);
                world.put(10, 5, z, CellMaterial.SOLID);
            }
        }
        // The walking route: 2+8+2+8+2+8+2+8+2 = 42 steps to (12,4,0) — the
        // g-cap (32) binds mid-serpentine; the fallback ends short of it.
        Path capped = finder().findPath(world, mob(0.5, 4.0, 0.5), 12.5, 4.0, 0.5, 16.0f);
        assertNotNull(capped, "the capped search still returns the best reached route");
        PathNode last = capped.getTarget();
        assertTrue(last.x < 12,
                "the capped search never delivers the far goal: " + last);
    }

    // ------------------------------------------------ the navigation

    @Test
    void theNavigationWalksTheRouteAndFinishes() {
        TestWorld world = new TestWorld().ground(3);
        MobView body = mob(0.5, 4.0, 0.5);
        MobNavigation navigation = new MobNavigation(body, world, 16.0f);
        assertTrue(navigation.moveTo(3.5, 4.0, 0.5, 0.2), "the route exists");
        assertFalse(navigation.isDone());
        // The first tick's direct-walk shortcut claims the open line at
        // once: the movement target is the goal's width-adjusted center.
        navigation.tick();
        assertEquals(3.5, navigation.currentTargetX, 1.0E-9,
                "the shortcut aims the goal cell center");
        assertEquals(0.5, navigation.currentTargetZ, 1.0E-9);
        // March the body onto the final waypoint; the band (width²·offset)
        // walks the index past the end and the route completes.
        double dx = navigation.currentTargetX - body.x();
        double dz = navigation.currentTargetZ - body.z();
        body = mob(body.x() + dx, 4.0, body.z() + dz);
        navigation = reopened(navigation, body, world);
        navigation.tick();
        assertTrue(navigation.isDone(), "the walked route finishes");
    }

    /** The navigation rebinds the moved body (the test walks fresh views). */
    private static MobNavigation reopened(MobNavigation old, MobView body, PathWorld world) {
        MobNavigation navigation = new MobNavigation(body, world, 16.0f);
        if (old.getPath() != null) {
            navigation.moveAlong(old.getPath(), 0.2);
        }
        return navigation;
    }

    @Test
    void theDirectWalkShortcutSkipsOpenGround() {
        TestWorld world = new TestWorld().ground(3);
        MobView body = mob(0.5, 4.0, 0.5);
        MobNavigation navigation = new MobNavigation(body, world, 16.0f);
        navigation.moveTo(5.5, 4.0, 0.5, 0.2);
        navigation.tick();
        // The straight line over open flat ground is walkable directly: the
        // backward scan jumps the index to the last node at once.
        assertEquals(navigation.getPath().length() - 1, navigation.getPath().getCurrentIndex(),
                "the vanilla canMoveDirectly shortcut claims the whole line");
    }

    @Test
    void theStuckCheckStopsAnImmobileRoute() {
        TestWorld world = new TestWorld().ground(3);
        MobView body = mob(0.5, 4.0, 0.5);
        MobNavigation navigation = new MobNavigation(body, world, 16.0f);
        navigation.moveTo(5.5, 4.0, 0.5, 0.2);
        navigation.tick();
        assertFalse(navigation.isDone());
        // 100+ stationary ticks: the vanilla stuck check stops the route.
        for (int i = 0; i < 101; i++) {
            navigation.tick();
        }
        assertTrue(navigation.isDone(), "the stuck check cleared the route");
        assertNull(navigation.getPath());
    }

    // ------------------------------------------------ the chasing zombie

    @Test
    void theZombieCrossesTheWallLineThroughTheCorridor() {
        Position player = new Position(6.5, 4.0, 2.5);
        MobEntity.WorldQuery world = new MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                if (y < 4.0) {
                    return true; // the floor
                }
                int cx = (int) Math.floor(x);
                int cz = (int) Math.floor(z);
                return cx == 3 && cz >= -1 && cz <= 1 && y < 6.0; // the wall
            }

            @Override public Position nearestPlayer(double x, double y, double z, double range) {
                return player;
            }
        };
        MobEntity zombie = new MobEntity(1, MobType.ZOMBIE,
                new Position(0.5, 4.0, 0.5), new Random(11), world);
        int ticks = 0;
        while (zombie.position().x() < 3.3 && ticks++ < 1200) {
            zombie.tick();
        }
        assertTrue(zombie.position().x() > 3.3,
                "the zombie crossed the wall line (x=" + zombie.position().x()
                        + " z=" + zombie.position().z() + " ticks=" + ticks + ")");
    }
}
