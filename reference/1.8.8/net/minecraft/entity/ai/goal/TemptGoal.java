package net.minecraft.entity.ai.goal;

import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class TemptGoal extends Goal {
    private PathFinderMobEntity mob;
    private double speed;
    private double targetX;
    private double targetY;
    private double targetZ;
    private double targetPitch;
    private double targetYaw;
    private PlayerEntity player;
    private int delay;
    private boolean active;
    private Item temptItem;
    private boolean scaredByMovement;
    private boolean inWater;

    public TemptGoal(PathFinderMobEntity entity, double speed, Item temptItem, boolean scaredByMovement) {
        this.mob = entity;
        this.speed = speed;
        this.temptItem = temptItem;
        this.scaredByMovement = scaredByMovement;
        this.setControls(3);
        if (!(entity.getNavigation() instanceof GroundPathNavigation)) {
            throw new IllegalArgumentException("Unsupported mob type for TemptGoal");
        }
    }

    @Override
    public boolean canStart() {
        if (this.delay > 0) {
            this.delay--;
            return false;
        }

        this.player = this.mob.world.getNearestPlayer(this.mob, 10.0);
        if (this.player == null) {
            return false;
        }

        ItemStack itemstack = this.player.getItemInHand();
        return itemstack != null && itemstack.getItem() == this.temptItem;
    }

    @Override
    public boolean shouldContinue() {
        if (this.scaredByMovement) {
            if (this.mob.squaredDistanceTo(this.player) < 36.0) {
                if (this.player.squaredDistanceTo(this.targetX, this.targetY, this.targetZ) > 0.010000000000000002) {
                    return false;
                }

                if (Math.abs(this.player.pitch - this.targetPitch) > 5.0 || Math.abs(this.player.yaw - this.targetYaw) > 5.0) {
                    return false;
                }
            } else {
                this.targetX = this.player.x;
                this.targetY = this.player.y;
                this.targetZ = this.player.z;
            }

            this.targetPitch = this.player.pitch;
            this.targetYaw = this.player.yaw;
        }

        return this.canStart();
    }

    @Override
    public void start() {
        this.targetX = this.player.x;
        this.targetY = this.player.y;
        this.targetZ = this.player.z;
        this.active = true;
        this.inWater = ((GroundPathNavigation)this.mob.getNavigation()).canSwim();
        ((GroundPathNavigation)this.mob.getNavigation()).setCanSwim(false);
    }

    @Override
    public void stop() {
        this.player = null;
        this.mob.getNavigation().stop();
        this.delay = 100;
        this.active = false;
        ((GroundPathNavigation)this.mob.getNavigation()).setCanSwim(this.inWater);
    }

    @Override
    public void tick() {
        this.mob.getLookControl().setLookatValues(this.player, 30.0F, this.mob.getLookPitchSpeed());
        if (this.mob.squaredDistanceTo(this.player) < 6.25) {
            this.mob.getNavigation().stop();
        } else {
            this.mob.getNavigation().moveTo(this.player, this.speed);
        }
    }

    public boolean isActive() {
        return this.active;
    }
}
