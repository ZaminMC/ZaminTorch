package net.minecraft.entity.ai.pathing;

import net.minecraft.entity.Entity;
import net.minecraft.util.Int2ObjectHashMap;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldView;

public abstract class NodeEvaluator {
    protected WorldView world;
    protected Int2ObjectHashMap<PathNode> nodes = new Int2ObjectHashMap<>();
    protected int entityWidth;
    protected int entityHeight;
    protected int entityDepth;

    public void prepare(WorldView world, Entity entity) {
        this.world = world;
        this.nodes.clear();
        this.entityWidth = MathHelper.floor(entity.width + 1.0F);
        this.entityHeight = MathHelper.floor(entity.height + 1.0F);
        this.entityDepth = MathHelper.floor(entity.width + 1.0F);
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

    public abstract PathNode getStart(Entity entity);

    public abstract PathNode getTarget(Entity entity, double x, double y, double z);

    public abstract int getNeighbors(PathNode[] path, Entity entity, PathNode node, PathNode target, float range);
}
