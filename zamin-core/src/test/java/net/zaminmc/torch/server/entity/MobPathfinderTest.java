package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The A*-light search on deterministic worlds: the straight route is the
 * cell line, a wall rounds instead of tunnels, a one-block ledge climbs,
 * a sealed goal yields no route, and the chasing zombie actually crosses
 * a wall line the straight chase used to grind against.
 */
class MobPathfinderTest {

    /** Flat ground at y=4, no obstacles (like the flat test world). */
    private static MobEntity.WorldQuery flatWorld(Position player) {
        return new MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }

            @Override public Position nearestPlayer(double x, double y, double z, double range) {
                return player;
            }
        };
    }

    /**
     * Flat ground at y=4 with a two-tall wall on the x=3 column for z in
     * [-1, 1]: the straight chase from the west grinds into it.
     */
    private static MobEntity.WorldQuery wallWorld(Position player) {
        return new MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                if (y < 4.0) {
                    return true;
                }
                return (int) Math.floor(x) == 3 && (int) Math.floor(z) >= -1
                        && (int) Math.floor(z) <= 1 && y < 6.0;
            }

            @Override public Position nearestPlayer(double x, double y, double z, double range) {
                return player;
            }
        };
    }

    /** Ground y=4 west of x=3, a one-block plateau (y=5) from x=3 east. */
    private static MobEntity.WorldQuery ledgeWorld() {
        return new MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < ((int) Math.floor(x) >= 3 ? 5.0 : 4.0);
            }

            @Override public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }
        };
    }

    private static boolean insideWallCell(Position waypoint) {
        int cx = (int) Math.floor(waypoint.x());
        int cz = (int) Math.floor(waypoint.z());
        return cx == 3 && cz >= -1 && cz <= 1;
    }

    @Test
    void theOpenRouteIsTheCellLineToTheGoal() {
        List<Position> path = MobPathfinder.find(flatWorld(null),
                new Position(0.5, 4.0, 0.5), new Position(4.5, 4.0, 0.5));
        assertFalse(path.isEmpty(), "open ground always routes");
        Position last = path.get(path.size() - 1);
        assertEquals(4.5, last.x(), 0.01, "the route ends at the goal cell");
        assertEquals(4.0, last.y(), 0.01, "the route ends on the surface");
        // Every waypoint stands on solid ground with body headroom (the
        // standable rule) — checked here by the floor test on the stub.
        for (Position waypoint : path) {
            assertTrue(waypoint.y() >= 3.9 && waypoint.y() <= 4.1,
                    "flat-ground waypoints ride the surface: " + waypoint);
        }
    }

    @Test
    void theRouteRoundsTheWallInsteadOfTunneling() {
        List<Position> path = MobPathfinder.find(wallWorld(null),
                new Position(0.5, 4.0, 0.5), new Position(6.5, 4.0, 0.5));
        assertFalse(path.isEmpty(), "a two-tall wall is routable around");
        for (Position waypoint : path) {
            assertFalse(insideWallCell(waypoint),
                    "no waypoint stands inside the wall: " + waypoint);
        }
        Position last = path.get(path.size() - 1);
        assertEquals(6.5, last.x(), 0.01, "the rounded route still ends at the goal");
    }

    @Test
    void theRouteClimbsTheOneBlockLedge() {
        List<Position> path = MobPathfinder.find(ledgeWorld(),
                new Position(0.5, 4.0, 0.5), new Position(4.5, 5.0, 0.5));
        assertFalse(path.isEmpty(), "a one-block ledge is climbable");
        Position last = path.get(path.size() - 1);
        assertEquals(5.0, last.y(), 0.01, "the route tops out on the plateau");
        assertTrue(path.stream().anyMatch(waypoint -> waypoint.y() > 4.5),
                "the route contains the climbing step");
    }

    @Test
    void theSealedGoalYieldsNoRoute() {
        MobEntity.WorldQuery sealed = new MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                // Solid everywhere except the start cell's pocket.
                boolean startPocket = (int) Math.floor(x) == 0
                        && (int) Math.floor(z) == 0 && y >= 4.0 && y < 6.0;
                return !startPocket;
            }

            @Override public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }
        };
        List<Position> path = MobPathfinder.find(sealed,
                new Position(0.5, 4.0, 0.5), new Position(10.5, 4.0, 0.5));
        assertTrue(path.isEmpty(), "a sealed goal has no walking route");
    }

    @Test
    void theSameCellNeedsNoRoute() {
        List<Position> path = MobPathfinder.find(flatWorld(null),
                new Position(0.5, 4.0, 0.5), new Position(0.7, 4.0, 0.6));
        assertTrue(path.isEmpty(), "the straight chase owns the same-cell case");
    }

    @Test
    void theChasingZombieRoundsTheWallTheStraightChaseGrindsOn() {
        Position player = new Position(6.5, 4.0, 0.5);
        MobEntity zombie = new MobEntity(1, MobType.ZOMBIE,
                new Position(0.5, 4.0, 0.5), new Random(7), wallWorld(player));
        int ticks = 0;
        while (ticks < 900) {
            zombie.tick(true);
            ticks++;
            double dx = player.x() - zombie.position().x();
            double dz = player.z() - zombie.position().z();
            if (dx * dx + dz * dz <= 1.2 * 1.2) {
                assertTrue(zombie.position().x() > 3.5,
                        "the zombie crossed the wall line to reach the target");
                return;
            }
        }
        throw new AssertionError("the zombie never rounded the wall (position "
                + zombie.position() + " after " + ticks + " ticks)");
    }
}
