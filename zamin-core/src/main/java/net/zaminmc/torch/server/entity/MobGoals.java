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

    /**
     * Priority 0, additive, after the float (TF-7f — the skeleton's
     * AvoidSunlightGoal, reference/1.8.8 net/minecraft/entity/ai/goal/
     * AvoidSunlightGoal 1:1; the reference's own relative order, the
     * swim before the sun-shade, SkeletonEntity lines 47-49): a pure
     * FLAG goal — while the sun stands it arms the navigation's
     * avoid-sunlight trim (start line 23) and drives nothing else; the
     * mind-tick default clear carries the stop() arm (line 27) when
     * the sun sets. The ADDITIVE seat is the reference's own
     * concurrency: the goal sets no mutex flags, so it runs beside
     * whatever exclusive goal owns the body — the panic, the escape,
     * the stroll — and every path adopted under the sun gets trimmed
     * at the sky boundary (GroundPathNavigation.trimPath lines 61-77,
     * the cut at the last shaded node before the first sky-exposed
     * one — the reference's truncate(i - 1)).
     */
    static final MobGoal AVOID_SUNLIGHT = new MobGoal() {
        @Override public String goalId() { return "avoid_sunlight"; }
        @Override public int priority() { return 0; }
        @Override public boolean exclusive() { return false; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalAvoidSunlightCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalAvoidSunlightTick();
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
     * Priority 1, exclusive (the band's second seat — a hurt body panics
     * first, a pressed one breaks): the zombie's door break (VIII9 —
     * BreakDoorGoal 1:1): a canBreakDoors-armed zombie that clips a
     * closed wooden door mid-walk claims it — the 240-tick hardness walk
     * with the 0-9 crack stages, the 1-in-20 knock sound, and the
     * HARD-alone completion (the door cell's removal + the 1012/2001
     * pair). The arming happens at the body's own clip site (the
     * reference's canStart reads collidingHorizontally + the live path —
     * the named adaptation the detour intercept carries); this goal's
     * canUse is the sticky walk, the tick the progress clock.
     */
    static final MobGoal BREAK_DOOR = new MobGoal() {
        @Override public String goalId() { return "break_door"; }
        @Override public int priority() { return 1; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalZombieBreakDoorCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalZombieBreakDoorTick();
        }
    };

    /**
     * Priority 2, exclusive: the breeding drive (the vanilla AnimalBreedGoal).
     * A loved body scans for the nearest legal partner, walks to it, and
     * after 60 ticks inside the 3-block band the breed lands through the
     * pending flag (the manager executes it — it owns spawning).
     *
     * <p>VIII10 — the VILLAGER's own arm (VillagerMatingGoal over the
     * same breed surface): the 1-in-500 cadence, the village's census
     * gate, the food/willing model (the 3-bread/12-potato/12-carrot
     * consume arms the willing at the attempt), and the mate scan over
     * the willing pair — the walk itself (the proximity window + the
     * request) rides the shared shape.</p>
     *
     * <p>TF-1 — the tameable family's own yield (the reference's own
     * priority order: FollowOwnerGoal 5 over the wolf's AnimalBreedGoal 6
     * and the ocelot's 9): a tamed pet whose owner stands beyond the
     * follow's 10-block start band never breeds — the follow could claim
     * the body, so the breed yields exactly like the reference's selector
     * steal.</p>
     */
    static final MobGoal BREED = new MobGoal() {
        @Override public String goalId() { return "breed"; }
        @Override public int priority() { return 2; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            if (mob.type() == MobType.VILLAGER) {
                return mob.goalVillagerMatingCanUse();
            }
            return mob.isInLove() && mob.findMate() != null
                    && !mob.followOwnerStartable();
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
    /**
     * Priority 3, exclusive (Slice TF-7b — the skeleton's
     * EscapeSunlightGoal, registered at band 3 BEFORE the wolf flee —
     * the reference's own order, SkeletonEntity lines 50-51): the
     * on-fire body under the open sunny sky walks to the first shaded
     * cell the favor family rejects. The registration is the skeleton's
     * own (the wither variant never joins — the nether has no sun).
     */
    static final MobGoal ESCAPE_SUNLIGHT = new MobGoal() {
        @Override public String goalId() { return "escape_sunlight"; }
        @Override public int priority() { return 3; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalEscapeSunlightCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalEscapeSunlightTick();
        }
    };

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
     * Priority 4, exclusive, FIRST in the band: the tameable family's
     * owner-follow (FollowOwnerGoal 1:1 — the reference's own goal 5 on
     * both kinds, above the wander's 7/10 and the perch's 6, below the
     * tempt's 3): the tamed pet whose owner stands 10+ blocks away walks
     * to them at the 1.0 goal speed, the path re-armed every 10 ticks,
     * and a failed path at 12+ blocks teleports through the 5x5 outer
     * ring. The wolf's own 2-block settle band, the ocelot's 5.
     */
    static final MobGoal FOLLOW_OWNER = new MobGoal() {
        @Override public String goalId() { return "follow_owner"; }
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalFollowOwnerCanUse(night);
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalFollowOwnerTick();
        }
    };

    /**
     * Priority 4, exclusive, after the follow: the tamed cat's perch
     * (OcelotSitOnBlockGoal 1:1 over GoToBlockGoal — the reference's own
     * goal 6): the 200+nextInt(200) cadence, the 8-block ring scan for a
     * chest nobody watches, a lit furnace, or the bed's foot half, the
     * walk at 0.8, and the sit at the arrival — the window standing the
     * body down when its own clock runs out.
     */
    static final MobGoal SIT_ON_BLOCK = new MobGoal() {
        @Override public String goalId() { return "sit_on_block"; }
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalSitOnBlockCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalSitOnBlockTick();
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
     * Priority 4, exclusive, before the stroll (the reference's own
     * order — the village walk 5 over the plain wander 9): the
     * villager's return home when its cached village sits behind the
     * body's radius (WanderThroughVillageGoal, VT-7k).
     */
    static final MobGoal VILLAGE_RETURN = new MobGoal() {
        @Override public String goalId() { return "village_return"; }
        @Override public int priority() { return 4; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(MobEntity mob, boolean night) {
            return mob.goalVillageReturnCanUse();
        }
        @Override public void tick(MobEntity mob, boolean night) {
            mob.goalVillageReturnTick();
        }
    };

    /**
     * The selector the mob runs: float + panic + the zombie's door break
     * + breed + the awareness band (beg, tempt, flee — the wolf and the
     * ocelot's own, all kind-gated) + the tameable band (the owner-follow
     * first, then the cat's perch) + the rabbit's garden raid + the
     * villager's village return + stroll, in priority order. The band-1
     * order matters: the panic claims a hurt body first, the door break
     * a pressed one (the reference's own 1-band; a hurt zombie runs, it
     * does not stop to break). The band-3 order matters: the beg is
     * additive so it runs first every tick, then the tempt outranks the
     * flee (the reference's own 3-over-4), so a fish-holder holds a
     * tempted cat even with players around. The band-4 order matters the
     * same way: the owner-follow outranks the perch and the plain stroll
     * (the reference's 5-over-6/7/10), and the perch outranks the stroll
     * (the reference's 6-over-10) — a tamed cat walks to its owner
     * before it perches, and perches before it wanders. The rabbit's
     * raid farm and the villager's village return keep their places
     * (kind-gated, never contending with the tameable band).
     */
    static GoalSelector standard() {
        return new GoalSelector(List.of(FLOAT, AVOID_SUNLIGHT, PANIC, BREAK_DOOR, BREED,
                BEG, TEMPT, ESCAPE_SUNLIGHT, FLEE_PLAYER, FOLLOW_OWNER, SIT_ON_BLOCK,
                RABBIT_RAID_FARM, VILLAGE_RETURN, RANDOM_STROLL));
    }
}
