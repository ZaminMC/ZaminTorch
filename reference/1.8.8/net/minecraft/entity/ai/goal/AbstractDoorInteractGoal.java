package net.minecraft.entity.ai.goal;

import net.minecraft.block.Block;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.material.Material;
import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.util.math.BlockPos;

public abstract class AbstractDoorInteractGoal extends Goal {
    protected MobEntity mob;
    protected BlockPos doorPos = BlockPos.ORIGIN;
    protected DoorBlock doorBlock;
    boolean shouldStop;
    float offsetX;
    float offsetZ;

    public AbstractDoorInteractGoal(MobEntity mob) {
        this.mob = mob;
        if (!(mob.getNavigation() instanceof GroundPathNavigation)) {
            throw new IllegalArgumentException("Unsupported mob type for DoorInteractGoal");
        }
    }

    @Override
    public boolean canStart() {
        if (!this.mob.collidingHorizontally) {
            return false;
        }

        GroundPathNavigation groundpathnavigation = (GroundPathNavigation)this.mob.getNavigation();
        Path path = groundpathnavigation.getPath();
        if (path != null && !path.isDone() && groundpathnavigation.canPassThroughDoors()) {
            for (int i = 0; i < Math.min(path.getCurrentIndex() + 2, path.length()); i++) {
                PathNode pathnode = path.getNode(i);
                this.doorPos = new BlockPos(pathnode.x, pathnode.y + 1, pathnode.z);
                if (!(this.mob.squaredDistanceTo(this.doorPos.getX(), this.mob.y, this.doorPos.getZ()) > 2.25)) {
                    this.doorBlock = this.getDoorBlock(this.doorPos);
                    if (this.doorBlock != null) {
                        return true;
                    }
                }
            }

            this.doorPos = new BlockPos(this.mob).up();
            this.doorBlock = this.getDoorBlock(this.doorPos);
            return this.doorBlock != null;
        } else {
            return false;
        }
    }

    @Override
    public boolean shouldContinue() {
        return !this.shouldStop;
    }

    @Override
    public void start() {
        this.shouldStop = false;
        this.offsetX = (float)(this.doorPos.getX() + 0.5F - this.mob.x);
        this.offsetZ = (float)(this.doorPos.getZ() + 0.5F - this.mob.z);
    }

    @Override
    public void tick() {
        float f = (float)(this.doorPos.getX() + 0.5F - this.mob.x);
        float f1 = (float)(this.doorPos.getZ() + 0.5F - this.mob.z);
        float f2 = this.offsetX * f + this.offsetZ * f1;
        if (f2 < 0.0F) {
            this.shouldStop = true;
        }
    }

    private DoorBlock getDoorBlock(BlockPos pos) {
        Block block = this.mob.world.getBlockState(pos).getBlock();
        return block instanceof DoorBlock && block.getMaterial() == Material.WOOD ? (DoorBlock)block : null;
    }
}
