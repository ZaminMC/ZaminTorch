package net.zaminmc.torch.server.entity.ai.pathing;

/**
 * The A* search — the exact vanilla 1.8.8 PathFinder (reference/1.8.8
 * net/minecraft/entity/ai/pathing/PathFinder.java): the binary heap loop,
 * the g-cost cap ({@code f < range * 2}), the evaluator's visited pruning,
 * and the historical closest-node fallback — when the target itself is
 * unreachable the search returns the best node it reached (the mob paths as
 * close as it can get, the behavior that makes zombies crowd the wall
 * between them and the player), and a search that never left the start
 * returns null.
 */
public class PathFinder {
    private final BinaryHeap heap = new BinaryHeap();
    private final PathNode[] neighbors = new PathNode[32];
    private final NodeEvaluator nodeEvaluator;

    public PathFinder(NodeEvaluator nodeEvaluator) {
        this.nodeEvaluator = nodeEvaluator;
    }

    public Path findPath(PathWorld world, MobView entity, MobView target, float range) {
        return this.findPath(world, entity, target.x(), target.y(), target.z(), range);
    }

    public Path findPath(PathWorld world, MobView entity, double x, double y, double z, float range) {
        this.heap.clear();
        this.nodeEvaluator.prepare(world, entity);
        PathNode pathnode = this.nodeEvaluator.getStart(entity);
        PathNode pathnode1 = this.nodeEvaluator.getTarget(entity, x, y, z);
        Path path = this.buildPath(entity, pathnode, pathnode1, range);
        this.nodeEvaluator.done();
        return path;
    }

    private Path buildPath(MobView entity, PathNode start, PathNode target, float range) {
        start.distanceFromStart = 0.0F;
        start.distanceToTarget = start.squaredDistanceTo(target);
        start.weight = start.distanceToTarget;
        this.heap.clear();
        this.heap.insert(start);
        PathNode pathnode = start;

        while (!this.heap.isEmpty()) {
            PathNode pathnode1 = this.heap.pop();
            if (pathnode1.equals(target)) {
                return this.buildPath(start, target);
            }

            if (pathnode1.squaredDistanceTo(target) < pathnode.squaredDistanceTo(target)) {
                pathnode = pathnode1;
            }

            pathnode1.visited = true;
            int i = this.nodeEvaluator.getNeighbors(this.neighbors, entity, pathnode1, target, range);

            for (int j = 0; j < i; j++) {
                PathNode pathnode2 = this.neighbors[j];
                float f = pathnode1.distanceFromStart + pathnode1.squaredDistanceTo(pathnode2);
                if (f < range * 2.0F && (!pathnode2.isInHeap() || f < pathnode2.distanceFromStart)) {
                    pathnode2.prev = pathnode1;
                    pathnode2.distanceFromStart = f;
                    pathnode2.distanceToTarget = pathnode2.squaredDistanceTo(target);
                    if (pathnode2.isInHeap()) {
                        this.heap.setWeight(pathnode2, pathnode2.distanceFromStart + pathnode2.distanceToTarget);
                    } else {
                        pathnode2.weight = pathnode2.distanceFromStart + pathnode2.distanceToTarget;
                        this.heap.insert(pathnode2);
                    }
                }
            }
        }

        return pathnode == start ? null : this.buildPath(start, pathnode);
    }

    private Path buildPath(PathNode start, PathNode target) {
        int i = 1;

        for (PathNode pathnode = target; pathnode.prev != null; pathnode = pathnode.prev) {
            i++;
        }

        PathNode[] apathnode = new PathNode[i];
        PathNode pathnode1 = target;

        for (apathnode[--i] = pathnode1; pathnode1.prev != null; apathnode[--i] = pathnode1) {
            pathnode1 = pathnode1.prev;
        }

        return new Path(apathnode);
    }
}
