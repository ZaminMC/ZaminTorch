package net.minecraft.entity.ai.goal;

import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.IronGolemEntity;
import net.minecraft.entity.living.mob.monster.CreeperEntity;
import net.minecraft.world.village.Village;

public class TrackIronGolemTargetGoal extends TrackTargetGoal {
    IronGolemEntity golem;
    LivingEntity target;

    public TrackIronGolemTargetGoal(IronGolemEntity golem) {
        super(golem, false, true);
        this.golem = golem;
        this.setControls(1);
    }

    @Override
    public boolean canStart() {
        Village village = this.golem.getVillage();
        if (village == null) {
            return false;
        }

        this.target = village.getNearestAttacker(this.golem);
        if (this.target instanceof CreeperEntity) {
            return false;
        }

        if (!this.canTarget(this.target, false)) {
            if (this.mob.getRandom().nextInt(20) == 0) {
                this.target = village.getNearestPlayer(this.golem);
                return this.canTarget(this.target, false);
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    @Override
    public void start() {
        this.golem.setAttackTarget(this.target);
        super.start();
    }
}
