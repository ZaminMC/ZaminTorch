package net.minecraft.entity.ai.goal;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.ai.pathing.PathNavigation;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.TameableEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class FollowOwnerGoal extends Goal {
    private TameableEntity pet;
    private LivingEntity owner;
    World world;
    private double speed;
    private PathNavigation navigation;
    private int updateTimer;
    float maxDistance;
    float minDistance;
    private boolean isInWater;

    public FollowOwnerGoal(TameableEntity pet, double speed, float minDistance, float maxDistance) {
        this.pet = pet;
        this.world = pet.world;
        this.speed = speed;
        this.navigation = pet.getNavigation();
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.setControls(3);
        if (!(pet.getNavigation() instanceof GroundPathNavigation)) {
            throw new IllegalArgumentException("Unsupported mob type for FollowOwnerGoal");
        }
    }

    @Override
    public boolean canStart() {
        LivingEntity livingentity = this.pet.getOwner();
        if (livingentity == null) {
            return false;
        }

        if (livingentity instanceof PlayerEntity && ((PlayerEntity)livingentity).isSpectator()) {
            return false;
        }

        if (this.pet.isSitting()) {
            return false;
        }

        if (this.pet.squaredDistanceTo(livingentity) < this.minDistance * this.minDistance) {
            return false;
        }

        this.owner = livingentity;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        return !this.navigation.isDone() && this.pet.squaredDistanceTo(this.owner) > this.maxDistance * this.maxDistance && !this.pet.isSitting();
    }

    @Override
    public void start() {
        this.updateTimer = 0;
        this.isInWater = ((GroundPathNavigation)this.pet.getNavigation()).canSwim();
        ((GroundPathNavigation)this.pet.getNavigation()).setCanSwim(false);
    }

    @Override
    public void stop() {
        this.owner = null;
        this.navigation.stop();
        ((GroundPathNavigation)this.pet.getNavigation()).setCanSwim(true);
    }

    private boolean canTeleportTo(BlockPos pos) {
        BlockState blockstate = this.world.getBlockState(pos);
        Block block = blockstate.getBlock();
        return block == Blocks.AIR || !block.isCube();
    }

    @Override
    public void tick() {
        this.pet.getLookControl().setLookatValues(this.owner, 10.0F, this.pet.getLookPitchSpeed());
        if (!this.pet.isSitting()) {
            if (--this.updateTimer <= 0) {
                this.updateTimer = 10;
                if (!this.navigation.moveTo(this.owner, this.speed)) {
                    if (!this.pet.isLeashed()) {
                        if (!(this.pet.squaredDistanceTo(this.owner) < 144.0)) {
                            int i = MathHelper.floor(this.owner.x) - 2;
                            int j = MathHelper.floor(this.owner.z) - 2;
                            int k = MathHelper.floor(this.owner.getShape().minY);

                            for (int l = 0; l <= 4; l++) {
                                for (int i1 = 0; i1 <= 4; i1++) {
                                    if ((l < 1 || i1 < 1 || l > 3 || i1 > 3)
                                        && World.hasSolidTop(this.world, new BlockPos(i + l, k - 1, j + i1))
                                        && this.canTeleportTo(new BlockPos(i + l, k, j + i1))
                                        && this.canTeleportTo(new BlockPos(i + l, k + 1, j + i1))) {
                                        this.pet.setPositionAndAngles(i + l + 0.5F, k, j + i1 + 0.5F, this.pet.yaw, this.pet.pitch);
                                        this.navigation.stop();
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
