package net.zaminmc.torch.server.entity.ai.pathing;

import java.util.HashMap;
import java.util.Map;

/**
 * The successor generator — the exact vanilla 1.8.8 NodeEvaluator shape
 * (reference/1.8.8 net/minecraft/entity/ai/pathing/NodeEvaluator.java):
 * the search-scoped node cache keyed by {@link PathNode#hash}, the entity
 * scan box ({@code floor(width + 1)} × {@code floor(height + 1)}) and the
 * four abstract hooks the PathFinder drives (prepare/getStart/getTarget/
 * getNeighbors/done).
 */
public abstract class NodeEvaluator {
    protected PathWorld world;
    protected Map<Integer, PathNode> nodes = new HashMap<>();
    protected int entityWidth;
    protected int entityHeight;
    protected int entityDepth;

    public void prepare(PathWorld world, MobView entity) {
        this.world = world;
        this.nodes.clear();
        this.entityWidth = (int) Math.floor(entity.width() + 1.0F);
        this.entityHeight = (int) Math.floor(entity.height() + 1.0F);
        this.entityDepth = (int) Math.floor(entity.width() + 1.0F);
    }

    public void done() {
    }

    protected PathNode getNode(int x, int y, int z) {
        int i = PathNode.hash(x, y, z);
        PathNode pathnode = this.nodes.get(i);
        if (pathnode == null) {
            pathnode = new PathNode(x, y, z);
            this.nodes.put(i, pathnode);
        }

        return pathnode;
    }

    public abstract PathNode getStart(MobView entity);

    public abstract PathNode getTarget(MobView entity, double x, double y, double z);

    public abstract int getNeighbors(PathNode[] path, MobView entity, PathNode node,
                                     PathNode target, float range);
}
