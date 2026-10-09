package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * The hostile pathfinder (A*-light): a bounded A* over walkable cells with
 * the vanilla node rules — a solid floor, two clear body cells, a one-block
 * step up, a drop of at most three — ported from the community node-evaluator
 * shape (vanilla PathFinder + WalkNodeEvaluator, the BlueDragonMC goal
 * architecture's movement core) as the engine's own search.
 *
 * <p>The search is deliberately small: a 384-node budget keeps a repath in
 * the microsecond range, and the diagonal rule (both adjacent cardinals must
 * be open) prevents corner clipping. Callers repath on a budget and follow
 * the returned cell centers; when no path exists the caller falls back to
 * the wall-slide detour steering the bodies always had.</p>
 */
public final class MobPathfinder {

    private MobPathfinder() {
    }

    /** The world surface the search walks on (the mob query's walk subset). */
    public interface WalkQuery {
        /** @return whether the point lies inside a collision shape. */
        boolean isSolidAt(double x, double y, double z);
    }

    /** The node budget (the repath cost cap; 384 nodes is well under a millisecond). */
    public static final int MAX_NODES = 384;
    /** The one-block step rule (the vanilla walkable block offset). */
    private static final double MAX_STEP_UP = 1.0;
    /** The drop allowance (the historical fall-distance tolerance). */
    private static final double MAX_DROP = 3.0;
    /** The waypoint arrival band (horizontal blocks). */
    public static final double WAYPOINT_REACH = 0.55;

    private record Node(double x, double y, double z, long cell) {
    }

    /**
     * Finds a walking path from {@code from} to the goal cell under
     * {@code to}. @return the cell-center waypoints (standing surfaces),
     * excluding the start cell and including the goal, or an empty list
     * when no route exists within the budget.
     */
    public static List<Position> find(WalkQuery world, Position from, Position to) {
        long startCell = cellKey(Math.floor(from.x()), Math.floor(from.y()),
                Math.floor(from.z()));
        long goalCell = cellKey(Math.floor(to.x()), Math.floor(to.y()),
                Math.floor(to.z()));
        if (startCell == goalCell) {
            return List.of(); // the straight chase already owns this case
        }

        PriorityQueue<Entry> open = new PriorityQueue<>();
        Map<Long, Double> bestCost = new HashMap<>();
        Map<Long, Long> cameFrom = new HashMap<>();
        Map<Long, Node> nodeAt = new HashMap<>();
        Node start = new Node(from.x(), from.y(), from.z(), startCell);
        bestCost.put(startCell, 0.0);
        open.add(new Entry(start, 0.0, heuristic(from, to)));
        int expanded = 0;

        while (!open.isEmpty() && expanded < MAX_NODES) {
            Entry current = open.poll();
            Node node = current.node;
            if (node.cell() == goalCell) {
                return reconstruct(cameFrom, nodeAt, node, start);
            }
            if (current.g > bestCost.getOrDefault(node.cell(), Double.MAX_VALUE) + 1.0E-9) {
                continue; // a cheaper route already claimed this cell
            }
            expanded++;
            double floorY = node.y();
            for (int[] direction : DIRECTIONS) {
                double dx = direction[0];
                double dz = direction[1];
                double nx = Math.floor(node.x()) + dx + 0.5;
                double nz = Math.floor(node.z()) + dz + 0.5;
                // Diagonals need both adjacent cardinals open (no corner clip).
                if (dx != 0 && dz != 0) {
                    if (world.isSolidAt(Math.floor(node.x()) + dx + 0.5, floorY + 0.1,
                            Math.floor(node.z()) + 0.5)
                            || world.isSolidAt(Math.floor(node.x()) + 0.5, floorY + 0.1,
                            Math.floor(node.z()) + dz + 0.5)) {
                        continue;
                    }
                }
                // The vertical band: one up, same level, three down — the
                // first standable surface from the top wins (the vanilla
                // node's getNeighbors picks the highest walkable).
                double[] bands = {floorY + 1.0, floorY, floorY - 1.0, floorY - 2.0,
                        floorY - 3.0};
                for (double candidateY : bands) {
                    if (candidateY - floorY > MAX_STEP_UP || floorY - candidateY > MAX_DROP) {
                        continue;
                    }
                    if (!standable(world, nx, candidateY, nz)) {
                        continue;
                    }
                    long nextCell = cellKey(Math.floor(nx), Math.floor(candidateY),
                            Math.floor(nz));
                    if (nextCell == node.cell()) {
                        continue; // the drop folded back into the same column
                    }
                    double stepCost = (dx != 0 && dz != 0) ? 1.4142 : 1.0;
                    double nextCost = current.g + stepCost
                            + (candidateY - floorY > 0 ? 0.5 : 0.0); // climbs cost more
                    if (nextCost < bestCost.getOrDefault(nextCell, Double.MAX_VALUE)) {
                        bestCost.put(nextCell, nextCost);
                        cameFrom.put(nextCell, node.cell());
                        Node next = new Node(nx, candidateY, nz, nextCell);
                        nodeAt.put(nextCell, next);
                        Position at = new Position(nx, candidateY, nz);
                        open.add(new Entry(next, nextCost, nextCost + heuristic(at, to)));
                    }
                    break; // the highest standable surface on this column
                }
            }
        }
        return List.of(); // no route (or the budget ran out): the detour fallback
    }

    /** The vanilla walkable test: solid floor, two clear body cells. */
    private static boolean standable(WalkQuery world, double x, double feetY, double z) {
        return world.isSolidAt(x, feetY - 0.1, z)
                && !world.isSolidAt(x, feetY + 0.1, z)
                && !world.isSolidAt(x, feetY + 1.1, z);
    }

    /** The octile heuristic (the admissible A* estimate, diagonal-aware). */
    private static double heuristic(Position from, Position to) {
        double dx = Math.abs(to.x() - from.x());
        double dz = Math.abs(to.z() - from.z());
        double dy = Math.abs(to.y() - from.y());
        double straight = Math.max(dx, dz);
        double diagonal = Math.min(dx, dz);
        return straight - diagonal + diagonal * 1.4142 + dy * 0.5;
    }

    private static long cellKey(double x, double y, double z) {
        // 21 bits per axis packed (worlds are far smaller than 2M blocks).
        return ((long) (x + 1_000_000) << 42) | ((long) (y + 1_000_000) << 21)
                | (long) (z + 1_000_000);
    }

    private static List<Position> reconstruct(Map<Long, Long> cameFrom,
                                              Map<Long, Node> nodeAt,
                                              Node goal, Node start) {
        List<Position> path = new ArrayList<>();
        Node cursor = goal;
        while (cursor != null && cursor.cell() != start.cell()) {
            path.add(new Position(cursor.x(), cursor.y(), cursor.z()));
            Long parentCell = cameFrom.get(cursor.cell());
            cursor = parentCell == null ? null : nodeAt.get(parentCell);
        }
        Collections.reverse(path);
        return path;
    }

    private record Entry(Node node, double g, double priority) implements Comparable<Entry> {
        @Override public int compareTo(Entry other) {
            return Double.compare(priority, other.priority);
        }
    }

    /** The 8-direction neighborhood (the vanilla cardinal + diagonal set). */
    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
    };
}
