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
     * Priority 2, exclusive: the breeding drive (the vanilla AnimalBreedGoal).
     * A loved body scans for the nearest legal partner, walks to it, and
     * after 60 ticks inside the 3-block band the breed lands through the
     * pending flag (the manager executes it — it owns spawning).
     */
    static final MobGoal BREED = new MobGoal() {
        @Override public int priority() { return 2; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.isInLove() && mob.findMate() != null;
        }
        @Override public void tick(MobEntity mob, boolean night) {
            MobEntity mate = mob.findMate();
            if (mate == null) {
                mob.resetBreedTimer();
                return;
            }
            mob.goalTickBreed(mate);
            if (mob.advanceBreedTimer() >= MobEntity.BREED_PROXIMITY_TICKS) {
                double dx = mate.position().x() - mob.position().x();
                double dy = mate.position().y() - mob.position().y();
                double dz = mate.position().z() - mob.position().z();
                if (dx * dx + dy * dy + dz * dz < MobEntity.BREED_DISTANCE_SQUARED) {
                    mob.requestBreedWith(mate);
                } else {
                    // The vanilla stop(): the 60-tick window closed without
                    // proximity, the goal restarts and the clock resets.
                    mob.resetBreedTimer();
                }
            }
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

    /** The selector the mob runs: float + panic + breed + stroll, in priority order. */
    static GoalSelector standard() {
        return new GoalSelector(List.of(FLOAT, PANIC, BREED, RANDOM_STROLL));
    }
}
