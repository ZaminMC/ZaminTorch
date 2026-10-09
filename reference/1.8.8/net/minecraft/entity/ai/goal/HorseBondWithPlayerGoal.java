package net.minecraft.entity.ai.goal;

import net.minecraft.entity.ai.TargetFinder;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public class HorseBondWithPlayerGoal extends Goal {
    private HorseBaseEntity horse;
    private double speed;
    private double targetX;
    private double targetY;
    private double targetZ;

    public HorseBondWithPlayerGoal(HorseBaseEntity horse, double speed) {
        this.horse = horse;
        this.speed = speed;
        this.setControls(1);
    }

    @Override
    public boolean canStart() {
        if (!this.horse.isTame() && this.horse.rider != null) {
            Vec3d vec3d = TargetFinder.getTarget(this.horse, 5, 4);
            if (vec3d == null) {
                return false;
            }

            this.targetX = vec3d.x;
            this.targetY = vec3d.y;
            this.targetZ = vec3d.z;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void start() {
        this.horse.getNavigation().moveTo(this.targetX, this.targetY, this.targetZ, this.speed);
    }

    @Override
    public boolean shouldContinue() {
        return !this.horse.getNavigation().isDone() && this.horse.rider != null;
    }

    @Override
    public void tick() {
        if (this.horse.getRandom().nextInt(50) == 0) {
            if (this.horse.rider instanceof PlayerEntity) {
                int i = this.horse.getTemper();
                int j = this.horse.getMaxTemper();
                if (j > 0 && this.horse.getRandom().nextInt(j) < i) {
                    this.horse.bondWithPlayer((PlayerEntity)this.horse.rider);
                    this.horse.world.doEntityEvent(this.horse, (byte)7);
                    return;
                }

                this.horse.addTemper(5);
            }

            this.horse.rider.startRiding(null);
            this.horse.rider = null;
            this.horse.playAngrySound();
            this.horse.world.doEntityEvent(this.horse, (byte)6);
        }
    }
}
