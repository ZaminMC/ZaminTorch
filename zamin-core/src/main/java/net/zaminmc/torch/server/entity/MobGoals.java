package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;

import java.util.List;

/**
 * The built-in goal set, in the historical priority order. The behaviors are
 * the ones the body always had (panic, the idle/wander cycle with its look-at
 * glance, the fluid swim-up); the goal layer is the community architecture
 * wrapped around them so new goals join by priority, not by editing the mind.
 */
final class MobGoals {

    private MobGoals() {
    }

    /** Priority 0, additive: a body in fluid always swims upward a little. */
    static final MobGoal FLOAT = new MobGoal() {
        @Override public int priority() { return 0; }
        @Override public boolean exclusive() { return false; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalInFluid();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalSwimUp();
        }
    };

    /** Priority 1, exclusive: the hurt panic outranks everything but floating. */
    static final MobGoal PANIC = new MobGoal() {
        @Override public int priority() { return 1; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalPanicking();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalTickPanic();
        }
    };

    /**
     * Priority 4, exclusive: the idle/wander cycle, with the historical
     * LookAtPlayer glance folded into the idle band.
     */
    static final MobGoal RANDOM_STROLL = new MobGoal() {
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return !mob.goalHasTarget(night); // yields while a hunt is live
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalTickIdleWander(mob.nearestVisiblePlayer());
        }
    };

    /** The selector the mob runs: float + panic + stroll, in priority order. */
    static GoalSelector standard() {
        return new GoalSelector(List.of(FLOAT, PANIC, RANDOM_STROLL));
    }
}
