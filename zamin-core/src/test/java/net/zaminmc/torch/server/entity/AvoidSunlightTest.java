package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.entity.ai.pathing.CellMaterial;
import net.zaminmc.torch.server.entity.ai.pathing.MobNavigation;
import net.zaminmc.torch.server.entity.ai.pathing.MobView;
import net.zaminmc.torch.server.entity.ai.pathing.Path;
import net.zaminmc.torch.server.entity.ai.pathing.PathNode;
import net.zaminmc.torch.server.entity.ai.pathing.PathWorld;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The skeleton's sun-shade flag (Slice TF-7f — AvoidSunlightGoal 1:1,
 * reference/1.8.8 net/minecraft/entity/ai/goal/AvoidSunlightGoal over
 * GroundPathNavigation lines 16/61-77/215-217): while the sun stands the
 * goal arms the navigation's avoid-sunlight trim and drives nothing else;
 * a body already under the open sky keeps its whole route, a shaded
 * body's route cuts at the last shaded node before the first
 * sky-exposed one ({@code truncate(i - 1)}), and the sunset's stop()
 * clears the flag. The goal rides the float's additive band — the
 * reference's flag-free mutex set — so EVERY goal's path gets the trim,
 * whatever owns the body.
 */
class AvoidSunlightTest {

    // ---- the structural trim (GroundPathNavigation.trimPath lines 61-77) ----

    /** A 0.6-wide body standing in the hall below the roof line. */
    private static final class Body implements MobView {
        double x = 2.5;
        double y = 64.0;
        double z = 2.5;

        @Override public double x() { return x; }
        @Override public double y() { return y; }
        @Override public double z() { return z; }
        @Override public double width() { return 0.6; }
        @Override public double height() { return 1.95; }
        @Override public boolean onGround() { return true; }
        @Override public boolean inWater() { return false; }
        @Override public boolean inLava() { return false; }
    }

    /** The roof line: cells at y >= 66 stand in the open, below is a hall. */
    private static final class RoofWorld implements PathWorld {
        @Override public CellMaterial materialAt(int x, int y, int z) {
            return CellMaterial.AIR;
        }

        @Override public boolean hasSkyAccess(int x, int y, int z) {
            return y >= 66;
        }
    }

