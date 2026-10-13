package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;

import java.util.List;

/**
 * The built-in goal set, in the historical priority order. The behaviors are
 * the ones the body always had (panic, the idle/wander cycle with its look-at
 * glance, the fluid swim-up); the goal layer is the community architecture
 * wrapped around them so new goals join by priority, not by editing the mind.
 *
 * <p>Each built-in carries a stable id (the addressable identity of the
 * public registration surface, §27.3): "float", "panic", "breed",
 * "stroll".</p>
 */
final class MobGoals {

    private MobGoals() {
    }

    /** Priority 0, additive: a body in fluid always swims upward a little. */
    static final MobGoal FLOAT = new MobGoal() {
        @Override public String goalId() { return "float"; }
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
        @Override public String goalId() { return "panic"; }
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
        @Override public String goalId() { return "breed"; }
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
     * Priority 3, additive (LOOK control only — WolfBegGoal's own shape):
     * the wolf's begging gaze. The nearest player within 8 blocks holding a
     * bone (the untamed gate) or the meat breeding family holds the head
     * for a {@code 40 + nextInt(40)} window; the wire's index-19 flag and
     * the stroll's glance both yield to it.
     */
    static final MobGoal BEG = new MobGoal() {
        @Override public String goalId() { return "beg"; }
        @Override public int priority() { return 3; }
        @Override public boolean exclusive() { return false; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalBegCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalBegTick();
        }
    };

    /**
     * Priority 3, exclusive: the ocelot's fish-follow (TemptGoal 1:1). The
     * cat walks to the nearest fish-holder within 10 blocks at the 0.6
     * creep (the shared-flags sneak posture), holds at 2.5 blocks, and
     * only that tempted state opens the tame's fish arm. A player who
     * moves or turns inside 6 blocks scares the cat off for 100 ticks.
     */
    static final MobGoal TEMPT = new MobGoal() {
        @Override public String goalId() { return "tempt"; }
        @Override public int priority() { return 3; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalTemptCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalTemptTick();
        }
    };

    /**
     * Priority 3, exclusive: the untamed ocelot's player-flee
     * (FleeEntityGoal 1:1). Any live player within 16 blocks sends the
     * wild cat running the other way — the far walk at 0.8, the 7-block
     * close band sprinting at 1.33 (the shared-flags sprint posture). The
     * tamed cat never flees (onTamedChanged's own removal). The rabbit's
     * own flee (Slice 21d — the wolf within 16 at 1.33 both bands) rides
     * the same band; the killer bunny never flees (setVariant's own
     * removeGoal).
     */
    static final MobGoal FLEE_PLAYER = new MobGoal() {
        @Override public String goalId() { return "flee_player"; }
        @Override public int priority() { return 3; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalFleePlayerCanUse() || mob.goalFleeWolfCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            if (mob.type() == MobType.RABBIT) {
                mob.goalFleeWolfTick();
            } else {
                mob.goalFleePlayerTick();
            }
        }
    };

    /**
     * Priority 4, exclusive: the rabbit's garden raid (Slice 21d —
     * RaidFarmGoal over GoToBlockGoal 1:1). A hungry rabbit scans the
     * ±16 box on the reference's own spiral for a farmland cell with
     * age-7 carrots, walks there at 0.7, and eats the crop (the world
     * write lands through the manager's intent). Yields while any hunt
     * is live — the reference's own priority order (the attack family's
     * 4 over the farm's 5).
     */
    static final MobGoal RABBIT_RAID_FARM = new MobGoal() {
        @Override public String goalId() { return "rabbit_raid_farm"; }
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return !mob.goalHasTarget(night) && mob.goalRabbitFarmCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalRabbitFarmTick();
        }
    };

    /**
     * Priority 4, exclusive: the idle/wander cycle, with the historical
     * LookAtPlayer glance folded into the idle band.
     */
    static final MobGoal RANDOM_STROLL = new MobGoal() {
        @Override public String goalId() { return "stroll"; }
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return !mob.goalHasTarget(night); // yields while a hunt is live
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalTickIdleWander(mob.nearestVisiblePlayer());
        }
    };

    /**
     * The selector the mob runs: float + panic + breed + the awareness band
     * (beg, tempt, flee — the wolf and the ocelot's own, all kind-gated)
     * + the rabbit's garden raid + stroll, in priority order. The band-3
     * order matters: the beg is additive so it runs first every tick, then
     * the tempt outranks the flee (the reference's own 3-over-4), so a
     * fish-holder holds a tempted cat even with players around.
     */
    static GoalSelector standard() {
        return new GoalSelector(List.of(FLOAT, PANIC, BREED, BEG, TEMPT, FLEE_PLAYER,
                RABBIT_RAID_FARM, RANDOM_STROLL));
    }
}
