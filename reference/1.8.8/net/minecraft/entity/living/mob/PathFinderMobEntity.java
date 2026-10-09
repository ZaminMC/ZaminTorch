package net.minecraft.entity.living.mob;

import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.WanderThroughVillageGoal;
import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.living.attribute.AttributeModifier;
import net.minecraft.entity.living.mob.passive.animal.tameable.TameableEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public abstract class PathFinderMobEntity extends MobEntity {
    public static final UUID FLEEING_SPEED_BONUS_UUID = UUID.fromString("E199AD21-BA8A-4C53-8D13-6182D5C69D3A");
    public static final AttributeModifier FLEEING_SPEED_BONUS = new AttributeModifier(FLEEING_SPEED_BONUS_UUID, "Fleeing speed bonus", 2.0, 2)
        .setSerialized(false);
    private BlockPos pos = BlockPos.ORIGIN;
    private float villageRadius = -1.0F;
    private Goal goToWalkTargetGoal = new WanderThroughVillageGoal(this, 1.0);
    /**
     * change navigation type when the mob is leeached
     */
    private boolean leechedNavigation;

    public PathFinderMobEntity(World world) {
        super(world);
    }

    public float getPathfindingFavor(BlockPos pos) {
        return 0.0F;
    }

    @Override
    public boolean canSpawn() {
        return super.canSpawn() && this.getPathfindingFavor(new BlockPos(this.x, this.getShape().minY, this.z)) >= 0.0F;
    }

    public boolean isNavigating() {
        return !this.entityNavigation.isDone();
    }

    public boolean isInVillage() {
        return this.isValidGoalTarget(new BlockPos(this));
    }

    public boolean isValidGoalTarget(BlockPos x) {
        return this.villageRadius == -1.0F || this.pos.squaredDistanceTo(x) < this.villageRadius * this.villageRadius;
    }

    public void setVillagePosAndRadius(BlockPos x, int y) {
        this.pos = x;
        this.villageRadius = y;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public float getVillageRadius() {
        return this.villageRadius;
    }

    public void resetVillageRadius() {
        this.villageRadius = -1.0F;
    }

    public boolean inVillage() {
        return this.villageRadius != -1.0F;
    }

    @Override
    protected void updateLeashStatus() {
        super.updateLeashStatus();
        if (this.isLeashed() && this.getLeashHolder() != null && this.getLeashHolder().world == this.world) {
            Entity entity = this.getLeashHolder();
            this.setVillagePosAndRadius(new BlockPos((int)entity.x, (int)entity.y, (int)entity.z), 5);
            float f = this.distanceTo(entity);
            if (this instanceof TameableEntity && ((TameableEntity)this).isSitting()) {
                if (f > 10.0F) {
                    this.detachLeash(true, true);
                }

                return;
            }

            if (!this.leechedNavigation) {
                this.goalSelector.addGoal(2, this.goToWalkTargetGoal);
                if (this.getNavigation() instanceof GroundPathNavigation) {
                    ((GroundPathNavigation)this.getNavigation()).setCanSwim(false);
                }

                this.leechedNavigation = true;
            }

            this.updateForLeashLength(f);
            if (f > 4.0F) {
                this.getNavigation().moveTo(entity, 1.0);
            }

            if (f > 6.0F) {
                double d0 = (entity.x - this.x) / f;
                double d1 = (entity.y - this.y) / f;
                double d2 = (entity.z - this.z) / f;
                this.velocityX = this.velocityX + d0 * Math.abs(d0) * 0.4;
                this.velocityY = this.velocityY + d1 * Math.abs(d1) * 0.4;
                this.velocityZ = this.velocityZ + d2 * Math.abs(d2) * 0.4;
            }

            if (f > 10.0F) {
                this.detachLeash(true, true);
            }
        } else if (!this.isLeashed() && this.leechedNavigation) {
            this.leechedNavigation = false;
            this.goalSelector.removeGoal(this.goToWalkTargetGoal);
            if (this.getNavigation() instanceof GroundPathNavigation) {
                ((GroundPathNavigation)this.getNavigation()).setCanSwim(true);
            }

            this.resetVillageRadius();
        }
    }

    protected void updateForLeashLength(float leashLength) {
    }
}
