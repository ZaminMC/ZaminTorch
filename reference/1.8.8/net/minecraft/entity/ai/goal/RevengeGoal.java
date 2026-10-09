package net.minecraft.entity.ai.goal;

import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.util.math.Box;

public class RevengeGoal extends TrackTargetGoal {
    private boolean callOthersForRevenge;
    private int lastAttackedTime;
    private final Class[] ignoredTypes;

    public RevengeGoal(PathFinderMobEntity mob, boolean callOthersForRevenge, Class... ignoredTypes) {
        super(mob, false);
        this.callOthersForRevenge = callOthersForRevenge;
        this.ignoredTypes = ignoredTypes;
        this.setControls(1);
    }

    @Override
    public boolean canStart() {
        int i = this.mob.getLastAttackedTime();
        return i != this.lastAttackedTime && this.canTarget(this.mob.getAttacker(), false);
    }

    @Override
    public void start() {
        this.mob.setAttackTarget(this.mob.getAttacker());
        this.lastAttackedTime = this.mob.getLastAttackedTime();
        if (this.callOthersForRevenge) {
            double d0 = this.getFollowRange();

            for (PathFinderMobEntity pathfindermobentity : this.mob
                .world
                .getEntitiesOfType(
                    this.mob.getClass(), new Box(this.mob.x, this.mob.y, this.mob.z, this.mob.x + 1.0, this.mob.y + 1.0, this.mob.z + 1.0).grown(d0, 10.0, d0)
                )) {
                if (this.mob != pathfindermobentity
                    && pathfindermobentity.getAttackTarget() == null
                    && !pathfindermobentity.isInSameTeam(this.mob.getAttacker())) {
                    boolean flag = false;

                    for (Class oclass : this.ignoredTypes) {
                        if (pathfindermobentity.getClass() == oclass) {
                            flag = true;
                            break;
                        }
                    }

                    if (!flag) {
                        this.setMobEntityTarget(pathfindermobentity, this.mob.getAttacker());
                    }
                }
            }
        }

        super.start();
    }

    protected void setMobEntityTarget(PathFinderMobEntity mob, LivingEntity target) {
        mob.setAttackTarget(target);
    }
}