    private static Path path(int... cells) {
        PathNode[] nodes = new PathNode[cells.length / 3];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = new PathNode(cells[i * 3], cells[i * 3 + 1], cells[i * 3 + 2]);
        }
        return new Path(nodes);
    }

    @Test
    void theTrimCutsTheRouteAtTheSkyBoundary() {
        Body body = new Body();
        MobNavigation nav = new MobNavigation(body, new RoofWorld(), 16.0f);
        nav.setAvoidSunLight(true);
        // The hall route climbing into the open: the first three nodes sit
        // under the roof, the fourth breaks into the sky — the cut lands at
        // the third's index (truncate(i - 1), i the first sky node).
        assertTrue(nav.moveAlong(path(2, 64, 2, 3, 64, 3, 4, 65, 4, 5, 66, 5), 1.0));
        assertEquals(2, nav.getPath().length(),
                "the route cut at the last shaded node (truncate(i - 1))");
        assertEquals(0, nav.getPath().getCurrentIndex(), "the walk index unmoved");
    }

    @Test
    void theOpenSkyBodyKeepsItsWholeRoute() {
        Body body = new Body();
        body.y = 66.0; // the body stands in the open — its own cell reads sky
        MobNavigation nav = new MobNavigation(body, new RoofWorld(), 16.0f);
        nav.setAvoidSunLight(true);
        assertTrue(nav.moveAlong(path(2, 66, 2, 3, 66, 3, 4, 67, 4), 1.0));
        assertEquals(3, nav.getPath().length(),
                "nothing left to protect — the whole route stands");
    }

    @Test
    void theUnflaggedRouteNeverTrims() {
        Body body = new Body();
        MobNavigation nav = new MobNavigation(body, new RoofWorld(), 16.0f);
        assertTrue(nav.moveAlong(path(2, 64, 2, 5, 66, 5), 1.0));
        assertEquals(2, nav.getPath().length(),
                "the flag off — the sky nodes stand");
    }

    @Test
    void theAllSkyRouteSpendsItself() {
        Body body = new Body();
        MobNavigation nav = new MobNavigation(body, new RoofWorld(), 16.0f);
        nav.setAvoidSunLight(true);
        // Every node in the open: truncate(0 - 1) — the reference's own
        // arithmetic leaves the length at -1 (moveAlong's empty check reads
        // 0, the done check reads the index past it — the route is spent).
        assertTrue(nav.moveAlong(path(2, 66, 2, 3, 66, 3), 1.0));
        assertEquals(-1, nav.getPath().length(),
                "the reference's truncate(i - 1) at i 0");
        assertTrue(nav.getPath().isDone(), "the spent route is done at once");
    }

    // ---- the goal arms (AvoidSunlightGoal canStart/start/stop) ----

    /**
     * The stub world: a solid floor below y=8, a sunny flag, and a
     * sky-access law by the x half — the west (x < 3) stands open, the
     * east under a roof. No players, no burning: nothing claims the
     * body but the stroll, whose west-bound routes are the trim's own
     * proof surface.
     */
    private static final class SunWorld implements MobEntity.WorldQuery {
        boolean sunny = true;

        @Override public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override public boolean isSunny() {
            return sunny;
        }

        @Override public boolean hasSkyAccess(int x, int y, int z) {
            return x < 3; // the open west half
        }
    }

    @Test
    void theSunnySkeletonArmsTheFlagAndTheSunsetClearsIt() {
        SunWorld world = new SunWorld();
        MobEntity skeleton = new MobEntity(600, MobType.SKELETON,
                new Position(5.5, 8.0, 5.5), new Random(11L), world);
        for (int i = 0; i < 20; i++) {
            skeleton.tick(true);
        }
        assertTrue(skeleton.avoidingSunlight(),
                "the sunny claim armed the trim flag (start line 23)");

        // The sunset's stop() arm (line 27): the mind-tick default clear
        // drops the flag the moment the claim stops holding.
        world.sunny = false;
        for (int i = 0; i < 20; i++) {
            skeleton.tick(true);
            assertFalse(skeleton.avoidingSunlight(),
                    "the sunset's stop() cleared the flag");
        }
    }

    @Test
    void theOtherKindsNeverArmTheSunShade() {
        SunWorld world = new SunWorld();
        MobEntity zombie = new MobEntity(601, MobType.ZOMBIE,
                new Position(5.5, 8.0, 5.5), new Random(12L), world);
        for (int i = 0; i < 20; i++) {
            zombie.tick(true);
        }
        assertFalse(zombie.avoidingSunlight(),
                "the registration is the skeleton's own (SkeletonEntity line 49)");
    }

    @Test
    void theShadedStrollNeverStepsIntoTheSun() {
        SunWorld world = new SunWorld();
        MobEntity skeleton = new MobEntity(602, MobType.SKELETON,
                new Position(5.5, 8.0, 5.5), new Random(13L), world);
        double furthestWest = 5.5;
        double furthestEast = 5.5;
        for (int i = 0; i < 300; i++) {
            skeleton.tick(true);
            furthestWest = Math.min(furthestWest, skeleton.position().x());
            furthestEast = Math.max(furthestEast, skeleton.position().x());
            assertTrue(skeleton.avoidingSunlight(),
                    "the flag stood through every tick of the sunny window");
        }
        assertTrue((5.5 - furthestWest) + (furthestEast - 5.5) > 0.25,
                "the stroll walked at all (west " + furthestWest + ", east "
                        + furthestEast + ")");
        assertTrue(furthestWest > 2.5,
                "the trimmed routes never walked the body into the open half"
                        + " (reached x " + furthestWest + ")");
    }
}
