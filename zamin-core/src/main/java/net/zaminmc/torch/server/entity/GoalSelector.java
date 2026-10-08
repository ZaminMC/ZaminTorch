package net.zaminmc.torch.server.entity;

import java.util.List;

/**
 * The prioritized goal selector (the historical PathfinderGoalSelector
 * shape): every tick it walks the goals by priority, lets additive goals
 * contribute, and stops at the first exclusive goal that claims the body.
 */
public final class GoalSelector {

    private final List<MobGoal> goals;

    public GoalSelector(List<MobGoal> goals) {
        // The declared order IS the priority order; callers pass goals sorted.
        this.goals = List.copyOf(goals);
    }

    /**
     * Runs one selector pass.
     *
     * @return true when an exclusive goal consumed the tick (the combat mind
     *         is skipped); false when the body stays available.
     */
    public boolean tick(MobEntity mob, boolean night) {
        for (MobGoal goal : goals) {
            if (!goal.canUse(mob, night)) {
                continue;
            }
            goal.tick(mob, night);
            if (goal.exclusive()) {
                return true;
            }
        }
        return false;
    }
}
