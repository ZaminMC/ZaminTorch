package net.minecraft.entity.ai.pathing;

import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldView;

public class WalkNodeEvaluator extends NodeEvaluator {
    private boolean canPassThroughDoors;
    private boolean canOpenDoors;
    private boolean canSwim;
    private boolean canFloat;
    private boolean savedCanSwim;

    @Override
    public void prepare(WorldView world, Entity entity) {
        super.prepare(world, entity);
        this.savedCanSwim = this.canSwim;
    }

    @Override
    public void done() {
        super.done();
        this.canSwim = this.savedCanSwim;
    }

    @Override
    public PathNode getStart(Entity entity) {
        int i;
        if (this.canFloat && entity.isInWater()) {
            i = (int)entity.getShape().minY;
            BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable(MathHelper.floor(entity.x), i, MathHelper.floor(entity.z));

            for (Block block = this.world.getBlockState(blockpos$mutable).getBlock();
                block == Blocks.FLOWING_WATER || block == Blocks.WATER;
                block = this.world.getBlockState(blockpos$mutable).getBlock()
            ) {
                blockpos$mutable.set(MathHelper.floor(entity.x), ++i, MathHelper.floor(entity.z));
            }

            this.canSwim = false;
        } else {
            i = MathHelper.floor(entity.getShape().minY + 0.5);
        }

        return this.getNode(MathHelper.floor(entity.getShape().minX), i, MathHelper.floor(entity.getShape().minZ));
    }

    @Override
    public PathNode getTarget(Entity entity, double x, double y, double z) {
        return this.getNode(MathHelper.floor(x - entity.width / 2.0F), MathHelper.floor(y), MathHelper.floor(z - entity.width / 2.0F));
    }

    @Override
    public int getNeighbors(PathNode[] path, Entity entity, PathNode node, PathNode target, float range) {
        int i = 0;
        int j = 0;
        if (this.getBlockingType(entity, node.x, node.y + 1, node.z) == 1) {
            j = 1;
        }

        PathNode pathnode = this.getValidNode(entity, node.x, node.y, node.z + 1, j);
        PathNode pathnode1 = this.getValidNode(entity, node.x - 1, node.y, node.z, j);
        PathNode pathnode2 = this.getValidNode(entity, node.x + 1, node.y, node.z, j);
        PathNode pathnode3 = this.getValidNode(entity, node.x, node.y, node.z - 1, j);
        if (pathnode != null && !pathnode.visited && pathnode.distanceTo(target) < range) {
            path[i++] = pathnode;
        }

        if (pathnode1 != null && !pathnode1.visited && pathnode1.distanceTo(target) < range) {
            path[i++] = pathnode1;
        }

        if (pathnode2 != null && !pathnode2.visited && pathnode2.distanceTo(target) < range) {
            path[i++] = pathnode2;
        }

        if (pathnode3 != null && !pathnode3.visited && pathnode3.distanceTo(target) < range) {
            path[i++] = pathnode3;
        }

        return i;
    }

    private PathNode getValidNode(Entity entity, int x, int y, int z, int neighborBlockingType) {
        PathNode pathnode = null;
        int i = this.getBlockingType(entity, x, y, z);
        if (i == 2) {
            return this.getNode(x, y, z);
        }

        if (i == 1) {
            pathnode = this.getNode(x, y, z);
        }

        if (pathnode == null && neighborBlockingType > 0 && i != -3 && i != -4 && this.getBlockingType(entity, x, y + neighborBlockingType, z) == 1) {
            pathnode = this.getNode(x, y + neighborBlockingType, z);
            y += neighborBlockingType;
        }

        if (pathnode != null) {
            int j = 0;
            int k = 0;

            while (y > 0) {
                k = this.getBlockingType(entity, x, y - 1, z);
                if (this.canSwim && k == -1) {
                    return null;
                }

                if (k != 1) {
                    break;
                }

                if (j++ >= entity.getSafeFallDistance()) {
                    return null;
                }

                if (--y <= 0) {
                    return null;
                }

                pathnode = this.getNode(x, y, z);
            }

            if (k == -2) {
                return null;
            }
        }

        return pathnode;
    }

    private int getBlockingType(Entity entity, int x, int y, int z) {
        return getBlockingType(
            this.world, entity, x, y, z, this.entityWidth, this.entityHeight, this.entityDepth, this.canSwim, this.canOpenDoors, this.canPassThroughDoors
        );
    }

    public static int getBlockingType(
        WorldView world,
        Entity entity,
        int x,
        int y,
        int z,
        int width,
        int height,
        int depth,
        boolean canSwim,
        boolean canOpenDoors,
        boolean canPassThroughDoors
    ) {
        boolean flag = false;
        BlockPos blockpos = new BlockPos(entity);
        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

        for (int i = x; i < x + width; i++) {
            for (int j = y; j < y + height; j++) {
                for (int k = z; k < z + depth; k++) {
                    blockpos$mutable.set(i, j, k);
                    Block block = world.getBlockState(blockpos$mutable).getBlock();
                    if (block.getMaterial() != Material.AIR) {
                        if (block == Blocks.TRAPDOOR || block == Blocks.IRON_TRAPDOOR) {
                            flag = true;
                        } else if (block != Blocks.FLOWING_WATER && block != Blocks.WATER) {
                            if (!canPassThroughDoors && block instanceof DoorBlock && block.getMaterial() == Material.WOOD) {
                                return 0;
                            }
                        } else {
                            if (canSwim) {
                                return -1;
                            }

                            flag = true;
                        }

                        if (entity.world.getBlockState(blockpos$mutable).getBlock() instanceof AbstractRailBlock) {
                            if (!(entity.world.getBlockState(blockpos).getBlock() instanceof AbstractRailBlock)
                                && !(entity.world.getBlockState(blockpos.down()).getBlock() instanceof AbstractRailBlock)) {
                                return -3;
                            }
                        } else if (!block.canWalkThrough(world, blockpos$mutable)
                            && (!canOpenDoors || !(block instanceof DoorBlock) || block.getMaterial() != Material.WOOD)) {
                            if (block instanceof FenceBlock || block instanceof FenceGateBlock || block instanceof WallBlock) {
                                return -3;
                            }

                            if (block == Blocks.TRAPDOOR || block == Blocks.IRON_TRAPDOOR) {
                                return -4;
                            }

                            Material material = block.getMaterial();
                            if (material != Material.LAVA) {
                                return 0;
                            }

                            if (!entity.isInLava()) {
                                return -2;
                            }
                        }
                    }
                }
            }
        }

        return flag ? 2 : 1;
    }

    public void setCanPassThroughDoors(boolean canPassThroughDoors) {
        this.canPassThroughDoors = canPassThroughDoors;
    }

    public void setCanOpenDoors(boolean canOpenDoors) {
        this.canOpenDoors = canOpenDoors;
    }

    public void setCanSwim(boolean canSwim) {
        this.canSwim = canSwim;
    }

    public void setCanFloat(boolean canFloat) {
        this.canFloat = canFloat;
    }

    public boolean canPassThroughDoors() {
        return this.canPassThroughDoors;
    }

    public boolean canFloat() {
        return this.canFloat;
    }

    public boolean canSwim() {
        return this.canSwim;
    }
}
