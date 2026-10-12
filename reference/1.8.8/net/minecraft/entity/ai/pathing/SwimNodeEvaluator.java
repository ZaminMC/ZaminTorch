package net.minecraft.entity.ai.pathing;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldView;

public class SwimNodeEvaluator extends NodeEvaluator {
    @Override
    public void prepare(WorldView world, Entity entity) {
        super.prepare(world, entity);
    }

    @Override
    public void done() {
        super.done();
    }

    @Override
    public PathNode getStart(Entity entity) {
        return this.getNode(MathHelper.floor(entity.getShape().minX), MathHelper.floor(entity.getShape().minY + 0.5), MathHelper.floor(entity.getShape().minZ));
    }

    @Override
    public PathNode getTarget(Entity entity, double x, double y, double z) {
        return this.getNode(MathHelper.floor(x - entity.width / 2.0F), MathHelper.floor(y + 0.5), MathHelper.floor(z - entity.width / 2.0F));
    }

    @Override
    public int getNeighbors(PathNode[] path, Entity entity, PathNode node, PathNode target, float range) {
        int i = 0;

        for (Direction direction : Direction.values()) {
            PathNode pathnode = this.getValidNode(entity, node.x + direction.getOffsetX(), node.y + direction.getOffsetY(), node.z + direction.getOffsetZ());
            if (pathnode != null && !pathnode.visited && pathnode.distanceTo(target) < range) {
                path[i++] = pathnode;
            }
        }

        return i;
    }

    private PathNode getValidNode(Entity entity, int x, int y, int z) {
        int i = this.getBlockingType(entity, x, y, z);
        return i == -1 ? this.getNode(x, y, z) : null;
    }

    private int getBlockingType(Entity entity, int x, int y, int z) {
        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

        for (int i = x; i < x + this.entityWidth; i++) {
            for (int j = y; j < y + this.entityHeight; j++) {
                for (int k = z; k < z + this.entityDepth; k++) {
                    Block block = this.world.getBlockState(blockpos$mutable.set(i, j, k)).getBlock();
                    if (block.getMaterial() != Material.WATER) {
                        return 0;
                    }
                }
            }
        }

        return -1;
    }
}
