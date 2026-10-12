package net.minecraft.entity.ai.pathing;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.mob.monster.ZombieEntity;
import net.minecraft.entity.living.mob.passive.animal.ChickenEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class GroundPathNavigation extends PathNavigation {
    protected WalkNodeEvaluator nodeEvaluator;
    private boolean avoidSunLight;

    public GroundPathNavigation(MobEntity mobEntity, World world) {
        super(mobEntity, world);
    }

    @Override
    protected PathFinder createPathFinder() {
        this.nodeEvaluator = new WalkNodeEvaluator();
        this.nodeEvaluator.setCanPassThroughDoors(true);
        return new PathFinder(this.nodeEvaluator);
    }

    @Override
    protected boolean canUpdatePath() {
        return this.mob.onGround
            || this.canFloat() && this.isInLiquid()
            || this.mob.isRiding() && this.mob instanceof ZombieEntity && this.mob.vehicle instanceof ChickenEntity;
    }

    @Override
    protected Vec3d getTempPos() {
        return new Vec3d(this.mob.x, this.getSurfaceY(), this.mob.z);
    }

    private int getSurfaceY() {
        if (this.mob.isInWater() && this.canFloat()) {
            int i = (int)this.mob.getShape().minY;
            Block block = this.world.getBlockState(new BlockPos(MathHelper.floor(this.mob.x), i, MathHelper.floor(this.mob.z))).getBlock();
            int j = 0;

            while (block == Blocks.FLOWING_WATER || block == Blocks.WATER) {
                block = this.world.getBlockState(new BlockPos(MathHelper.floor(this.mob.x), ++i, MathHelper.floor(this.mob.z))).getBlock();
                if (++j > 16) {
                    return (int)this.mob.getShape().minY;
                }
            }

            return i;
        } else {
            return (int)(this.mob.getShape().minY + 0.5);
        }
    }

    @Override
    protected void trimPath() {
        super.trimPath();
        if (this.avoidSunLight) {
            if (this.world.hasSkyAccess(new BlockPos(MathHelper.floor(this.mob.x), (int)(this.mob.getShape().minY + 0.5), MathHelper.floor(this.mob.z)))) {
                return;
            }

            for (int i = 0; i < this.path.length(); i++) {
                PathNode pathnode = this.path.getNode(i);
                if (this.world.hasSkyAccess(new BlockPos(pathnode.x, pathnode.y, pathnode.z))) {
                    this.path.truncate(i - 1);
                    return;
                }
            }
        }
    }

    @Override
    protected boolean canMoveDirectly(Vec3d from, Vec3d to, int width, int height, int depth) {
        int i = MathHelper.floor(from.x);
        int j = MathHelper.floor(from.z);
        double d0 = to.x - from.x;
        double d1 = to.z - from.z;
        double d2 = d0 * d0 + d1 * d1;
        if (d2 < 1.0E-8) {
            return false;
        }

        double d3 = 1.0 / Math.sqrt(d2);
        d0 *= d3;
        d1 *= d3;
        width += 2;
        depth += 2;
        if (!this.canWalkOn(i, (int)from.y, j, width, height, depth, from, d0, d1)) {
            return false;
        }

        width -= 2;
        depth -= 2;
        double d4 = 1.0 / Math.abs(d0);
        double d5 = 1.0 / Math.abs(d1);
        double d6 = i * 1 - from.x;
        double d7 = j * 1 - from.z;
        if (d0 >= 0.0) {
            d6++;
        }

        if (d1 >= 0.0) {
            d7++;
        }

        d6 /= d0;
        d7 /= d1;
        int k = d0 < 0.0 ? -1 : 1;
        int l = d1 < 0.0 ? -1 : 1;
        int i1 = MathHelper.floor(to.x);
        int j1 = MathHelper.floor(to.z);
        int k1 = i1 - i;
        int l1 = j1 - j;

        while (k1 * k > 0 || l1 * l > 0) {
            if (d6 < d7) {
                d6 += d4;
                i += k;
                k1 = i1 - i;
            } else {
                d7 += d5;
                j += l;
                l1 = j1 - j;
            }

            if (!this.canWalkOn(i, (int)from.y, j, width, height, depth, from, d0, d1)) {
                return false;
            }
        }

        return true;
    }

    private boolean canWalkOn(int x, int y, int z, int width, int height, int depth, Vec3d pos, double distanceX, double distanceZ) {
        int i = x - width / 2;
        int j = z - depth / 2;
        if (!this.canWalkAbove(i, y, j, width, height, depth, pos, distanceX, distanceZ)) {
            return false;
        }

        for (int k = i; k < i + width; k++) {
            for (int l = j; l < j + depth; l++) {
                double d0 = k + 0.5 - pos.x;
                double d1 = l + 0.5 - pos.z;
                if (!(d0 * distanceX + d1 * distanceZ < 0.0)) {
                    Block block = this.world.getBlockState(new BlockPos(k, y - 1, l)).getBlock();
                    Material material = block.getMaterial();
                    if (material == Material.AIR) {
                        return false;
                    }

                    if (material == Material.WATER && !this.mob.isInWater()) {
                        return false;
                    }

                    if (material == Material.LAVA) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private boolean canWalkAbove(int x, int y, int z, int width, int height, int depth, Vec3d pos, double distanceX, double distanceZ) {
        for (BlockPos blockpos : BlockPos.iterateRegion(new BlockPos(x, y, z), new BlockPos(x + width - 1, y + height - 1, z + depth - 1))) {
            double d0 = blockpos.getX() + 0.5 - pos.x;
            double d1 = blockpos.getZ() + 0.5 - pos.z;
            if (!(d0 * distanceX + d1 * distanceZ < 0.0)) {
                Block block = this.world.getBlockState(blockpos).getBlock();
                if (!block.canWalkThrough(this.world, blockpos)) {
                    return false;
                }
            }
        }

        return true;
    }

    public void setCanSwim(boolean canSwim) {
        this.nodeEvaluator.setCanSwim(canSwim);
    }

    public boolean canSwim() {
        return this.nodeEvaluator.canSwim();
    }

    public void setCanOpenDoors(boolean canOpenDoors) {
        this.nodeEvaluator.setCanOpenDoors(canOpenDoors);
    }

    public void setCanPassThroughDoors(boolean canPassThroughDoors) {
        this.nodeEvaluator.setCanPassThroughDoors(canPassThroughDoors);
    }

    public boolean canPassThroughDoors() {
        return this.nodeEvaluator.canPassThroughDoors();
    }

    public void setCanFloat(boolean canFloat) {
        this.nodeEvaluator.setCanFloat(canFloat);
    }

    public boolean canFloat() {
        return this.nodeEvaluator.canFloat();
    }

    public void setAvoidSunLight(boolean avoidSunLight) {
        this.avoidSunLight = avoidSunLight;
    }
}
