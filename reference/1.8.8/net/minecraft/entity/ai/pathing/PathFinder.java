package net.minecraft.entity.ai.pathing;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

public class PathFinder {
    private BinaryHeap heap = new BinaryHeap();
    private PathNode[] neighbors = new PathNode[32];
    private NodeEvaluator nodeEvaluator;

    public PathFinder(NodeEvaluator nodeEvaluator) {
        this.nodeEvaluator = nodeEvaluator;
    }

    public Path findPath(WorldView world, Entity entity, Entity target, float range) {
        return this.findPath(world, entity, target.x, target.getShape().minY, target.z, range);
    }

    public Path findPath(WorldView world, Entity entity, BlockPos target, float range) {
        return this.findPath(world, entity, target.getX() + 0.5F, target.getY() + 0.5F, target.getZ() + 0.5F, range);
    }

    private Path findPath(WorldView world, Entity entity, double x, double y, double z, float range) {
        this.heap.clear();
        this.nodeEvaluator.prepare(world, entity);
        PathNode pathnode = this.nodeEvaluator.getStart(entity);
        PathNode pathnode1 = this.nodeEvaluator.getTarget(entity, x, y, z);
        Path path = this.buildPath(entity, pathnode, pathnode1, range);
        this.nodeEvaluator.done();
        return path;
    }

    private Path buildPath(Entity entity, PathNode start, PathNode target, float range) {
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
