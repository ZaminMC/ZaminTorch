package net.minecraft.entity.ai.goal;

import java.util.List;
import net.minecraft.entity.ai.TargetFinder;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.util.math.Vec3d;

public class FormCaravanGoal extends Goal {
    private VillagerEntity villager;
    private LivingEntity target;
    private double speed;
    private int actionCountdown;

    public FormCaravanGoal(VillagerEntity villager, double speed) {
        this.villager = villager;
        this.speed = speed;
        this.setControls(1);
    }

    @Override
    public boolean canStart() {
        if (this.villager.getBreedingAge() >= 0) {
            return false;
        }

        if (this.villager.getRandom().nextInt(400) != 0) {
            return false;
        }

        List<VillagerEntity> list = this.villager.world.getEntitiesOfType(VillagerEntity.class, this.villager.getShape().grown(6.0, 3.0, 6.0));
        double d0 = Double.MAX_VALUE;

        for (VillagerEntity villagerentity : list) {
            if (villagerentity != this.villager && !villagerentity.getInCaravan() && villagerentity.getBreedingAge() < 0) {
                double d1 = villagerentity.squaredDistanceTo(this.villager);
                if (!(d1 > d0)) {
                    d0 = d1;
                    this.target = villagerentity;
                }
            }
        }

        if (this.target == null) {
            Vec3d vec3d = TargetFinder.getTarget(this.villager, 16, 3);
            if (vec3d == null) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean shouldContinue() {
        return this.actionCountdown > 0;
    }

    @Override
    public void start() {
        if (this.target != null) {
            this.villager.setInCaravan(true);
        }

        this.actionCountdown = 1000;
    }

    @Override
    public void stop() {
        this.villager.setInCaravan(false);
        this.target = null;
    }

    @Override
    public void tick() {
        this.actionCountdown--;
        if (this.target != null) {
            if (this.villager.squaredDistanceTo(this.target) > 4.0) {
                this.villager.getNavigation().moveTo(this.target, this.speed);
            }
        } else if (this.villager.getNavigation().isDone()) {
            Vec3d vec3d = TargetFinder.getTarget(this.villager, 16, 3);
            if (vec3d == null) {
                return;
            }

            this.villager.getNavigation().moveTo(vec3d.x, vec3d.y, vec3d.z, this.speed);
        }
    }
}
